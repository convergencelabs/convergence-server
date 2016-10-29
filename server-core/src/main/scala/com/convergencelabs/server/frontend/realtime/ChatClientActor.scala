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
import com.convergencelabs.server.domain.ChatServiceActor.LeaveRoom
import com.convergencelabs.server.domain.ChatServiceActor.SendMessage
import com.convergencelabs.server.domain.ChatServiceActor.JoinRoomRequest
import com.convergencelabs.server.domain.ChatServiceActor.JoinRoomResponse

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
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingChatRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingChatRequestMessage], replyPromise)

    case UserJoined(roomId, sk, timestamp) =>
      context.parent ! UserJoinedRoomMessage(roomId, sk.uid, sk.serialize(), timestamp)
    case UserLeft(roomId, sk, timestamp) =>
      context.parent ! UserLeftRoomMessage(roomId, sk.uid, sk.serialize(), timestamp)
    case UserMessage(roomId, sk, message, timestamp) =>
      context.parent ! UserChatMessage(roomId, sk.uid, sk.serialize(), message, timestamp)

    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingChatNormalMessage): Unit = {
    message match {
      case LeftChatRoomMessage(roomId) =>
        onLeft(roomId)
      case PublishedChatMessage(roomId, message) =>
        onChatMessage(roomId, message)
    }
  }

  def onRequestReceived(message: IncomingChatRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case JoinChatRoomRequestMessage(roomId) => onJoined(roomId, replyCallback)
    }
  }

  def onJoined(roomId: String, cb: ReplyCallback): Unit = {
    val future = this.chatServiceActor ? JoinRoomRequest(self, roomId, sk)

    future.mapResponse[JoinRoomResponse] onComplete {
      case Success(JoinRoomResponse(members, count, lastMessage)) =>
        cb.reply(JoinChatRoomResponseMessage(members.map { _.serialize() }, count, lastMessage))
      case Failure(cause) =>
        cb.unexpectedError("could not join activity")
    }
  }

  def onLeft(roomId: String): Unit = {
    this.chatServiceActor ! LeaveRoom(self, roomId)
  }

  def onChatMessage(roomId: String, message: String): Unit = {
    this.chatServiceActor ! SendMessage(self, roomId, message)
  }
}
