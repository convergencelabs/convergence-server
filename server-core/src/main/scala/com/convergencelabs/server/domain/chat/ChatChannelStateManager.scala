package com.convergencelabs.server.domain.chat

import java.time.Instant

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.datastore.domain.ChatChannelStore
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import com.convergencelabs.server.datastore.domain.GroupPermission
import com.convergencelabs.server.datastore.domain.PermissionsStore
import com.convergencelabs.server.datastore.domain.UserPermission
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.domain.chat.ChatChannelStateManager.AllChatChannelPermissions
import com.convergencelabs.server.domain.chat.ChatChannelStateManager.ChatPermissions
import com.convergencelabs.server.domain.chat.ChatChannelStateManager.DefaultChatPermissions

import grizzled.slf4j.Logging
import com.convergencelabs.server.datastore.domain.ChatChannelMember
import com.convergencelabs.server.domain.DomainUserId

object ChatChannelStateManager {
  def create(channelId: String, chatChannelStore: ChatChannelStore, permissionsStore: PermissionsStore): Try[ChatChannelStateManager] = {
    chatChannelStore.getChatChannelInfo(channelId) map { info =>
      val ChatChannelInfo(id, channelType, created, isPrivate, name, topic, lastEventNo, lastEventTime, members) = info
      val memberMap = members.map(member => (member.userId, member)).toMap
      val state = ChatChannelState(id, channelType, created, isPrivate, name, topic, lastEventTime, lastEventNo, memberMap)
      new ChatChannelStateManager(channelId, state, chatChannelStore, permissionsStore)
    } recoverWith {
      case cause: EntityNotFoundException =>
        Failure(ChannelNotFoundException(channelId))
    }
  }

  // TODO: Move these somewhere else

  object ChatPermissions {
    val CreateChannel = "create_chat_channel"
    val RemoveChannel = "remove_chat_channel"
    val JoinChannel = "join_chat_channel"
    val LeaveChannel = "leave_chat_channel"
    val AddUser = "add_chat_user"
    val RemoveUser = "remove_chat_user"
    val SetName = "set_chat_name"
    val SetTopic = "set_topic"
    val Manage = "manage_chat_permissions"
  }

  val AllChatChannelPermissions = Set(ChatPermissions.RemoveChannel, ChatPermissions.JoinChannel,
    ChatPermissions.LeaveChannel, ChatPermissions.AddUser, ChatPermissions.RemoveUser,
    ChatPermissions.SetName, ChatPermissions.SetTopic, ChatPermissions.Manage)

  val AllChatPermissions = AllChatChannelPermissions + ChatPermissions.CreateChannel

  val DefaultChatPermissions = Set(ChatPermissions.JoinChannel, ChatPermissions.LeaveChannel)
}

class ChatChannelStateManager(
  private[this] val channelId: String,
  private[this] var state: ChatChannelState,
  private[this] val channelStore: ChatChannelStore,
  private[this] val permissionsStore: PermissionsStore) extends Logging {

  import ChatChannelMessages._

  def state(): ChatChannelState = {
    state
  }

  def onRemoveChannel(channelId: String, userId: DomainUserId): Try[Unit] = {
    hasPermission(userId, ChatPermissions.RemoveChannel).map { _ =>
      channelStore.removeChatChannel(channelId)
    }
  }

  def onJoinChannel(userId: DomainUserId): Try[ChatUserJoinedEvent] = {
    hasPermission(userId, ChatPermissions.JoinChannel).map { _ =>
      val members = state.members
      if (members contains userId) {
        Failure(ChannelAlreadyJoinedException(channelId))
      } else {
        val newMembers = members + (userId -> ChatChannelMember(channelId, userId, 0))

        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserJoinedEvent(eventNo, channelId, userId, timestamp)

        for {
          _ <- channelStore.addChatUserJoinedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, userId, None)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }.get
  }

  def onLeaveChannel(userId: DomainUserId): Try[ChatUserLeftEvent] = {
    hasPermission(userId, ChatPermissions.LeaveChannel).map { _ =>
      val members = state.members
      if (members contains userId) {
        val newMembers = members - userId
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserLeftEvent(eventNo, channelId, userId, timestamp)

        for {
          _ <- channelStore.addChatUserLeftEvent(event)
          _ <- channelStore.removeChatChannelMember(channelId, userId)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      } else {
        Failure(ChannelNotJoinedException(channelId))
      }
    }.get
  }

  def onAddUserToChannel(channelId: String, userId: DomainUserId, userToAdd: DomainUserId): Try[ChatUserAddedEvent] = {
    hasPermission(userId, ChatPermissions.AddUser).map { _ =>
      val members = state.members
      if (members contains userToAdd) {
        Failure(ChannelAlreadyJoinedException(channelId))
      } else {
        val newMembers = members + (userToAdd -> ChatChannelMember(channelId, userToAdd, 0))
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserAddedEvent(eventNo, channelId, userId, timestamp, userToAdd)
        for {
          _ <- channelStore.addChatUserAddedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, userToAdd, None)
          channel <- channelStore.getChatChannelRid(channelId)
          _ <- permissionsStore.addUserPermissions(DefaultChatPermissions, userToAdd, Some(channel))
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }.get
  }

  def onRemoveUserFromChannel(channelId: String, userId: DomainUserId, userToRemove: DomainUserId): Try[ChatUserRemovedEvent] = {
    hasPermission(userId, ChatPermissions.RemoveUser).map { _ =>
      val members = state.members
      if (members contains userToRemove) {
        val newMembers = members - userToRemove
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserRemovedEvent(eventNo, channelId, userId, timestamp, userToRemove)

        for {
          _ <- channelStore.addChatUserRemovedEvent(event)
          _ <- channelStore.removeChatChannelMember(channelId, userToRemove)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState

          event
        }

      } else {
        Failure(ChannelNotJoinedException(channelId))
      }
    }.get
  }

  def onSetChatChannelName(channelId: String, userId: DomainUserId, name: String): Try[ChatNameChangedEvent] = {
    hasPermission(userId, ChatPermissions.SetName).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatNameChangedEvent(eventNo, channelId, userId, timestamp, name)

      for {
        _ <- channelStore.addChatNameChangedEvent(event)
        _ <- channelStore.updateChatChannel(channelId, Some(name), None)
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, name = name)
        this.state = newState
        event
      }
    }.get
  }

  def onSetChatChannelTopic(channelId: String, userId: DomainUserId, topic: String): Try[ChatTopicChangedEvent] = {
    hasPermission(userId, ChatPermissions.SetTopic).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatTopicChangedEvent(eventNo, channelId, userId, timestamp, topic)

      for {
        _ <- channelStore.addChatTopicChangedEvent(event)
        _ <- channelStore.updateChatChannel(channelId, None, Some(topic))
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, topic = topic)
        this.state = newState
        event
      }
    }.get
  }

  def onMarkEventsSeen(channelId: String, userId: DomainUserId, eventNumber: Long): Try[Unit] = {
    if (state.members contains userId) {
      val newMembers = state.members + (userId -> ChatChannelMember(channelId, userId, eventNumber))
      val newState = state.copy(members = newMembers)
      this.state = newState
      channelStore.markSeen(channelId, userId, eventNumber)
    } else {
      Failure(UnauthorizedException("Not authorized"))
    }
  }

  def onGetHistory(channelId: String, userId: DomainUserId, limit: Option[Int], startEvent: Option[Long],
    forward: Option[Boolean], eventFilter: Option[List[String]]): Try[List[ChatChannelEvent]] = {
    channelStore.getChatChannelEvents(channelId, eventFilter, startEvent, limit, forward)
  }

  def onPublishMessage(channelId: String, userId: DomainUserId, message: String): Try[ChatMessageEvent] = {
    if (state.members contains userId) {
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatMessageEvent(eventNo, channelId, userId, timestamp, message)

      channelStore.addChatMessageEvent(event).map { _ =>
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp)
        this.state = newState
        event
      }
    } else {
      Failure(UnauthorizedException("Not authorized"))
    }
  }

  def onAddPermissions(channelId: String, userId: DomainUserId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(userId, ChatPermissions.Manage).map { _ =>
      for {
        channel <- channelStore.getChatChannelRid(channelId)
      } yield {
        world map { permissionsStore.addWorldPermissions(_, Some(channel)).get }

        user map {
          _ foreach {
            case UserPermissions(userId, permissions) =>
              permissionsStore.addUserPermissions(permissions, userId, Some(channel)).get
          }
        }

        group map {
          _ foreach {
            case GroupPermissions(group, permissions) =>
              permissionsStore.addGroupPermissions(permissions, group, Some(channel)).get
          }
        }
      }
    }
  }

  def onRemovePermissions(channelId: String, userId: DomainUserId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(userId, ChatPermissions.Manage).map { _ =>
      for {
        channel <- channelStore.getChatChannelRid(channelId)
      } yield {
        world map { permissionsStore.removeWorldPermissions(_, Some(channel)).get }

        user map {
          _ foreach {
            case UserPermissions(userId, permissions) =>
              permissionsStore.removeUserPermissions(permissions, userId, Some(channel)).get
          }
        }

        group map {
          _ foreach {
            case GroupPermissions(group, permissions) =>
              permissionsStore.removeGroupPermissions(permissions, group, Some(channel)).get
          }
        }
      }
    }
  }

  def onSetPermissions(channelId: String, userId: DomainUserId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(userId, ChatPermissions.Manage).map { _ =>
      for {
        channel <- channelStore.getChatChannelRid(channelId)
      } yield {
        world map { permissionsStore.setWorldPermissions(_, Some(channel)).get }

        user map {
          _ foreach {
            case UserPermissions(userId, permissions) =>
              permissionsStore.setUserPermissions(permissions, userId, Some(channel)).get
          }
        }

        group map {
          _ foreach {
            case GroupPermissions(group, permissions) =>
              permissionsStore.setGroupPermissions(permissions, group, Some(channel)).get
          }
        }
      }
    }
  }

  def onGetClientPermissions(channelId: String, userId: DomainUserId): Try[Set[String]] = {
    channelStore.getChatChannelRid(channelId) flatMap { permissionsStore.getAggregateUserPermissions(userId, _, AllChatChannelPermissions) }
  }

  def onGetWorldPermissions(channelId: String): Try[Set[String]] = {
    val worldPermissions = channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getWorldPermissions(Some(channel)) }
    worldPermissions map { permissions => permissions.map { _.permission } }
  }

  def onGetAllUserPermissions(channelId: String): Try[Set[UserPermission]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getAllUserPermissions(Some(channel)) }
  }

  def onGetAllGroupPermissions(channelId: String): Try[Set[GroupPermission]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getAllGroupPermissions(Some(channel)) }
  }

  def onGetUserPermissions(channelId: String, userId: DomainUserId): Try[Set[String]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getUserPermissions(userId, Some(channel)) }
  }

  def onGetGroupPermissions(channelId: String, groupId: String): Try[Set[String]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getGroupPermissions(groupId, Some(channel)) }
  }

  def removeAllMembers(): Unit = {
    this.state().members.values.foreach(member => {
      this.channelStore.removeChatChannelMember(channelId, member.userId) recover {
        case cause: Throwable =>
          error("Error removing chat channel member", cause)
      }
    })
    state = state.copy(members = Map())
  }

  private[this] def hasPermission(userId: DomainUserId, permission: String): Try[Unit] = {
    if (userId.isConvergence) {
      Success(())
    } else {
      for {
        channelRid <- channelStore.getChatChannelRid(channelId)
        hasPermission <- permissionsStore.hasPermission(userId, channelRid, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }
}
