package rocks.heikoseeberger.chakka.iam

import akka.actor.typed.{ ActorRef, Behavior }
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.scaladsl.{ Effect, PersistentBehaviors }
import org.apache.logging.log4j.scala.Logging

import scala.util.matching.Regex

object Accounts extends Logging {
  sealed trait Command
  sealed trait Event
  final case class CreateAccount(username: String,
                                 password: String,
                                 replyTo: ActorRef[CreateAccountReply])
      extends Command

  sealed trait CreateAccountReply
  final case object UsernameTaken   extends CreateAccountReply
  final case object UsernameInvalid extends CreateAccountReply
  final case object PasswordInvalid extends CreateAccountReply
  final case class AccountCreated(username: String, passwordHash: String)
      extends CreateAccountReply
      with Event

  final case object Stop extends Command

  final case class Config(usernameRegex: Regex, passwordRegex: Regex)

  final case class State(usernames: Set[String] = Set.empty)

  final val PersistenceId = "accounts"

  def apply(config: Config): Behavior[Command] =
    PersistentBehaviors.receive(PersistenceId, State(), commandHandler(config), eventHandler)

  def commandHandler(config: Config): CommandHandler[Command, Event, State] = {
    case (_, State(usernames), CreateAccount(username, password, replyTo)) =>
      if (usernames contains username) {
        replyTo ! UsernameTaken
        Effect.none
      } else if (!config.usernameRegex.pattern.matcher(username).matches) {
        replyTo ! UsernameInvalid
        Effect.none
      } else if (!config.passwordRegex.pattern.matcher(password).matches) {
        replyTo ! PasswordInvalid
        Effect.none
      } else {
        val accountCreated = AccountCreated(username, Passwords.createHash(password))
        Effect
          .persist(accountCreated)
          .andThen {
            logger.info(s"Account for $username created")
            replyTo ! accountCreated.copy(passwordHash = "")
          }
      }

    case (_, _, Stop) => Effect.stop
  }

  def eventHandler: (State, Event) => State = {
    case (State(usernames), AccountCreated(username, _)) => State(usernames + username)
  }

  def createAccount(username: String,
                    password: String)(replyTo: ActorRef[CreateAccountReply]): CreateAccount =
    CreateAccount(username, password, replyTo)
}
