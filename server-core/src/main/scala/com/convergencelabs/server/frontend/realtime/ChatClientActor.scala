package com.convergencelabs.server.frontend.realtime

import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.datastore.domain.ChatCreatedEvent
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.chat.ChatChannelActor
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.ChannelsExistsRequest
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.ChannelsExistsResponse
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetChannelsRequest
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetChannelsResponse
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetDirectChannelsRequest
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetDirectChannelsResponse
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetJoinedChannelsRequest
import com.convergencelabs.server.domain.chat.ChatChannelLookupActor.GetJoinedChannelsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.AddUserToChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelAlreadyExistsException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelAlreadyJoinedException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNameChanged
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNotJoinedException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelRemoved
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelTopicChanged
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChatChannelBroadcastMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChatChannelException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.CreateChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.CreateChannelResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllGroupChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllGroupChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllUserChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetAllUserChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetChannelHistoryRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetChannelHistoryResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetClientChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetClientChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetGroupChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetGroupChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetUserChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetUserChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetWorldChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GetWorldChatPermissionsResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.GroupPermissions
import com.convergencelabs.server.domain.chat.ChatChannelMessages.InvalidChannelMessageExcpetion
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.JoinChannelResponse
import com.convergencelabs.server.domain.chat.ChatChannelMessages.LeaveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.MarkChannelEventsSeenRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.PublishChatMessageRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoteChatMessage
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.RemoveUserFromChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.SetChannelNameRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.SetChannelTopicRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.SetChatPermissionsRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserAddedToChannel
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserJoinedChannel
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserLeftChannel
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserPermissions
import com.convergencelabs.server.domain.chat.ChatChannelMessages.UserRemovedFromChannel
import com.convergencelabs.server.domain.model.SessionKey


import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.cluster.pubsub.DistributedPubSub
import akka.cluster.pubsub.DistributedPubSubMediator.Subscribe
import akka.cluster.pubsub.DistributedPubSubMediator.SubscribeAck
import akka.util.Timeout
import akka.pattern.ask
import com.convergencelabs.server.domain.chat.ChatChannelSharding
import io.convergence.proto.Incoming
import io.convergence.proto.Chat
import io.convergence.proto.Request
import io.convergence.proto.Permissions
import io.convergence.proto.permissions.GetGroupPermissionsRequestMessage
import io.convergence.proto.permissions.AddPermissionsRequestMessage
import io.convergence.proto.permissions.GetClientPermissionsRequestMessage
import io.convergence.proto.permissions.AddPermissionsReponseMessage
import io.convergence.proto.chat.PublishChatRequestMessage
import io.convergence.proto.permissions.GetClientPermissionsReponseMessage
import io.convergence.proto.chat.ChatChannelRemovedMessage
import io.convergence.proto.chat.ChatChannelTopicSetMessage
import io.convergence.proto.chat.ChatChannelInfoData
import io.convergence.proto.permissions.GetUserPermissionsReponseMessage
import io.convergence.proto.chat.ChatTopicChangedEventData
import io.convergence.proto.chat.UserJoinedChatChannelMessage
import io.convergence.proto.chat.ChatChannelHistoryRequestMessage
import io.convergence.proto.chat.RemoveChatChannelResponseMessage
import io.convergence.proto.chat.RemoteChatMessageMessage
import io.convergence.proto.chat.ChatChannelsExistsResponseMessage
import io.convergence.proto.chat.ChatUserLeftEventData
import io.convergence.proto.chat.LeaveChatChannelRequestMessage
import io.convergence.proto.chat.JoinChatChannelRequestMessage
import io.convergence.proto.permissions.GetUserPermissionsRequestMessage
import io.convergence.proto.chat.CreateChatChannelRequestMessage
import io.convergence.proto.permissions.SetPermissionsRequestMessage
import io.convergence.proto.chat.MarkChatChannelEventsSeenRequestMessage
import io.convergence.proto.chat.JoinChatChannelResponseMessage
import io.convergence.proto.chat.ChatChannelHistoryResponseMessage
import io.convergence.proto.permissions.GetAllGroupPermissionsRequestMessage
import io.convergence.proto.chat.ChatUserJoinedEventData
import io.convergence.proto.chat.LeaveChatChannelResponseMessage
import io.convergence.proto.chat.GetChatChannelsRequestMessage
import io.convergence.proto.chat.PublishChatResponseMessage
import io.convergence.proto.permissions.GetWorldPermissionsRequestMessage
import io.convergence.proto.chat.SetChatChannelTopicRequestMessage
import io.convergence.proto.chat.ChatCreatedEventData
import io.convergence.proto.permissions.RemovePermissionsReponseMessage
import io.convergence.proto.chat.AddUserToChatChannelResponseMessage
import io.convergence.proto.chat.UserAddedToChatChannelMessage
import io.convergence.proto.chat.CreateChatChannelResponseMessage
import io.convergence.proto.chat.AddUserToChatChannelRequestMessage
import io.convergence.proto.chat.ChatChannelNameSetMessage
import io.convergence.proto.chat.RemoveUserFromChatChannelResponseMessage
import io.convergence.proto.chat.GetJoinedChatChannelsRequestMessage
import io.convergence.proto.permissions.GetAllUserPermissionsRequestMessage
import io.convergence.proto.chat.ChatChannelsExistsRequestMessage
import io.convergence.proto.chat.SetChatChannelNameResponseMessage
import io.convergence.proto.chat.ChatUserRemovedEventData
import io.convergence.proto.chat.ChatUserAddedEventData
import io.convergence.proto.chat.MarkChatChannelEventsSeenResponseMessage
import io.convergence.proto.permissions.GetAllUserPermissionsReponseMessage
import io.convergence.proto.permissions.SetPermissionsReponseMessage
import io.convergence.proto.permissions.GetAllGroupPermissionsReponseMessage
import io.convergence.proto.chat.ChatMessageEventData
import io.convergence.proto.chat.UserLeftChatChannelMessage
import io.convergence.proto.chat.GetChatChannelsResponseMessage
import io.convergence.proto.chat.RemoveUserFromChatChannelRequestMessage
import io.convergence.proto.permissions.RemovePermissionsRequestMessage
import io.convergence.proto.permissions.GetGroupPermissionsReponseMessage
import io.convergence.proto.chat.UserRemovedFromChatChannelMessage
import io.convergence.proto.chat.SetChatChannelTopicResponseMessage
import io.convergence.proto.chat.ChatNameChangedEventData
import io.convergence.proto.chat.SetChatChannelNameRequestMessage
import io.convergence.proto.chat.RemoveChatChannelRequestMessage
import io.convergence.proto.chat.GetDirectChannelsRequestMessage
import io.convergence.proto.chat.ChatChannelEventData
import com.google.protobuf.timestamp.Timestamp
import com.convergencelabs.server.frontend.realtime.ImplicitMessageConversions._
import io.convergence.proto.permissions.PermissionsSet
import io.convergence.proto.permissions.GetWorldPermissionsResponseMessage
import io.convergence.proto.Response

object ChatClientActor {
  def props(
    domainFqn: DomainFqn,
    chatLookupActor: ActorRef,
    sk: SessionKey,
    requestTimeout: Timeout): Props =
    Props(new ChatClientActor(domainFqn, chatLookupActor, sk, requestTimeout))
}

class ChatClientActor(
    domainFqn: DomainFqn,
    chatLookupActor: ActorRef,
    sk: SessionKey,
    implicit val requestTimeout: Timeout) extends Actor with ActorLogging {

  implicit val ec = context.dispatcher

  val chatChannelActor = ChatChannelSharding.shardRegion(context.system)
  
  val mediator = DistributedPubSub(context.system).mediator
  val chatTopicName = ChatChannelActor.getChatUsernameTopicName(sk.uid)

  mediator ! Subscribe(chatTopicName, self)

  def receive: Receive = {
    case SubscribeAck(Subscribe(chatTopicName, _, _)) ⇒
      log.debug("Subscribe to chat channel for user")

    case MessageReceived(message: Incoming with Chat) =>
      onMessageReceived(message)
    case RequestReceived(message: Request with Chat, replyPromise) =>
      onRequestReceived(message, replyPromise)
    case RequestReceived(message: Request with Permissions, replyPromise) =>
      onPermissionsRequestReceived(message, replyPromise)

    case message: ChatChannelBroadcastMessage =>
      handleBroadcastMessage(message)

    case x: Any =>
      unhandled(x)
  }

  private[this] def handleBroadcastMessage(message: ChatChannelBroadcastMessage): Unit = {
    message match {
      // Broadcast messages
      case RemoteChatMessage(channelId, eventNumber, timestamp, sk, message) =>
        if (this.sk != sk) {
          // We don't need to send this back to ourselves.9
          context.parent ! RemoteChatMessageMessage(channelId, eventNumber, 
              Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), 
              Some(io.convergence.proto.authentication.SessionKey(sk.uid, sk.sid)), message)
        }

      case UserJoinedChannel(channelId, eventNumber, timestamp, username) =>
        context.parent ! UserJoinedChatChannelMessage(channelId, eventNumber, 
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), username)

      case UserLeftChannel(channelId, eventNumber, timestamp, username) =>
        context.parent ! UserLeftChatChannelMessage(channelId, eventNumber, 
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), username)

      case UserAddedToChannel(channelId, eventNumber, timestamp, username, addedBy) =>
        context.parent ! UserAddedToChatChannelMessage(channelId, eventNumber, 
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), username, addedBy)

      case UserRemovedFromChannel(channelId, eventNumber, timestamp, username, removedBy) =>
        context.parent ! UserRemovedFromChatChannelMessage(channelId, eventNumber, 
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), username, removedBy)

      case ChannelRemoved(channelId) =>
        context.parent ! ChatChannelRemovedMessage(channelId)

      case ChannelNameChanged(channelId, eventNumber, timestamp, name, setBy) =>
        context.parent ! ChatChannelNameSetMessage(channelId, eventNumber, 
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), setBy, name)

      case ChannelTopicChanged(channelId, eventNumber, timestamp, name, setBy) =>
        context.parent ! ChatChannelTopicSetMessage(channelId, eventNumber, 
            Some(Timestamp(timestamp.getEpochSecond, timestamp.getNano)), setBy, name)
    }
  }

  //
  // Incoming Messages
  //

  def onMessageReceived(message: Incoming with Chat): Unit = {
    log.error("Chat channel actor received a non-request message")
  }

  def onRequestReceived(message: Request with Chat, replyCallback: ReplyCallback): Unit = {
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
      case message: ChatChannelsExistsRequestMessage =>
        onChannelsExist(message, replyCallback)
    }
  }

  def onPermissionsRequestReceived(message: Request with Permissions, replyCallback: ReplyCallback): Unit = {
    message match {
      case message: AddPermissionsRequestMessage =>
        onAddChatPermissions(message, replyCallback)
      case message: RemovePermissionsRequestMessage =>
        onRemoveChatPermissions(message, replyCallback)
      case message: SetPermissionsRequestMessage =>
        onSetChatPermissions(message, replyCallback)
      case message: GetClientPermissionsRequestMessage =>
        onGetClientChatPermissions(message, replyCallback)
      case message: GetWorldPermissionsRequestMessage =>
        onGetWorldPermissions(message, replyCallback)
      case message: GetAllUserPermissionsRequestMessage =>
        onGetAllUserPermissions(message, replyCallback)
      case message: GetUserPermissionsRequestMessage =>
        onGetUserPermissions(message, replyCallback)
      case message: GetAllGroupPermissionsRequestMessage =>
        onGetAllGroupPermissions(message, replyCallback)
      case message: GetGroupPermissionsRequestMessage =>
        onGetGroupPermissions(message, replyCallback)
    }
  }

  def onCreateChannel(message: CreateChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val CreateChatChannelRequestMessage(channelId, channelType, name, topic, privateChannel, members) = message;
    val request = CreateChannelRequest(channelId, sk, channelType, name, topic, privateChannel, members.toSet)
    chatLookupActor.ask(request).mapTo[CreateChannelResponse] onComplete {
      case Success(CreateChannelResponse(channelId)) =>
        cb.reply(CreateChatChannelResponseMessage(channelId))
      case Failure(cause: ChatChannelException) =>
        this.handleChatChannelException(cause, cb)
      case Failure(cause) =>
        log.error(cause, "could not create channel: " + message)
        cb.unexpectedError("An unexcpeected error occurred creating the chat channel")
    }
  }

  def onRemoveChannel(message: RemoveChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveChatChannelRequestMessage(channelId) = message;
    val request = RemoveChannelRequest(domainFqn, channelId, sk)
    handleSimpleChannelRequest(request, { () => RemoveChatChannelResponseMessage() }, cb)
  }

  def onJoinChannel(message: JoinChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val JoinChatChannelRequestMessage(channelId) = message;
    val request = JoinChannelRequest(domainFqn, channelId, sk, self)
    chatChannelActor.ask(request).mapTo[JoinChannelResponse] onComplete {
      case Success(JoinChannelResponse(info)) =>
        cb.reply(JoinChatChannelResponseMessage(Some(info)))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onLeaveChannel(message: LeaveChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val LeaveChatChannelRequestMessage(channelId) = message;
    val request = LeaveChannelRequest(domainFqn, channelId, sk, self)
    handleSimpleChannelRequest(request, { () => LeaveChatChannelResponseMessage() }, cb)
  }

  def onAddUserToChannel(message: AddUserToChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val AddUserToChatChannelRequestMessage(channelId, userToAdd) = message;
    val request = AddUserToChannelRequest(domainFqn, channelId, sk, userToAdd)
    handleSimpleChannelRequest(request, { () => AddUserToChatChannelResponseMessage() }, cb)
  }

  def onRemoveUserFromChannel(message: RemoveUserFromChatChannelRequestMessage, cb: ReplyCallback): Unit = {
    val RemoveUserFromChatChannelRequestMessage(channelId, userToRemove) = message;
    val request = RemoveUserFromChannelRequest(domainFqn, channelId, sk, userToRemove)
    handleSimpleChannelRequest(request, { () => RemoveUserFromChatChannelResponseMessage() }, cb)
  }

  def onSetChatChannelName(message: SetChatChannelNameRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatChannelNameRequestMessage(channelId, name) = message;
    val request = SetChannelNameRequest(domainFqn, channelId, sk, name)
    handleSimpleChannelRequest(request, { () => SetChatChannelNameResponseMessage() }, cb)
  }

  def onSetChatChannelTopic(message: SetChatChannelTopicRequestMessage, cb: ReplyCallback): Unit = {
    val SetChatChannelTopicRequestMessage(channelId, topic) = message;
    val request = SetChannelTopicRequest(domainFqn, channelId, sk, topic)
    handleSimpleChannelRequest(request, { () => SetChatChannelTopicResponseMessage() }, cb)
  }

  def onMarkEventsSeen(message: MarkChatChannelEventsSeenRequestMessage, cb: ReplyCallback): Unit = {
    val MarkChatChannelEventsSeenRequestMessage(channelId, eventNumber) = message;
    val request = MarkChannelEventsSeenRequest(domainFqn, channelId, sk, eventNumber)
    handleSimpleChannelRequest(request, { () => MarkChatChannelEventsSeenResponseMessage() }, cb)
  }

  def onAddChatPermissions(message: AddPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val AddPermissionsRequestMessage(idType, id, world, user, group) = message;
    val groupPermissions = group map {
        case (groupId, permissions) => GroupPermissions(groupId, permissions.permissions.toSet)
    }

    val userPermissions = user map {
        case (username, permissions) => UserPermissions(username, permissions.permissions.toSet)
    }

    val request = AddChatPermissionsRequest(domainFqn, id, sk, Some(world.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => AddPermissionsReponseMessage() }, cb)
  }

  def onRemoveChatPermissions(message: RemovePermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val RemovePermissionsRequestMessage(idType, id, world, user, group) = message;
    val groupPermissions = group map {
        case (groupId, permissions) => GroupPermissions(groupId, permissions.permissions.toSet)
    }
    val userPermissions = user map {
        case (username, permissions) => UserPermissions(username, permissions.permissions.toSet)
    }
    val request = RemoveChatPermissionsRequest(domainFqn, id, sk, Some(world.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => RemovePermissionsReponseMessage() }, cb)
  }

  def onSetChatPermissions(message: SetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val SetPermissionsRequestMessage(idType, id, world, user, group) = message;
    val groupPermissions = group map {
        case (groupId, permissions) => GroupPermissions(groupId, permissions.permissions.toSet)
    }
    val userPermissions = user map {
        case (username, permissions) => UserPermissions(username, permissions.permissions.toSet)
    }
    val request = SetChatPermissionsRequest(domainFqn, id, sk, Some(world.toSet), Some(userPermissions.toSet), Some(groupPermissions.toSet))
    handleSimpleChannelRequest(request, { () => SetPermissionsReponseMessage() }, cb)
  }

  def onGetClientChatPermissions(message: GetClientPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetClientPermissionsRequestMessage(idType, id) = message;
    val request = GetClientChatPermissionsRequest(domainFqn, id, sk)
    chatChannelActor.ask(request).mapTo[GetClientChatPermissionsResponse] onComplete {
      case Success(GetClientChatPermissionsResponse(permissions)) =>
        cb.reply(GetClientPermissionsReponseMessage(permissions.toSeq))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetWorldPermissions(message: GetWorldPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetWorldPermissionsRequestMessage(idType, id) = message;
    val request = GetWorldChatPermissionsRequest(domainFqn, id, sk)
    chatChannelActor.ask(request).mapTo[GetWorldChatPermissionsResponse] onComplete {
      case Success(GetWorldChatPermissionsResponse(permissions)) =>
        cb.reply(GetWorldPermissionsResponseMessage(permissions.toSeq))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetAllUserPermissions(message: GetAllUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllUserPermissionsRequestMessage(idType, id) = message;
    val request = GetAllUserChatPermissionsRequest(domainFqn, id, sk)
    chatChannelActor.ask(request).mapTo[GetAllUserChatPermissionsResponse] onComplete {
      case Success(GetAllUserChatPermissionsResponse(users)) =>
        cb.reply(GetAllUserPermissionsReponseMessage(users map {case (key, value) => (key, PermissionsSet(value.toSeq))}))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetAllGroupPermissions(message: GetAllGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetAllGroupPermissionsRequestMessage(idType, id) = message;
    val request = GetAllGroupChatPermissionsRequest(domainFqn, id, sk)
    chatChannelActor.ask(request).mapTo[GetAllGroupChatPermissionsResponse] onComplete {
      case Success(GetAllGroupChatPermissionsResponse(groups)) =>
        cb.reply(GetAllGroupPermissionsReponseMessage(groups map {case (key, value) => (key, PermissionsSet(value.toSeq))}))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetUserPermissions(message: GetUserPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetUserPermissionsRequestMessage(idType, id, username) = message;
    val request = GetUserChatPermissionsRequest(domainFqn, id, username, sk)
    chatChannelActor.ask(request).mapTo[GetUserChatPermissionsResponse] onComplete {
      case Success(GetUserChatPermissionsResponse(permissions)) =>
        cb.reply(GetUserPermissionsReponseMessage(permissions.toSeq))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetGroupPermissions(message: GetGroupPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetGroupPermissionsRequestMessage(idType, id, groupId) = message;
    val request = GetGroupChatPermissionsRequest(domainFqn, id, groupId, sk)
    chatChannelActor.ask(request).mapTo[GetGroupChatPermissionsResponse] onComplete {
      case Success(GetGroupChatPermissionsResponse(permissions)) =>
        cb.reply(GetGroupPermissionsReponseMessage(permissions.toSeq))
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onPublishMessage(message: PublishChatRequestMessage, cb: ReplyCallback): Unit = {
    val PublishChatRequestMessage(channelId, msg) = message;
    val request = PublishChatMessageRequest(domainFqn, channelId, sk, msg)
    handleSimpleChannelRequest(request, { () => PublishChatResponseMessage() }, cb)
  }

  def onChannelsExist(message: ChatChannelsExistsRequestMessage, cb: ReplyCallback): Unit = {
    val ChatChannelsExistsRequestMessage(channelIds) = message;
    val request = ChannelsExistsRequest(sk, channelIds.toList)
    chatLookupActor.ask(request).mapTo[ChannelsExistsResponse] onComplete {
      case Success(ChannelsExistsResponse(channels)) =>
        cb.reply(ChatChannelsExistsResponseMessage(channels))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetChannels(message: GetChatChannelsRequestMessage, cb: ReplyCallback): Unit = {
    val GetChatChannelsRequestMessage(ids) = message;
    val request = GetChannelsRequest(sk, ids.toList)
    chatLookupActor.ask(request).mapTo[GetChannelsResponse] onComplete {
      case Success(GetChannelsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage(_))
        cb.reply(GetChatChannelsResponseMessage(info))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetDirect(message: GetDirectChannelsRequestMessage, cb: ReplyCallback): Unit = {
    val GetDirectChannelsRequestMessage(usernameLists) = message;
    val request = GetDirectChannelsRequest(sk.uid, usernameLists.map(_.strings.toSet).toList)
    chatLookupActor.ask(request).mapTo[GetDirectChannelsResponse] onComplete {
      case Success(GetDirectChannelsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage(_))
        cb.reply(GetChatChannelsResponseMessage(info))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetJoinedChannels(cb: ReplyCallback): Unit = {
    val request = GetJoinedChannelsRequest(sk.uid)
    chatLookupActor.ask(request).mapTo[GetJoinedChannelsResponse] onComplete {
      case Success(GetJoinedChannelsResponse(channels)) =>
        val info = channels.map(channelInfoToMessage(_))
        cb.reply(GetChatChannelsResponseMessage(info))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  def onGetHistory(message: ChatChannelHistoryRequestMessage, cb: ReplyCallback): Unit = {
    val ChatChannelHistoryRequestMessage(channelId, limit, offset, forward, eventFilter) = message;
    val mappedEvents = eventFilter.map(toChannelEventCode(_))
    val request = GetChannelHistoryRequest(domainFqn, channelId, sk, limit, offset, forward, Some(mappedEvents.toList))
    chatChannelActor.ask(request).mapTo[GetChannelHistoryResponse] onComplete {
      case Success(GetChannelHistoryResponse(events)) =>
        val eventData = events.map(channelEventToMessage(_))
        cb.reply(ChatChannelHistoryResponseMessage(eventData.toSeq))
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def handleSimpleChannelRequest(request: Any, response: () => Response, cb: ReplyCallback): Unit = {
    chatChannelActor.ask(request).mapTo[Unit] onComplete {
      case Success(()) =>
        val r = response()
        cb.reply(r)
      case Failure(cause: ChatChannelException) =>
        handleChatChannelException(cause, cb)
      case Failure(cause) =>
        handleUnexpectedError(request, cause, cb)
    }
  }

  private[this] def handleUnexpectedError(request: Any, cause: Throwable, cb: ReplyCallback): Unit = {
    log.error(cause, "Unexpected error processing chat request " + request)
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
      case InvalidChannelMessageExcpetion(message) =>
        cb.expectedError(
          "invalid_channel_message",
          s"The message that was sent was not valid for this type of channel: '${message}'",
          Map())
    }
  }


  private[this] def toChannelEventCode: PartialFunction[Int, String] = {
    case 0 => "created"
    case 1 => "message"
    case 2 => "user_joined"
    case 3 => "user_left"
    case 4 => "user_added"
    case 5 => "user_removed"
    case 6 => "name_changed"
    case 7 => "topic_changed"
  }
}
