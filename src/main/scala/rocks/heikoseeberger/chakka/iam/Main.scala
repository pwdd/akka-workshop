package rocks.heikoseeberger.chakka.iam

import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.{CoordinatedShutdown, Scheduler}
import akka.stream.Materializer
import akka.stream.typed.ActorMaterializer
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import org.apache.logging.log4j.scala.Logging
import pureconfig.loadConfigOrThrow

object Main extends Logging {
  import akka.actor.typed.scaladsl.adapter._

  sealed trait Command
  final case class Config(accounts: Accounts.Config, api: Api.Config)

  final case object TopLevelActorTerminated extends Reason

  def main(args: Array[String]): Unit = {
    sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName // async logging

    val config = loadConfigOrThrow[Config]("chakka-iam")
    val system = ActorSystem(Main(config), "chakka-iam")

    logger.info(s"${system.name} started")
  }

  def apply(config: Config): Behavior[Command] = {

    Behaviors.setup { context =>

      implicit val mat: Materializer = ActorMaterializer()(context.system)
      implicit val scheduler: Scheduler = context.system.scheduler

      val authenticator = context.spawn(Authenticator(), "authenticator")
      context.watch(authenticator)

      val accounts = context.spawn(Accounts(config.accounts, authenticator), "accounts")
      context.watch(accounts)

      Api(config.api, accounts, authenticator)

      Behaviors.receiveSignal {
        case (_, Terminated(actor)) =>
          logger.error(s"Shutting down because $actor terminated")
          CoordinatedShutdown(context.system.toUntyped).run(TopLevelActorTerminated)
          Behaviors.same
      }
    }
  }
}
