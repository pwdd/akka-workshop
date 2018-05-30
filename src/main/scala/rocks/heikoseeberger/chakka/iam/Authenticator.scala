package rocks.heikoseeberger.chakka.iam

import akka.Done
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.query.EventEnvelope
import akka.persistence.query.scaladsl.EventsByPersistenceIdQuery
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.util.Timeout
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.FiniteDuration
import scala.util.control.NonFatal

object Authenticator extends Logging {

  sealed trait Command
  private final case class AddCredentials(seqNo: Long,
                                          username: String,
                                          passwordHash: String,
                                          replyTo: ActorRef[Done])
      extends Command

  final case class Authenticate(username: String,
                                password: String,
                                replyTo: ActorRef[AuthenticateReply])
      extends Command

  private final case object HandleProjectionComplete extends Command

  sealed trait AuthenticateReply
  final case object InvalidCredentials extends AuthenticateReply
  final case object Authenticated      extends AuthenticateReply

  final case class Config(askTimeout: FiniteDuration)

  def apply(config: Config)(implicit mat: Materializer,
                            readJournal: EventsByPersistenceIdQuery): Behavior[Command] =
    Behaviors.setup { context =>
      implicit val scheduler: Scheduler = context.system.scheduler
      runProjection(0, context.self, config.askTimeout)
      Authenticator(0, Map.empty, config.askTimeout)
    }

  def apply(lastSeqNo: Long, credentials: Map[String, String], askTimeout: FiniteDuration)(
      implicit mat: Materializer,
      scheduler: Scheduler,
      readJournal: EventsByPersistenceIdQuery
  ): Behavior[Command] =
    Behaviors.receive {
      case (_, Authenticate(username, password, replyTo)) =>
        val valid = credentials
          .get(username)
          .map(verifyPassword(password))
          .fold(false)(identity)
        if (!valid) {
          logger.warn(s"Invalid credentials for username $username!")
          replyTo ! InvalidCredentials
        } else replyTo ! Authenticated

        Behaviors.same

      case (_, AddCredentials(seqNo, username, passwordHash, replyTo)) =>
        logger.debug(s"Credentials for username $username added")
        replyTo ! Done
        Authenticator(seqNo, credentials + (username -> passwordHash), askTimeout)

      case (context, HandleProjectionComplete) =>
        runProjection(lastSeqNo, context.self, askTimeout)
        Behaviors.same
    }

  def authenticate(username: String,
                   password: String)(replyTo: ActorRef[AuthenticateReply]): Authenticate =
    Authenticate(username, password, replyTo)

  private def verifyPassword(password: String)(passwordHash: String): Boolean =
    try Passwords.verifyPassword(password, passwordHash)
    catch { case NonFatal(_) => false }

  private def runProjection(lastSeqNo: Long,
                            authenticator: ActorRef[Authenticator.Command],
                            askTimeout: FiniteDuration)(
      implicit mat: Materializer,
      scheduler: Scheduler,
      readJournal: EventsByPersistenceIdQuery
  ): Unit = {
    implicit val timeout: Timeout = askTimeout

    readJournal
      .eventsByPersistenceId(Accounts.PersistenceId, lastSeqNo + 1, Long.MaxValue)
      .collect {
        case EventEnvelope(_, _, seqNo, Accounts.AccountCreated(username, passwordHash, _)) =>
          Authenticator.AddCredentials(seqNo, username, passwordHash, _: ActorRef[Done])
      }
      .mapAsync(1)(authenticator ? _)
      .runWith(Sink.onComplete { cause =>
        logger.warn(s"Projection of Accounts events completed unexpectedly: $cause")
        authenticator ! HandleProjectionComplete
      })
  }
}
