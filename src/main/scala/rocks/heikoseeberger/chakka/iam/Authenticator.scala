package rocks.heikoseeberger.chakka.iam

import akka.actor.typed.{ActorRef, Behavior}
import akka.actor.typed.scaladsl.Behaviors
import org.apache.logging.log4j.scala.Logging

import scala.util.control.NonFatal

object Authenticator extends Logging {

  sealed trait Command
  final case class AddCredentials(username: String, passwordHash: String) extends Command
  final case class Authenticate(username: String,
                                password: String,
                                replyTo: ActorRef[AuthenticateReply]) extends Command

  sealed trait AuthenticateReply
  final case object InvalidCredentials extends AuthenticateReply
  final case object Authenticated extends AuthenticateReply

  def apply(credentials: Map[String, String] = Map.empty): Behavior[Command] = {
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

      case (_, AddCredentials(username, passwordHash)) =>
        logger.debug(s"Credentials for username $username added")
        Authenticator(credentials + (username -> passwordHash))
    }
  }

  def authenticate(username: String, password: String)
                  (replyTo: ActorRef[AuthenticateReply]): Authenticate =
    Authenticate(username, password, replyTo)

  private def verifyPassword(password: String)(passwordHash: String): Boolean = {
    try Passwords.verifyPassword(password, passwordHash)
    catch { case NonFatal(_) => false }
  }
}
