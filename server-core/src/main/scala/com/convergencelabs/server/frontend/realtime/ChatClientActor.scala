package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.domain.ChatChannelActor
import com.convergencelabs.server.domain.ChatChannelLookupActor.GetChannelsRequest
import com.convergencelabs.server.domain.ChatChannelLookupActor.GetChannelsResponse
import com.convergencelabs.server.domain.ChatChannelLookupActor.GetDirectChannelsRequest
import com.convergencelabs.server.domain.ChatChannelLookupActor.GetDirectChannelsResponse
import com.convergencelabs.server.domain.ChatChannelLookupActor.GetJoinedChannelsRequest
import com.convergencelabs.server.domain.ChatChannelLookupActor.GetJoinedChannelsResponse
import com.convergencelabs.server.domain.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelAlreadyExistsException
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelAlreadyJoinedException
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelNameChanged
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelNotJoinedException
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelRemoved
import com.convergencelabs.server.domain.ChatChannelMessages.ChannelTopicChanged
import com.convergencelabs.server.domain.ChatChannelMessages.ChatChannelBroadcastMessage
import com.convergencelabs.server.domain.ChatChannelMessages.ChatChannelException
import com.convergencelabs.server.domain.ChatChannelMessages.CreateChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.CreateChannelResponse
import com.convergencelabs.server.domain.ChatChannelMessages.GetChannelHistoryRequest
import com.convergencelabs.server.domain.ChatChannelMessages.GetChannelHistoryResponse
import com.convergencelabs.server.domain.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.MarkChannelEventsSeenRequest
import com.convergencelabs.server.domain.ChatChannelMessages.PublishChatMessageRequest
import com.convergencelabs.server.domain.ChatChannelMessages.RemoteChatMessage
import com.convergencelabs.server.domain.ChatChannelMessages.RemoveChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.RemoveUserFromChannelRequest
import com.convergencelabs.server.domain.ChatChannelMessages.SetChannelNameRequest
import com.convergencelabs.server.domain.ChatChannelMessages.SetChannelTopicRequest
import com.convergencelabs.server.domain.ChatChannelMessages.UserAddedToChannel
import com.convergencelabs.server.domain.ChatChannelMessages.UserJoinedChannel
import com.convergencelabs.server.domain.ChatChannelMessages.UserLeftChannel
import com.convergencelabs.server.domain.ChatChannelMessages.UserRemovedFromChannel
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.pattern.ask
import akka.util.Timeout
import com.convergencelabs.server.datastore.domain.ChatChannelInfo

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

      case ChannelRemoved(channelId) =>
        context.parent ! ChatChannelRemovedMessage(channelId)

      case ChannelNameChanged(channelId, eventNumber, timestamp, name, setBy) =>
        context.parent ! ChatChannelNameSetMessage(channelId, eventNumber, timestamp.toEpochMilli, setBy, name)

      case ChannelTopicChanged(channelId, eventNumber, timestamp, name, setBy) =>
        context.parent ! ChatChannelTopicSetMessage(channelId, eventNumber, timestamp.toEpochMilli, setBy, name)
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
        onCreateChannel(message, replyCallback)
      case message: RemoveChatChannelRequestMessage =>
        onRemoveChannel(message, replyCallback)
      case message: JoinChatChannelRequestMessage =>
        onJoinChannel(message, replyCallback)
      case message: LeaveChatChannelRequestMessage =>
        onLeaveChannel(message, replyCallback)
      case message: AddUserToChatChannelRequestMessage =>
        onAddUserToChannel(message, replyCallback)
      case message: RemoveUserFromChatChannelRequestMessage =>
        onRemoveUserFromChannel(message, replyCallback)
      case message: SetChatChannelNameRequestMessage =>
        onSetChatChannelName(message, replyCallback)
      case message: SetChatChannelTopicRequestMessage =>
        onSetChatChannelTopic(message, replyCallback)
      case message: MarkChatChannelEventsSeenRequestMessage =>
        onMarkEventsSeen(message, replyCallback)
      case message: GetChatChannelsRequestMessage =>
        onGetChannels(message, replyCallback)
      case message: GetJoinedChatChannelsRequestMessage =>
        onGetJoinedChannels(replyCallback)
      case message: GetDirectChannelsRequestMessage =>
        onGetDirect(message, replyCallback)
      case message: ChatChannelHistoryRequestMessage =>
        onGetHistory(message, replyCallback)
      case message: PublishChatRequestMessage =>
        onPublishMessage(message, replyCallback)
    }
  }

  def onCreateChannel(message: CreateChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateChatChannelRequestMessage(channelId, channelType, name, topic, privateChannel, members) = message;
    val request = CreateChannelRequest(channelId, channelType, name, topic, privateChannel, members.getOrElse(Set()), sk.uid)
    chatLookupActor.ask(request).mapTo[CreateChannelResponse] onComplete {
      case Success(CreateChannelResponse(channelId)) =>
        cb.reply(CreateChatChannelResponseMessage(channelId))
      case Failure(ChatChannelException(cause)) =>
        this.handleChatChannelException(cause, cb)
      case Failure(cause) =>
        log.error(cause, "could not create channel: " + message)
        cb.unexpectedError("An unexcpeected error occurred creating the chat channel")
    }
  }

  def onRemoveChannel(message: RemoveChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveChatChannelRequestMessage(channelId) = message;
    val request = RemoveChannelRequest(channelId, sk.uid)
    handleSimpleChannelRequest(request, { () => RemoveChatChannelResponseMessage() }, cb)
  }

  def onJoinChannel(message: JoinChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatChannelRequestMessage(channelId) = message;
    val request = JoinChannelRequest(channelId, sk.uid)
    handleSimpleChannelRequest(request, { () => JoinChatChannelResponseMessage() }, cb)
  }

  def onLeaveChannel(message: LeaveChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val LeaveChatChannelRequestMessage(channelId) = message;
    val request = LeaveChannelRequest(channelId, sk.uid)
    handleSimpleChannelRequest(request, { () => LeaveChatChannelResponseMessage() }, cb)
  }

  def onAddUserToChannel(message: AddUserToChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val AddUserToChatChannelRequestMessage(channelId, userToAdd) = message;
    val request = AddUserToChannelRequest(channelId, userToAdd, sk.uid)
    handleSimpleChannelRequest(request, { () => AddUserToChatChannelResponseMessage() }, cb)
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveUserFromChatChannelRequestMessage(channelId, userToRemove) = message;
    val request = RemoveUserFromChannelRequest(channelId, userToRemove, sk.uid)
    handleSimpleChannelRequest(request, { () => RemoveUserFromChatChannelResponseMessage() }, cb)
  }

  def onSetChatChannelName(message: SetChatChannelNameRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatChannelNameRequestMessage(channelId, name) = message;
    val request = SetChannelNameRequest(channelId, name, sk.uid)
    handleSimpleChannelRequest(request, { () => SetChatChannelNameResponseMessage() }, cb)
  }

  def onSetChatChannelTopic(message: SetChatChannelTopicRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatChannelTopicRequestMessage(channelId, topic) = message;
    val request = SetChannelTopicRequest(channelId, topic, sk.uid)
    handleSimpleChannelRequest(request, { () => SetChatChannelTopicResponseMessage() }, cb)
  }

  def onMarkEventsSeen(message: MarkChatChannelEventsSeenRequestMessage, cb: ReplyCallback): Unit = {
    val MarkChatChannelEventsSeenRequestMessage(channelId, eventNumber) = message;
    val request = MarkChannelEventsSeenRequest(channelId, eventNumber, sk.uid)
    handleSimpleChannelRequest(request, { () => MarkChatChannelEventsSeenResponseMessage() }, cb)
  }

  def onPublishMessage(message: PublishChatRequestMessage, cb: ReplyCallback): Unit = {
    val PublishChatRequestMessage(channelId, msg) = message;
    val request = PublishChatMessageRequest(channelId, msg, sk)
    handleSimpleChannelRequest(request, { () => PublishChatResponseMessage() }, cb)
  }

  def onGetChannels(message: GetChatChannelsRequestMessage, cb: ReplyCallback): Unit = {
    val GetChatChannelsRequestMessage(ids) = message;
    val request = GetChannelsRequest(ids, sk.uid)
    chatLookupActor.ask(request).mapTo[GetChannelsResponse] onComplete {
      case Success(GetChannelsResponse(channels)) =>
        val info = channels.map(toChannelInfoData(_))
        cb.reply(GetChatChannelsResponseMessage(info))
      case Failure(cause) => 
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetDirect(message: GetDirectChannelsRequestMessage, cb: ReplyCallback): Unit = {
    val GetDirectChannelsRequestMessage(usernameLists) = message;
    val request = GetDirectChannelsRequest(sk.uid, usernameLists)
    chatLookupActor.ask(request).mapTo[GetDirectChannelsResponse] onComplete {
      case Success(GetDirectChannelsResponse(channels)) =>
        val info = channels.map(toChannelInfoData(_))
        cb.reply(GetChatChannelsResponseMessage(info))
      case Failure(cause) => 
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetJoinedChannels(cb: ReplyCallback): Unit = {
    val request = GetJoinedChannelsRequest(sk.uid)
    chatLookupActor.ask(request).mapTo[GetJoinedChannelsResponse] onComplete {
      case Success(GetJoinedChannelsResponse(channels)) =>
        val info = channels.map(toChannelInfoData(_))
        cb.reply(GetChatChannelsResponseMessage(info))
      case Failure(cause) => 
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetHistory(message: ChatChannelHistoryRequestMessage, cb: ReplyCallback): Unit = {
    val ChatChannelHistoryRequestMessage(channelId, limit, offset, forward, events) = message;
    val request = GetChannelHistoryRequest(channelId, sk.uid, limit, offset, forward, events)
    chatChannelActor.ask(request).mapTo[GetChannelHistoryResponse] onComplete {
      case Success(GetChannelHistoryResponse(events)) =>
        val eventData = events.map(toChannelEventDatat(_))
        cb.reply(ChatChannelHistoryResponseMessage(eventData))
      case Failure(cause) => 
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def handleSimpleChannelRequest(request: Any, response: () => OutgoingProtocolResponseMessage, cb: ReplyCallback): Unit = {
    chatChannelActor.ask(request).mapTo[Unit] onComplete {
      case Success(()) =>
        cb.reply(response())
      case Failure(ChatChannelException(cause)) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def handleUnexpectedError(request: Any, cause: Throwable, cb: ReplyCallback): Unit = {
    log.error(cause, "Unexpected error processing chat request" + request)
    cb.unexpectedError("Unexpected error processing chat request")
  }

  private[this] def handleChatChannelException(cause: ChatChannelException, cb: ReplyCallback): Unit = {
    cause match {
      case ChannelNotFoundException(channelId) =>
        cb.expectedError(
          "channel_not_found",
          s"Could not complete the request because a channel with id '${channelId}' does not exist.",
          Map("channelId" -> channelId))
      case ChannelNotJoinedException(channelId) =>
        cb.expectedError(
          "channel_not_joined",
          s"Could not complete the request the user is not joined to the channel: '${channelId}'",
          Map("channelId" -> channelId))
      case ChannelAlreadyExistsException(channelId) =>
        cb.expectedError(
          "channel_already_exists",
          s"Could not complete the request because a channel with id '${channelId}' aready exists.",
          Map("channelId" -> channelId))
      case ChannelAlreadyJoinedException(channelId) =>
        cb.expectedError(
          "channel_already_joined",
          s"Could not complete the request the user is already joined to the channel: '${channelId}'",
          Map("channelId" -> channelId))
    }
  }
  
  private[this] def toChannelInfoData(state: ChatChannelInfo): ChatChannelInfoData = {
    ???
  }
  
  private[this] def toChannelEventDatat(event: ChatChannelEvent): ChatChannelEventData = {
    ???
  }
}
