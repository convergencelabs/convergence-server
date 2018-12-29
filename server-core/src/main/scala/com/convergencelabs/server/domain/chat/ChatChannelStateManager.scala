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
import com.convergencelabs.server.domain.model.SessionKey

import grizzled.slf4j.Logging


object ChatChannelStateManager {
  def create(channelId: String, chatChannelStore: ChatChannelStore, permissionsStore: PermissionsStore): Try[ChatChannelStateManager] = {
    chatChannelStore.getChatChannelInfo(channelId) map { info =>
      val ChatChannelInfo(id, channelType, created, isPrivate, name, topic, members, lastEventNo, lastEventTime) = info
      val state = ChatChannelState(id, channelType, created, isPrivate, name, topic, lastEventTime, lastEventNo, members)
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

  def onRemoveChannel(channelId: String, sk: SessionKey): Try[Unit] = {
    hasPermission(sk, ChatPermissions.RemoveChannel).map { _ =>
      channelStore.removeChatChannel(channelId)
    }
  }

  def onJoinChannel(sk: SessionKey): Try[ChatUserJoinedEvent] = {
    hasPermission(sk, ChatPermissions.JoinChannel).map { _ =>
      val members = state.members
      if (members contains sk.uid) {
        Failure(ChannelAlreadyJoinedException(channelId))
      } else {
        val newMembers = members + sk.uid

        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserJoinedEvent(eventNo, channelId, sk.uid, timestamp)

        for {
          _ <- channelStore.addChatUserJoinedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, sk.uid, None)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }.get
  }

  def onLeaveChannel(sk: SessionKey): Try[ChatUserLeftEvent] = {
    hasPermission(sk, ChatPermissions.LeaveChannel).map { _ =>
      val members = state.members
      if (members contains sk.uid) {
        val newMembers = members - sk.uid
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserLeftEvent(eventNo, channelId, sk.uid, timestamp)

        for {
          _ <- channelStore.addChatUserLeftEvent(event)
          _ <- channelStore.removeChatChannelMember(channelId, sk.uid)
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

  def onAddUserToChannel(channelId: String, sk: SessionKey, username: String): Try[ChatUserAddedEvent] = {
    hasPermission(sk, ChatPermissions.AddUser).map { _ =>
      val members = state.members
      if (members contains username) {
        Failure(ChannelAlreadyJoinedException(channelId))
      } else {
        val newMembers = members + username
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserAddedEvent(eventNo, channelId, sk.uid, timestamp, username)
        for {
          _ <- channelStore.addChatUserAddedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, username, None)
          channel <- channelStore.getChatChannelRid(channelId)
          _ <- permissionsStore.addUserPermissions(DefaultChatPermissions, username, Some(channel))
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }.get
  }

  def onRemoveUserFromChannel(channelId: String, sk: SessionKey, username: String): Try[ChatUserRemovedEvent] = {
    hasPermission(sk, ChatPermissions.RemoveUser).map { _ =>
      val members = state.members
      if (members contains username) {
        val newMembers = members - username
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserRemovedEvent(eventNo, channelId, sk.uid, timestamp, username)

        for {
          _ <- channelStore.addChatUserRemovedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, username, None)
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

  def onSetChatChannelName(channelId: String, sk: SessionKey, name: String): Try[ChatNameChangedEvent] = {
    hasPermission(sk, ChatPermissions.SetName).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatNameChangedEvent(eventNo, channelId, sk.uid, timestamp, name)

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

  def onSetChatChannelTopic(channelId: String, sk: SessionKey, topic: String): Try[ChatTopicChangedEvent] = {
    hasPermission(sk, ChatPermissions.SetTopic).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatTopicChangedEvent(eventNo, channelId, sk.uid, timestamp, topic)

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

  def onMarkEventsSeen(channelId: String, sk: SessionKey, eventNumber: Long): Try[Unit] = {
    if (state.members contains sk.uid) {
      channelStore.markSeen(channelId, sk.uid, eventNumber)
    } else {
      Failure(UnauthorizedException("Not authorized"))
    }
  }

  def onGetHistory(channelId: String, username: String, limit: Option[Int], offset: Option[Int],
                   forward: Option[Boolean], eventFilter: Option[List[String]]): Try[List[ChatChannelEvent]] = {
    channelStore.getChatChannelEvents(channelId, eventFilter, offset, limit, forward)
  }

  def onPublishMessage(channelId: String, sk: SessionKey, message: String): Try[ChatMessageEvent] = {
    if (state.members contains sk.uid) {
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatMessageEvent(eventNo, channelId, sk.uid, timestamp, message)

      channelStore.addChatMessageEvent(event).map { _ =>
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp)
        this.state = newState
        event
      }
    } else {
      Failure(UnauthorizedException("Not authorized"))
    }
  }

  def onAddPermissions(channelId: String, sk: SessionKey, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(sk, ChatPermissions.Manage).map { _ =>
      for {
        channel <- channelStore.getChatChannelRid(channelId)
      } yield {
        world map { permissionsStore.addWorldPermissions(_, Some(channel)).get }

        user map {
          _ foreach {
            case UserPermissions(username, permissions) =>
              permissionsStore.addUserPermissions(permissions, username, Some(channel)).get
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

  def onRemovePermissions(channelId: String, sk: SessionKey, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(sk, ChatPermissions.Manage).map { _ =>
      for {
        channel <- channelStore.getChatChannelRid(channelId)
      } yield {
        world map { permissionsStore.removeWorldPermissions(_, Some(channel)).get }

        user map {
          _ foreach {
            case UserPermissions(username, permissions) =>
              permissionsStore.removeUserPermissions(permissions, username, Some(channel)).get
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

  def onSetPermissions(channelId: String, sk: SessionKey, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(sk, ChatPermissions.Manage).map { _ =>
      for {
        channel <- channelStore.getChatChannelRid(channelId)
      } yield {
        world map { permissionsStore.setWorldPermissions(_, Some(channel)).get }

        user map {
          _ foreach {
            case UserPermissions(username, permissions) =>
              permissionsStore.setUserPermissions(permissions, username, Some(channel)).get
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

  def onGetClientPermissions(channelId: String, sk: SessionKey): Try[Set[String]] = {
    channelStore.getChatChannelRid(channelId) flatMap { permissionsStore.getAggregateUserPermissions(sk.uid, _, AllChatChannelPermissions) }
  }

  def onGetWorldPermissions(channelId: String, sk: SessionKey): Try[Set[String]] = {
    val worldPermissions = channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getWorldPermissions(Some(channel)) }
    worldPermissions map { permissions => permissions.map { _.permission } }
  }

  def onGetAllUserPermissions(channelId: String, sk: SessionKey): Try[Set[UserPermission]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getAllUserPermissions(Some(channel)) }
  }

  def onGetAllGroupPermissions(channelId: String, sk: SessionKey): Try[Set[GroupPermission]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getAllGroupPermissions(Some(channel)) }
  }

  def onGetUserPermissions(channelId: String, username: String, sk: SessionKey): Try[Set[String]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getUserPermissions(username, Some(channel)) }
  }

  def onGetGroupPermissions(channelId: String, groupId: String, sk: SessionKey): Try[Set[String]] = {
    channelStore.getChatChannelRid(channelId) flatMap { channel => permissionsStore.getGroupPermissions(groupId, Some(channel)) }
  }

  def removeAllMembers(): Unit = {
    this.state().members.foreach(username => {
      this.channelStore.removeChatChannelMember(channelId, username) recover {
        case cause: Throwable =>
          error("Error removing chat channel member", cause)
      }
    })
    state = state.copy(members = Set())
  }

  private[this] def hasPermission(sk: SessionKey, permission: String): Try[Unit] = {
    if (sk.admin) {
      Success(())
    } else {
      for {
        channelRid <- channelStore.getChatChannelRid(channelId)
        hasPermission <- permissionsStore.hasPermission(sk.uid, channelRid, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }
}
