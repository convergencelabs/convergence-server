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

    case UserMessage(channelId, sk, message, timestamp) =>
      val eventNo = 0L // FIXME
      context.parent ! RemoteChatMessage(channelId, eventNo, timestamp, sk.serialize(), message)
    // FIXME handle outgoing messages

    case x: Any => unhandled(x)
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingChatNormalMessage): Unit = {
    // FISME
    ???
  }

  def onRequestReceived(message: IncomingChatRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case message: CreateChatChannelRequestMessage =>
        onCreateChannel(message)
      case message: RemoveChatChannelRequestMessage =>
        onRemoveChannel(message)
      case message: JoinChatChannelRequestMessage =>
        onJoinChannel(message)
      case message: LeaveChatChannelRequestMessage =>
        onLeaveChannel(message)
      case message: AddUserToChatChannelRequestMessage =>
        onAddUserToChannel(message)
      case message: RemoveUserFromChatChannelRequestMessage =>
        onRemoveUserFromChannel(message)
      case message: SetChatChannelNameRequestMessage =>
        onSetChatChannelName(message)
      case message: SetChatChannelTopicRequestMessage =>
        onSetChatChannelTopic(message)
      case message: MarkChatChannelEventsSeenRequestMessage =>
        onMarkEventsSeen(message)
      case message: GetChatChannelsRequestMessage =>
        onGetChannels(message)
      case message: GetJoinedChatChannelsRequestMessage =>
        onGetJoinedChannels()
      case message: GetDirectChannelsRequestMessage =>
        onGetDirect(message)
      case message: ChatChannelHistoryRequestMessage =>
        onGetHistory(message)
      case message: PublishChatRequestMessage =>
        onPublishMessage(message)
    }
  }

  def onCreateChannel(message: CreateChatChannelRequestMessage): Unit = {
    val CreateChatChannelRequestMessage(channelId, channelType, name, topic, privateChannel, members) = message;
    ???
  }

  def onRemoveChannel(message: RemoveChatChannelRequestMessage): Unit = {
    val RemoveChatChannelRequestMessage(channelId) = message;
    ???
  }

  def onJoinChannel(message: JoinChatChannelRequestMessage): Unit = {
    val JoinChatChannelRequestMessage(channelId) = message;
    ???
  }

  def onLeaveChannel(message: LeaveChatChannelRequestMessage): Unit = {
    val LeaveChatChannelRequestMessage(channelId) = message;
    ???
  }

  def onAddUserToChannel(message: AddUserToChatChannelRequestMessage): Unit = {
    val AddUserToChatChannelRequestMessage(channelId, username) = message;
    ???
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChatChannelRequestMessage): Unit = {
    val RemoveUserFromChatChannelRequestMessage(channelId, username) = message;
    ???
  }

  def onSetChatChannelName(message: SetChatChannelNameRequestMessage): Unit = {
    val SetChatChannelNameRequestMessage(channelId, name) = message;
    ???
  }

  def onSetChatChannelTopic(message: SetChatChannelTopicRequestMessage): Unit = {
    val SetChatChannelTopicRequestMessage(channelId, topic) = message;
    ???
  }

  def onMarkEventsSeen(message: MarkChatChannelEventsSeenRequestMessage): Unit = {
    val MarkChatChannelEventsSeenRequestMessage(channelId, eventNumber) = message;
    ???
  }

  def onGetChannels(message: GetChatChannelsRequestMessage): Unit = {
    val GetChatChannelsRequestMessage(ids) = message;
    ???
  }

  def onGetDirect(message: GetDirectChannelsRequestMessage): Unit = {
    val GetDirectChannelsRequestMessage(usernameLists) = message;
    ???
  }

  def onGetJoinedChannels(): Unit = {
    ???
  }

  def onGetHistory(message: ChatChannelHistoryRequestMessage): Unit = {
    val ChatChannelHistoryRequestMessage(channleId, limit, offset, forward, events) = message;
    ???
  }

  def onPublishMessage(message: PublishChatRequestMessage): Unit = {
    val PublishChatRequestMessage(channeId, msg) = message;
    ???
  }

}
