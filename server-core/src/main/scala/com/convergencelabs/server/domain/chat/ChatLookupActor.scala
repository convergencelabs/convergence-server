package com.convergencelabs.server.domain.chat

import java.time.Instant

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.ChatInfo
import com.convergencelabs.server.datastore.domain.ChatStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.PermissionsStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.chat.ChatMessages.ChatAlreadyExistsException
import com.convergencelabs.server.domain.chat.ChatStateManager.ChatPermissions

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.actor.actorRef2Scala
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ChatMembership
import com.convergencelabs.server.datastore.domain.ChatType
import com.convergencelabs.server.datastore.domain.schema.ChatClass

object ChatLookupActor {

  val RelativePath = "chatChannelLookupActor"

  def props(provider: DomainPersistenceProvider): Props = Props(new ChatLookupActor(provider))

  trait ChatStoreRequest
  case class FindChatInfo(filter: Option[String], offset: Option[Int], limit: Option[Int]) extends ChatStoreRequest
  case class GetChatInfo(chatId: String) extends ChatStoreRequest

  case class CreateChatRequest(
    chatId: Option[String],
    createdBy: DomainUserId,
    chatType: ChatType.Value,
    membership: ChatMembership.Value,
    name: Option[String],
    topic: Option[String],
    members: Set[DomainUserId]) extends ChatStoreRequest

  case class CreateChatResponse(channelId: String)
  
  case class GetChannelsRequest(userId: DomainUserId, ids: List[String])
  case class GetChannelsResponse(channels: List[ChatInfo])

  case class ChannelsExistsRequest(userId: DomainUserId, ids: List[String])
  case class ChannelsExistsResponse(channels: List[Boolean])

  case class GetJoinedChannelsRequest(userId: DomainUserId)
  case class GetJoinedChannelsResponse(channels: List[ChatInfo])

  case class GetDirectChannelsRequest(userId: DomainUserId, userLists: List[Set[DomainUserId]])
  case class GetDirectChannelsResponse(channels: List[ChatInfo])

  val DefaultPermissions = List()
}

class ChatLookupActor private[domain] (provider: DomainPersistenceProvider) extends Actor with ActorLogging {

  import ChatLookupActor._

  var chatStore: ChatStore = provider.chatStore
  var permissionsStore: PermissionsStore = provider.permissionsStore

  def receive: Receive = {
    case message: CreateChatRequest =>
      onCreateChannel(message)
    case message: GetChannelsRequest =>
      onGetChannels(message)
    case message: GetJoinedChannelsRequest =>
      onGetJoinedChannels(message)
    case message: GetDirectChannelsRequest =>
      onGetDirect(message)
    case message: ChannelsExistsRequest =>
      onExists(message)
    case message: FindChatInfo =>
      onGetChats(message)
      case message: GetChatInfo =>
      onGetChat(message)
  }

  def onCreateChannel(message: CreateChatRequest): Unit = {
    val CreateChatRequest(channelId, createdBy, chatType, membership, name, topic, members) = message
    hasPermission(createdBy, ChatPermissions.CreateChannel).map { _ =>
      (for {
        id <- createChannel(channelId, chatType, membership, name, topic, members, createdBy)
        forRecord <- chatStore.getChatRid(id)
        _ <- permissionsStore.addUserPermissions(ChatStateManager.AllChatPermissions, createdBy, Some(forRecord))
      } yield {
        sender ! CreateChatResponse(id)
      }) recover {
        case e: DuplicateValueException =>
          // FIXME how to deal with this? The channel id should only conflict if it was
          // defined by the user.
          val cId = channelId.get
          sender ! Status.Failure(ChatAlreadyExistsException(cId))
        case NonFatal(cause) =>
          sender ! Status.Failure(cause)
      }
    }
  }

  def onGetChats(message: FindChatInfo): Unit = {
    val FindChatInfo(filter, offset, limit) = message
    chatStore.findChats(None, filter, offset, limit).map { info =>
      sender ! info
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }
  
  def onGetChat(message: GetChatInfo): Unit = {
    val GetChatInfo(chatId) = message
    chatStore.getChatInfo(chatId).map { info =>
      sender ! info
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  def onGetChannels(message: GetChannelsRequest): Unit = {
    val GetChannelsRequest(sk, ids) = message
    // TODO support multiple.
    val id = ids(0)
    chatStore.getChatInfo(id).map { info =>
      if (info.membership == ChatMembership.Private) {
        sender ! GetChannelsResponse(List(info))
      } else {
        hasPermission(sk, ChatPermissions.JoinChannel).map { _ =>
          sender ! GetChannelsResponse(List(info))
        }
      } recover {
        case cause: UnauthorizedException =>
          sender ! ChannelsExistsResponse(List(false))
        case cause: Exception =>
          sender ! Status.Failure(cause)
      }
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  def onExists(message: ChannelsExistsRequest): Unit = {
    val ChannelsExistsRequest(sk, ids) = message
    // TODO support multiple.
    // FIXME this should be an option or something.
    val id = ids(0)
    chatStore.getChatInfo(id).map { info =>
      if (info.membership == ChatMembership.Private) {
        sender ! ChannelsExistsResponse(List(true))
      } else {
        hasPermission(sk, ChatPermissions.JoinChannel).map { _ =>
          sender ! ChannelsExistsResponse(List(true))
        } recover {
          case cause: UnauthorizedException =>
            sender ! ChannelsExistsResponse(List(false))
          case cause: Exception =>
            sender ! Status.Failure(cause)
        }
      }
    } recover {
      case cause: EntityNotFoundException =>
        sender ! ChannelsExistsResponse(List(false))
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  def onGetDirect(message: GetDirectChannelsRequest): Unit = {
    val GetDirectChannelsRequest(userId, userIdList) = message;
    // FIXME support multiple channel requests.

    // The channel must contain the user who is looking it up and any other users
    val userIds = (userIdList(0) + userId)

    chatStore.getDirectChatInfoByUsers(userIds) flatMap {
      _ match {
        case Some(c) =>
          // The channel exists, just return it.
          Success(c)
        case None =>
          // Does not exists, so create it.
          createChannel(None, ChatType.Direct, ChatMembership.Private, None, None, userIds.toSet, userId) flatMap { channelId =>
            // Create was successful, now let's just get the channel.
            chatStore.getChatInfo(channelId)
          } recoverWith {
            case DuplicateValueException(ChatClass.Fields.Members, _, _) =>
              // The channel already exists based on the members, this must have been a race condition.
              // So just try to get it again
              chatStore.getDirectChatInfoByUsers(userIds) flatMap {
                _ match {
                  case Some(c) =>
                    // Yup it's there.
                    Success(c)
                  case None =>
                    // We are now in a bizaro world where we are told the channel exists, but can
                    // not look it up.
                    Failure(new IllegalStateException("Can not create direct channel, due to an unexpected error"))
                }
              }
          }
      }
    } map { channel =>
      sender ! GetDirectChannelsResponse(List(channel))
    } recover {
      case cause: Throwable =>
        sender ! Status.Failure(cause)
    }
  }

  def onGetJoinedChannels(message: GetJoinedChannelsRequest): Unit = {
    val GetJoinedChannelsRequest(userId) = message
    this.chatStore.getJoinedChats(userId) map { channels =>
      sender ! GetJoinedChannelsResponse(channels)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def createChannel(
    chatId: Option[String],
    ct: ChatType.Value,
    membership: ChatMembership.Value,
    name: Option[String],
    topic: Option[String],
    members: Set[DomainUserId],
    createdBy: DomainUserId): Try[String] = {

    this.chatStore.createChat(
      chatId, ct, Instant.now(), membership, name.getOrElse(""), topic.getOrElse(""), Some(members), createdBy)
  }

  private[this] def hasPermission(userId: DomainUserId, permission: String): Try[Unit] = {
    Success(())
    if (userId.isConvergence) {
      Success(())
    } else {
      for {
        hasPermission <- permissionsStore.hasPermission(userId, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }

  private[this] def hasPermission(userId: DomainUserId, channelId: String, permission: String): Try[Unit] = {
    if (userId.isConvergence) {
      Success(())
    } else {
      for {
        channelRid <- chatStore.getChatRid(channelId)
        hasPermission <- permissionsStore.hasPermission(userId, channelRid, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }
}
