package rocks.heikoseeberger.chakka.iam

import akka.Done
import akka.actor.CoordinatedShutdown.{ PhaseServiceRequestsDone, PhaseServiceUnbind, Reason }
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.{ ActorSystem, CoordinatedShutdown, Scheduler }
import akka.http.scaladsl.Http
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.MediaTypes.`text/event-stream`
import akka.http.scaladsl.model.{ HttpEntity, HttpResponse }
import akka.http.scaladsl.model.StatusCodes._
import akka.http.scaladsl.model.headers.`Last-Event-ID`
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.{ Directives, Route }
import akka.pattern.after
import akka.persistence.query.EventEnvelope
import akka.stream.Materializer
import akka.util.Timeout
import com.softwaremill.session.{ SessionConfig, SessionDirectives, SessionManager }
import com.softwaremill.session.SessionOptions.{ oneOff, usingCookies }
import de.heikoseeberger.akkahttpcirce.ErrorAccumulatingCirceSupport
import org.apache.logging.log4j.scala.Logging
import java.nio.charset.StandardCharsets.UTF_8

import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

object Api extends Logging {

  final case class Config(address: String,
                          port: Int,
                          requestDoneAfter: FiniteDuration,
                          askTimeout: FiniteDuration,
                          eventsMaxIdle: FiniteDuration)

  final case object BindFailure extends Reason

  final case class SignUp(username: String, password: String, nickname: String)

  final case class SignIn(username: String, password: String)

  def apply(config: Config,
            accounts: ActorRef[Accounts.Command],
            authenticator: ActorRef[Authenticator.Command])(
      implicit untypedSystem: ActorSystem,
      mat: Materializer,
      readJournal: EventsByPersistenceIdQuery
  ): Unit = {
    import config._
    import untypedSystem.dispatcher

    implicit val scheduler: Scheduler = untypedSystem.scheduler
    val shutdown                      = CoordinatedShutdown(untypedSystem)

    Http()
      .bindAndHandle(route(accounts, authenticator, askTimeout, eventsMaxIdle), address, port)
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

  def route(
      accounts: ActorRef[Accounts.Command],
      authenticator: ActorRef[Authenticator.Command],
      askTimeout: FiniteDuration,
      eventsMaxIdle: FiniteDuration
  )(implicit duration: Scheduler, readJournal: EventsByPersistenceIdQuery): Route = {
    import Directives._
    import ErrorAccumulatingCirceSupport._
    import EventStreamMarshalling._
    import SessionDirectives._
    import io.circe.generic.auto._
    import io.circe.syntax._

    implicit val sessions: SessionManager[String] =
      new SessionManager[String](SessionConfig.fromConfig())
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
              case SignUp(username, password, nickname) =>
                onSuccess(accounts ? createAccount(username, password, nickname)) {
                  case UsernameInvalid   => complete(BadRequest)
                  case UsernameTaken     => complete(Conflict)
                  case PasswordInvalid   => complete(BadRequest)
                  case _: AccountCreated => complete(Created)
                }
            }
          }
        } ~
        path("events") {
          get {
            optionalHeaderValueByName(`Last-Event-ID`.name) { lastEventId =>
              try {
                val fromSeqNo = lastEventId.getOrElse("-1").trim.toLong + 1
                complete {
                  readJournal
                    .eventsByPersistenceId(PersistenceId, fromSeqNo, Long.MaxValue)
                    .collect {
                      case EventEnvelope(_, _, seqNo, ac: Accounts.AccountCreated) =>
                        ServerSentEvent(ac.copy(passwordHash = "").asJson.noSpaces,
                                        "account-created",
                                        seqNo.toString)
                    }
                    .keepAlive(eventsMaxIdle, () => ServerSentEvent.heartbeat)
                }
              } catch {
                case _: NumberFormatException =>
                  complete(
                    HttpResponse(
                      BadRequest,
                      entity = HttpEntity(`text/event-stream`,
                                          "Last-Event-ID must be numeric!".getBytes(UTF_8))
                    )
                  )
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
                  case Authenticated =>
                    setSession(oneOff, usingCookies, username) {
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
