package rocks.heikoseeberger.chakka.iam

import akka.NotUsed
import akka.actor.Scheduler
import akka.actor.typed.scaladsl.AskPattern.Askable
import akka.actor.typed.scaladsl.Behaviors
import akka.actor.typed.{ActorRef, Behavior}
import akka.stream.Materializer
import akka.stream.scaladsl.{Flow, Framing}
import akka.util.{ByteString, Timeout}
import org.apache.logging.log4j.scala.Logging

import scala.concurrent.duration.FiniteDuration
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

  def load[T](accounts: ActorRef[CreateAccount], askTimeout: FiniteDuration)
             (implicit mat: Materializer,
              scheduler: Scheduler
             ): Flow[ByteString, CreateAccountReply, NotUsed] = {
    implicit val timeout: Timeout = askTimeout
    Flow[ByteString]
      .via(Framing.delimiter(ByteString("\n"), 256, allowTruncation = true))
      .map(_.utf8String)
      .mapAsync(parallelism = 1)(accounts ? createAccount(_))
  }

  def createAccount(username: String)(replyTo: ActorRef[CreateAccountReply]): CreateAccount =
    CreateAccount(username, replyTo)
}
