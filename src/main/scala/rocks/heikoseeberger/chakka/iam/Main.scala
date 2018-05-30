package rocks.heikoseeberger.chakka.iam

import akka.actor.CoordinatedShutdown.Reason
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorSystem, Behavior, Terminated}
import akka.actor.{CoordinatedShutdown, Scheduler, ActorSystem => UntypedSystem}
import akka.cluster.typed.{Cluster, SelfUp, Subscribe}
import akka.persistence.cassandra.query.scaladsl.CassandraReadJournal
import akka.persistence.query.PersistenceQuery
import akka.stream.Materializer
import akka.stream.typed.ActorMaterializer
import org.apache.logging.log4j.core.async.AsyncLoggerContextSelector
import org.apache.logging.log4j.scala.Logging
import pureconfig.loadConfigOrThrow

object Main extends Logging {
  import akka.actor.typed.scaladsl.adapter._

  sealed trait Command
  final case class Config(accounts: Accounts.Config, authenticator: Authenticator.Config, api: Api.Config)

  final case object TopLevelActorTerminated extends Reason

  def main(args: Array[String]): Unit = {
    sys.props += "log4j2.contextSelector" -> classOf[AsyncLoggerContextSelector].getName // async logging

    val config = loadConfigOrThrow[Config]("chakka-iam")
    val system = ActorSystem(Main(config), "chakka-iam")

    logger.info(s"${system.name} started")
  }

  def apply(config: Config): Behavior[SelfUp] = {
    Behaviors.setup { context =>
      Cluster(context.system).subscriptions ! Subscribe(context.self, classOf[SelfUp])

      Behaviors
        .receiveMessage[SelfUp] { _ =>
        logger.info(s"${context.system.name} joined cluster and is up")
        onSelfUp(config, context)
        Behaviors.empty
      }
        .receiveSignal {
          case (_, Terminated(actor)) =>
            logger.error(s"Shutting down because $actor terminated")
            CoordinatedShutdown(context.system.toUntyped).run(TopLevelActorTerminated)
            Behaviors.same
        }
    }
  }

  private def onSelfUp(config: Config, context: ActorContext[SelfUp]): Unit = {
    implicit val untypedSystem: UntypedSystem = context.system.toUntyped
    implicit val mat: Materializer = ActorMaterializer()(context.system)
    implicit val scheduler: Scheduler = context.system.scheduler
    implicit val readJournal: CassandraReadJournal = PersistenceQuery(untypedSystem)
      .readJournalFor[CassandraReadJournal](CassandraReadJournal.Identifier)

    val authenticator = context.spawn(Authenticator(config.authenticator), "authenticator")
    context.watch(authenticator)

    val accounts = context.spawn(Accounts(config.accounts), "accounts")
    context.watch(accounts)

    Api(config.api, accounts, authenticator)
  }
}
