package rocks.heikoseeberger.chakka.iam

import java.nio.file.Path

import akka.actor.{CoordinatedShutdown, Scheduler}
import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, ActorSystem, Behavior, Terminated}
import akka.stream.Materializer
import akka.stream.scaladsl.FileIO
import akka.stream.typed.ActorMaterializer
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import org.apache.logging.log4j.scala.Logging
import pureconfig.loadConfigOrThrow

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration
import scala.util.{Failure, Success}

object Main extends Logging {
  import akka.actor.typed.scaladsl.adapter._

  sealed trait Command
  final case class Config(initialAccounts: Path, askTimeout: FiniteDuration, accounts: Accounts.Config)

  final case object TopLevelActorTerminated extends Reason

  def main(args: Array[String]): Unit = {
    sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName // async logging

    val config = loadConfigOrThrow[Config]("chakka-iam")
    val system = ActorSystem(Main(config), "chakka-iam")

    logger.info(s"${system.name} started")
  }

  def apply(config: Config): Behavior[Command] = {
    import config._

    Behaviors.setup { context =>

      implicit val mat: Materializer = ActorMaterializer()(context.system)
      implicit val scheduler: Scheduler = context.system.scheduler

      val accounts = context.spawn(Accounts(config.accounts), "accounts")
      context.watch(accounts)

      loadAccounts(initialAccounts, askTimeout, accounts)

      Behaviors.receiveSignal {
        case (_, Terminated(actor)) =>
          logger.error(s"Shutting down because $actor terminated")
          CoordinatedShutdown(context.system.toUntyped).run(TopLevelActorTerminated)
          Behaviors.stopped
      }
    }
  }

  private def loadAccounts(initialAccounts: Path,
                           askTimeout: FiniteDuration,
                           accounts: ActorRef[Accounts.Command]
                          )(implicit mat: Materializer, scheduler: Scheduler, ec: ExecutionContext): Unit =
    FileIO
      .fromPath(initialAccounts)
      .via(Accounts.load(accounts, askTimeout))
      .runFold((0, 0, 0)) {
        case ((i, t, c), Accounts.UsernameInvalid) => (i + 1, t, c)
        case ((i, t, c), Accounts.UsernameTaken) => (i, t + 1, c)
        case ((i, t, c), Accounts.AccountCreated(_)) => (i, t, c + 1)
      }
      .onComplete {
        case Failure(cause) => logger.error("Cannot load accounts", cause)
        case Success((i, t, c)) => logger.info(s"Accounts loaded: invalid=$i, taken:$t, created=$c")
      }
}
