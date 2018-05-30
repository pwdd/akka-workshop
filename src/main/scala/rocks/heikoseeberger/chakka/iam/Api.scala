package rocks.heikoseeberger.chakka.iam

import akka.Done
import akka.actor.CoordinatedShutdown.{PhaseServiceRequestsDone, PhaseServiceUnbind, Reason}
import akka.actor.{ActorSystem, CoordinatedShutdown}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.{Directives, Route}
import akka.http.scaladsl.model.StatusCodes.OK
import akka.pattern.after
import akka.stream.Materializer
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Api extends Logging {

  final case class Config(address: String, port: Int, requestDoneAfter: FiniteDuration)

  final case object BindFailure extends Reason

  def apply(config: Config)(implicit untypedSystem: ActorSystem, mat: Materializer) = {
    import config._
    import untypedSystem.dispatcher

    val shutdown = CoordinatedShutdown(untypedSystem)

    Http()
      .bindAndHandle(route, address, port)
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

  def route: Route = {
    import Directives._

    pathPrefix("iam") {
      pathEnd {
        get {
          complete {
            OK
          }
        }
      }
    }
  }
}
