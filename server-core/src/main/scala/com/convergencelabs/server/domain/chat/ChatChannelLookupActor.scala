package com.convergencelabs.server.domain.chat

import java.time.Instant

import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.util.control.NonFatal

import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.datastore.domain.ChatChannelStore
import com.convergencelabs.server.datastore.domain.ChatChannelStore.ChannelType
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.datastore.domain.PermissionsStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserId
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelAlreadyExistsException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.CreateChannelRequest
import com.convergencelabs.server.domain.chat.ChatChannelMessages.CreateChannelResponse
import com.convergencelabs.server.domain.chat.ChatChannelStateManager.ChatPermissions

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.Status
import akka.actor.actorRef2Scala
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider

object ChatChannelLookupActor {

  val RelativePath = "chatChannelLookupActor"

  def props(provider: DomainPersistenceProvider): Props = Props(new ChatChannelLookupActor(provider))

  trait ChatStoreRequest
  case class GetChats(offset: Option[Int], limit: Option[Int]) extends ChatStoreRequest

  case class GetChannelsRequest(userId: DomainUserId, ids: List[String])
  case class GetChannelsResponse(channels: List[ChatChannelInfo])

  case class ChannelsExistsRequest(userId: DomainUserId, ids: List[String])
  case class ChannelsExistsResponse(channels: List[Boolean])

  case class GetJoinedChannelsRequest(userId: DomainUserId)
  case class GetJoinedChannelsResponse(channels: List[ChatChannelInfo])

  case class GetDirectChannelsRequest(userId: DomainUserId, userLists: List[Set[DomainUserId]])
  case class GetDirectChannelsResponse(channels: List[ChatChannelInfo])

  val DefaultPermissions = List()
}

class ChatChannelLookupActor private[domain] (provider: DomainPersistenceProvider) extends Actor with ActorLogging {

  import ChatChannelLookupActor._

  var chatChannelStore: ChatChannelStore = provider.chatChannelStore
  var permissionsStore: PermissionsStore = provider.permissionsStore

  def receive: Receive = {
    case message: CreateChannelRequest =>
      onCreateChannel(message)
    case message: GetChannelsRequest =>
      onGetChannels(message)
    case message: GetJoinedChannelsRequest =>
      onGetJoinedChannels(message)
    case message: GetDirectChannelsRequest =>
      onGetDirect(message)
    case message: ChannelsExistsRequest =>
      onExists(message)
    case message: GetChats =>
      onGetChats(message)
  }

  def onCreateChannel(message: CreateChannelRequest): Unit = {
    val CreateChannelRequest(channelId, createdBy, channelType, channelMembership, name, topic, members) = message
    hasPermission(createdBy.userId, ChatPermissions.CreateChannel).map { _ =>
      ChannelType.withNameOpt(channelType) match {
        case Some(ct) =>
          val isPrivate = channelMembership.toLowerCase match {
            case "private" => true
            case _ => false
          }

          (for {
            id <- createChannel(channelId, ct, isPrivate, name, topic, members, createdBy.userId)
            forRecord <- chatChannelStore.getChatChannelRid(id)
            _ <- permissionsStore.addUserPermissions(ChatChannelStateManager.AllChatPermissions, createdBy.userId, Some(forRecord))
          } yield {
            sender ! CreateChannelResponse(id)
          }) recover {
            case e: DuplicateValueException =>
              // FIXME how to deal with this? The channel id should only conflict if it was
              // defined by the user.
              val cId = channelId.get
              sender ! Status.Failure(ChannelAlreadyExistsException(cId))
            case NonFatal(cause) =>
              sender ! Status.Failure(cause)
          }
        case None =>
          sender ! Status.Failure(new IllegalArgumentException(s"Invalid channel type: ${channelType}"))
      }
      //TODO: Add recover
    }
  }
  
  def onGetChats(message: GetChats): Unit = {
    val GetChats(offset, limit) = message
    chatChannelStore.getChatChannels(None, offset, limit).map { info =>
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
    chatChannelStore.getChatChannelInfo(id).map { info =>
      if (info.isPrivate) {
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
    chatChannelStore.getChatChannelInfo(id).map { info =>
      if (info.isPrivate) {
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

    chatChannelStore.getDirectChatChannelInfoByUsers(userIds) flatMap {
      _ match {
        case Some(c) =>
          // The channel exists, just return it.
          Success(c)
        case None =>
          // Does not exists, so create it.
          createChannel(None, ChannelType.Direct, true, None, None, userIds.toSet, userId) flatMap { channelId =>
            // Create was successful, now let's just get the channel.
            chatChannelStore.getChatChannelInfo(channelId)
          } recoverWith {
            case DuplicateValueException(ChatChannelStore.Fields.Members, _, _) =>
              // The channel already exists based on the members, this must have been a race condition.
              // So just try to get it again
              chatChannelStore.getDirectChatChannelInfoByUsers(userIds) flatMap {
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
    this.chatChannelStore.getJoinedChannels(userId) map { channels =>
      sender ! GetJoinedChannelsResponse(channels)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  private[this] def createChannel(
    channelId: Option[String],
    ct: ChannelType.Value,
    isPrivate: Boolean,
    name: Option[String],
    topic: Option[String],
    members: Set[DomainUserId],
    createdBy: DomainUserId): Try[String] = {

    this.chatChannelStore.createChatChannel(
      channelId, ct, Instant.now(), isPrivate, name.getOrElse(""), topic.getOrElse(""), Some(members), createdBy)
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
        channelRid <- chatChannelStore.getChatChannelRid(channelId)
        hasPermission <- permissionsStore.hasPermission(userId, channelRid, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }
}
