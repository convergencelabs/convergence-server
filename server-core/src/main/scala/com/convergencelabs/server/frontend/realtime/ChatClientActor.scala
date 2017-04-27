package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.domain.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.domain.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.RemoteChatMessage
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import akka.pattern.ask
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import com.convergencelabs.server.domain.ChatChannelActor
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import com.convergencelabs.server.domain.ChatChannelMessages.UserJoinedChannel
import com.convergencelabs.server.domain.ChatChannelMessages.UserLeftChannel
import com.convergencelabs.server.domain.ChatChannelMessages.ChatChannelBroadcastMessage
import com.convergencelabs.server.domain.ChatChannelMessages.UserAddedToChannel
import com.convergencelabs.server.domain.ChatChannelMessages.UserRemovedFromChannel
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelJoined
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelLeft
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelRemoved

object ChatClientActor {
  def props(chatLookupActor: ActorRef, chatChannelActor: ActorRef, sk: SessionKey): Props =
    Props(new ChatClientActor(chatLookupActor, chatChannelActor, sk))
}

class ChatClientActor(chatLookupActor: ActorRef, chatChannelActor: ActorRef, sk: SessionKey) extends Actor with ActorLogging {

  implicit val timeout = Timeout(5 seconds)
  implicit val ec = context.dispatcher

  val mediator = DistributedPubSub(context.system).mediator
  val chatTopicName = ChatChannelActor.getChatUsernameTopicName(sk.uid)

  mediator ! Subscribe(chatTopicName, self)

  def receive: Receive = {
    case SubscribeAck(Subscribe(chatTopicName, _, _)) ⇒
      log.debug("Subscribe to chat channel for user")

    case MessageReceived(message) if message.isInstanceOf[IncomingChatNormalMessage] =>
      onMessageReceived(message.asInstanceOf[IncomingChatNormalMessage])
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingChatRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingChatRequestMessage], replyPromise)

    case message: ChatChannelBroadcastMessage =>
      handleBroadcastMessage(message)

    case x: Any =>
      unhandled(x)
  }

  private[this] def handleBroadcastMessage(message: ChatChannelBroadcastMessage): Unit = {
    message match {
      // Broadcast messages
      case RemoteChatMessage(channelId, eventNumber, timestamp, sk, message) =>
        context.parent ! RemoteChatMessageMessage(channelId, eventNumber, timestamp.toEpochMilli(), sk.serialize(), message)

      case UserJoinedChannel(channelId, eventNumber, timestamp, username) =>
        context.parent ! UserJoinedChatChannelMessage(channelId, eventNumber, timestamp.toEpochMilli(), username)

      case UserLeftChannel(channelId, eventNumber, timestamp, username) =>
        context.parent ! UserLeftChatChannelMessage(channelId, eventNumber, timestamp.toEpochMilli(), username)

      case UserAddedToChannel(channelId, eventNumber, timestamp, username, addedBy) =>
        context.parent ! UserAddedToChatChannelMessage(channelId, eventNumber, timestamp.toEpochMilli(), username, addedBy)

      case UserRemovedFromChannel(channelId, eventNumber, timestamp, username, removedBy) =>
        context.parent ! UserRemovedFromChatChannelMessage(channelId, eventNumber, timestamp.toEpochMilli(), username, removedBy)

      case ChannelJoined(channelId) =>
        context.parent ! ChatChannelJoinedMessage(channelId)
        
      case ChannelLeft(channelId) =>
        context.parent ! ChatChannelLeftMessage(channelId)
        
      case ChannelRemoved(channelId) =>
        context.parent ! ChatChannelRemovedMessage(channelId)
    }
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: IncomingChatNormalMessage): Unit = {
    log.error("Chat channel actor received a non-request message")
  }

  def onRequestReceived(message: IncomingChatRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case message: CreateChatChannelRequestMessage =>
        onCreateChannel(message)
      case message: RemoveChatChannelRequestMessage =>
        onRemoveChannel(message)
      case message: JoinChatChannelRequestMessage =>
        onJoinChannel(message, replyCallback)
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

  def onJoinChannel(message: JoinChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatChannelRequestMessage(channelId) = message;
    chatChannelActor.ask(JoinChannelRequest(channelId, sk.uid)).mapTo[Unit].onComplete {
      case Success(()) =>
        cb.reply(JoinChatChannelResponseMessage())
      case Failure(ChannelNotFoundException(_)) =>
        cb.expectedError("channel_not_found", s"A channel with id '${channelId}' does not exist.")
      case Failure(cause) =>
        cb.unexpectedError(cause.getMessage)
    }
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
