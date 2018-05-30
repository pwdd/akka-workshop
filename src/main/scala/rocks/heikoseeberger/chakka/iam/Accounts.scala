package rocks.heikoseeberger.chakka.iam

import akka.actor.typed.{ActorRef, Behavior}
import akka.persistence.typed.scaladsl.PersistentBehaviors.CommandHandler
import akka.persistence.typed.scaladsl.{Effect, PersistentBehaviors}
import org.apache.logging.log4j.scala.Logging

import scala.util.matching.Regex

object Accounts extends Logging {
  sealed trait Command
  sealed trait Event
  final case class CreateAccount(username: String,
                                 password: String,
                                 replyTo: ActorRef[CreateAccountReply]) extends Command

  sealed trait CreateAccountReply
  final case object UsernameTaken extends CreateAccountReply
  final case object UsernameInvalid extends CreateAccountReply
  final case object PasswordInvalid extends CreateAccountReply
  final case class AccountCreated(username: String) extends CreateAccountReply with Event

  final case class Config(usernameRegex: Regex, passwordRegex: Regex)

  final case class State(usernames: Set[String] = Set.empty)

  final val PersistenceId = "accounts"

  def apply(config: Config,
            authenticator: ActorRef[Authenticator.Command]): Behavior[Command] =
    PersistentBehaviors.receive(PersistenceId,
      State(),
      commandHandler(config, authenticator),
      eventHandler)

  def commandHandler(config: Config,
                     authenticator: ActorRef[Authenticator.Command]): CommandHandler[Command, Event, State] = {
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
        val accountCreated = AccountCreated(username)
        Effect
          .persist(accountCreated)
          .andThen {
            logger.info(s"Account for $username created")
            authenticator ! Authenticator.AddCredentials(username, Passwords.createHash(password))
            replyTo ! AccountCreated(username)
          }
      }
  }

  def eventHandler: (State, Event) => State = {
    case (State(usernames), AccountCreated(username)) => State(usernames + username)
  }

  def createAccount(username: String, password: String)(replyTo: ActorRef[CreateAccountReply]): CreateAccount =
    CreateAccount(username, password, replyTo)
}
