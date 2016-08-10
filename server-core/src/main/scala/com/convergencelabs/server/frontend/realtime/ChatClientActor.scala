package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.ChatServiceActor.UserJoined
import com.convergencelabs.server.domain.ChatServiceActor.UserLeft
import com.convergencelabs.server.domain.ChatServiceActor.UserMessage
import com.convergencelabs.server.domain.ChatServiceActor.JoinRoom
import com.convergencelabs.server.domain.ChatServiceActor.LeaveRoom
import com.convergencelabs.server.domain.ChatServiceActor.SendMessage

object ChatClientActor {
  def props(chatServiceActor: ActorRef, sk: SessionKey): Props =
    Props(new ChatClientActor(chatServiceActor, sk))
}

class ChatClientActor(chatServiceActor: ActorRef, sk: SessionKey) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  def receive: Receive = {
    case MessageReceived(message) if message.isInstanceOf[IncomingChatNormalMessage] =>
      onMessageReceived(message.asInstanceOf[IncomingChatNormalMessage])

    case UserJoined(roomId, SessionKey(username, sessionId), timestamp) =>
      context.parent ! UserJoinedRoomMessage(roomId, username, sessionId, timestamp)
    case UserLeft(roomId, SessionKey(username, sessionId), timestamp) =>
      context.parent ! UserLeftRoomMessage(roomId, username, sessionId, timestamp)
    case UserMessage(roomId, SessionKey(username, sessionId), message, timestamp) =>
      context.parent ! UserChatMessage(roomId, username, sessionId, message, timestamp)

    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingChatNormalMessage): Unit = {
    message match {
      case JoinedChatRoomMessage(roomId)           => onJoined(roomId)
      case LeftChatRoomMessage(roomId)             => onLeft(roomId)
      case PublishedChatMessage(roomId, message) => onChatMessage(roomId, message)
    }
  }

  def onJoined(roomId: String): Unit = {
    this.chatServiceActor ! JoinRoom(self, roomId, sk)
  }

  def onLeft(roomId: String): Unit = {
    this.chatServiceActor ! LeaveRoom(self, roomId)
  }

  def onChatMessage(roomId: String, message: String): Unit = {
    this.chatServiceActor ! SendMessage(self, roomId, message)
  }
}
