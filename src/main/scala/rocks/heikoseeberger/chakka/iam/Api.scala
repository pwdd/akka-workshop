package rocks.heikoseeberger.chakka.iam

import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.{ActorSystem, CoordinatedShutdown, Scheduler}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.model.StatusCodes.{
  BadRequest,
  Conflict,
  Created,
  OK,
  NoContent,
  Unauthorized
}
import akka.pattern.after
import akka.stream.Materializer
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging
import com.softwaremill.session.SessionOptions.{ oneOff, usingCookies }
import com.softwaremill.session.{ SessionConfig, SessionDirectives, SessionManager }
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Api extends Logging {

  final case class Config(address: String, port: Int, requestDoneAfter: FiniteDuration, askTimeout: FiniteDuration)

  final case object BindFailure extends Reason

  final case class SignUp(username: String, password: String)

  final case class SignIn(username: String, password: String)

  def apply(config: Config, accounts: ActorRef[Accounts.Command], authenticator: ActorRef[Authenticator.Command])
           (implicit untypedSystem: ActorSystem, mat: Materializer): Unit = {
    import config._
    import untypedSystem.dispatcher

    implicit val scheduler: Scheduler = untypedSystem.scheduler
    val shutdown = CoordinatedShutdown(untypedSystem)

    Http()
      .bindAndHandle(route(accounts, authenticator, askTimeout), address, port)
      .onComplete {
        case Failure(cause) =>
          logger.error(s"Shutting down because cannot bind to $address:$port", cause)
          shutdown.run(BindFailure)
        case Success(binding) =>
          logger.info(s"Listening to HTTP requests on ${binding.localAddress}")
          shutdown.addTask(PhaseServiceUnbind, "api.unbind") { () =>
            binding.unbind
          }
          shutdown.addTask(PhaseServiceRequestsDone, "api.request-done") { () =>
            after(requestDoneAfter, untypedSystem.scheduler)(Future.successful(Done))
          }
      }
  }

  def route(accounts: ActorRef[Accounts.Command],
            authenticator: ActorRef[Authenticator.Command],
            askTimeout: FiniteDuration)
           (implicit duration: Scheduler): Route = {
    import Directives._
    import ErrorAccumulatingCirceSupport._
    import SessionDirectives._
    import io.circe.generic.auto._

    implicit val timeout: Timeout = askTimeout

    pathPrefix("iam") {
      pathEnd {
        get {
          complete {
            OK
          }
        }
      } ~
      pathPrefix("accounts") {
        import Accounts._
        pathEnd {
          post {
            entity(as[SignUp]) {
              case SignUp(username, password) =>
                onSuccess(accounts ? createAccount(username, password)) {
                  case UsernameInvalid => complete(BadRequest)
                  case UsernameTaken => complete(Conflict)
                  case PasswordInvalid => complete(BadRequest)
                  case _: AccountCreated => complete(Created)
                }
            }
          }
        }
      } ~
      pathPrefix("sessions") {
        import Authenticator._
        pathEnd {
          post {
            entity(as[SignIn]) {
              case SignIn(username, password) =>
                onSuccess(authenticator ? authenticate(username, password)) {
                  case InvalidCredentials => complete(Unauthorized)
                  case Authenticated => setSession(oneOff, usingCookies, username) {
                    complete(NoContent)
                  }
                }
            }
          }
        }
      }
    }
  }
}
