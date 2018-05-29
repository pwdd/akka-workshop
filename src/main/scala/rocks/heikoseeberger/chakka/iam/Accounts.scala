package rocks.heikoseeberger.chakka.iam

import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import org.apache.logging.log4j.scala.Logging

import scala.util.matching.Regex

object Accounts extends Logging {
  sealed trait Command
  final case class CreateAccount(username: String, replyTo: ActorRef[CreateAccountReply]) extends Command

  sealed trait CreateAccountReply
  final case object UsernameTaken extends CreateAccountReply
  final case class AccountCreated(username: String) extends CreateAccountReply
  final case object UsernameInvalid extends CreateAccountReply

  final case class Config(usernameRegex: Regex)

  def apply(config: Config, usernames: Set[String] = Set.empty): Behavior[Command] = {
    import config._

    Behaviors.receiveMessage {
      case CreateAccount(username, replyTo) =>
        if (usernames contains username) {
          replyTo ! UsernameTaken
          Behaviors.same
        } else if (!usernameRegex.pattern.matcher(username).matches) {
          replyTo ! UsernameInvalid
          Behaviors.same
        } else {
          val accountCreated = AccountCreated(username)
          logger.info(s"Account for $username created")
          replyTo ! accountCreated
          Accounts(config, usernames + username)
        }
    }
  }
}
