package com.convergencelabs.server.domain.chat

import java.time.Instant

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.datastore.domain.ChatChannelStore
import com.convergencelabs.server.datastore.domain.ChatMessageEvent
import com.convergencelabs.server.datastore.domain.ChatNameChangedEvent
import com.convergencelabs.server.datastore.domain.ChatTopicChangedEvent
import com.convergencelabs.server.datastore.domain.ChatUserAddedEvent
import com.convergencelabs.server.datastore.domain.ChatUserJoinedEvent
import com.convergencelabs.server.datastore.domain.ChatUserLeftEvent
import com.convergencelabs.server.datastore.domain.ChatUserRemovedEvent
import com.convergencelabs.server.domain.chat.ChatChannelMessages.ChannelNotFoundException
import com.convergencelabs.server.frontend.realtime.ChatChannelRemovedMessage
import com.convergencelabs.server.datastore.domain.ChatChannelInfo
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.datastore.domain.ChatChannelEvent
import com.convergencelabs.server.datastore.domain.PermissionsStore
import com.convergencelabs.server.domain.chat.ChatChannelStateManager.ChatPermissions
import com.convergencelabs.server.domain.UnauthorizedException

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

  object ChatPermissions {
    val RemoveChannel = "remove_chat_channel"
    val JoinChannel = "join_chat_channel"
    val LeaveChannel = "leave_chat_channel"
    val AddUser = "add_chat_user"
    val RemoveUser = "remove_chat_user"
    val SetName = "set_chat_name"
    val SetTopic = "set_topic"
    val SendMessage = "send_chat_message"
  }
}

class ChatChannelStateManager(
    private[this] val channelId: String,
    private[this] var state: ChatChannelState,
    private[this] val channelStore: ChatChannelStore,
    private[this] val permissionsStore: PermissionsStore) {

  import ChatChannelMessages._

  def state(): ChatChannelState = {
    state
  }

  def onRemoveChannel(channelId: String, username: String): Try[Unit] = {
    hasPermission(username, ChatPermissions.RemoveChannel).map { _ =>
      channelStore.removeChatChannel(channelId)
    }
  }

  def onJoinChannel(username: String): Try[ChatUserJoinedEvent] = {
    hasPermission(username, ChatPermissions.JoinChannel).map { _ =>
      val members = state.members
      if (members contains username) {
        Failure(ChannelAlreadyJoinedException(channelId))
      } else {
        val newMembers = members + username

        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserJoinedEvent(eventNo, channelId, username, timestamp)

        for {
          _ <- channelStore.addChatUserJoinedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, username, None)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }.get
  }

  def onLeaveChannel(username: String): Try[ChatUserLeftEvent] = {
    hasPermission(username, ChatPermissions.LeaveChannel).map { _ =>
      val members = state.members
      if (members contains username) {
        val newMembers = members - username
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserLeftEvent(eventNo, channelId, username, timestamp)

        for {
          _ <- channelStore.addChatUserLeftEvent(event)
          _ <- channelStore.removeChatChannelMember(channelId, username)
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

  def onAddUserToChannel(channelId: String, username: String, addedBy: String): Try[ChatUserAddedEvent] = {
    hasPermission(addedBy, ChatPermissions.AddUser).map { _ =>
      val members = state.members
      if (members contains username) {
        Failure(ChannelAlreadyJoinedException(channelId))
      } else {
        val newMembers = members + username
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserAddedEvent(eventNo, channelId, addedBy, timestamp, username)
        for {
          _ <- channelStore.addChatUserAddedEvent(event)
          _ <- channelStore.addChatChannelMember(channelId, username, None)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }.get
  }

  def onRemoveUserFromChannel(channelId: String, username: String, removedBy: String): Try[ChatUserRemovedEvent] = {
    hasPermission(removedBy, ChatPermissions.RemoveUser).map { _ =>
      val members = state.members
      if (members contains username) {
        val newMembers = members - username
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserRemovedEvent(eventNo, channelId, removedBy, timestamp, username)

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

  def onSetChatChannelName(channelId: String, name: String, setBy: String): Try[ChatNameChangedEvent] = {
    hasPermission(setBy, ChatPermissions.SetName).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatNameChangedEvent(eventNo, channelId, setBy, timestamp, name)

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

  def onSetChatChannelTopic(channelId: String, topic: String, setBy: String): Try[ChatTopicChangedEvent] = {
    hasPermission(setBy, ChatPermissions.SetTopic).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatTopicChangedEvent(eventNo, channelId, setBy, timestamp, topic)

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

  def onMarkEventsSeen(channelId: String, eventNumber: Long, username: String): Try[Unit] = {
    channelStore.markSeen(channelId, username, eventNumber)
  }

  def onGetHistory(channelId: String, username: String, limit: Option[Int], offset: Option[Int],
                   forward: Option[Boolean], eventFilter: Option[List[String]]): Try[List[ChatChannelEvent]] = {
    channelStore.getChatChannelEvents(channelId, eventFilter, offset, limit, forward)
  }

  def onPublishMessage(channelId: String, message: String, sk: SessionKey): Try[ChatMessageEvent] = {
    hasPermission(sk.uid, ChatPermissions.SendMessage).map { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatMessageEvent(eventNo, channelId, sk.uid, timestamp, message)

      channelStore.addChatMessageEvent(event).map { _ =>
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp)
        this.state = newState
        event
      }
    }.get
  }

  def removeAllMembers(): Unit = {
    this.state().members.foreach(username => {
      this.channelStore.removeChatChannelMember(channelId, username)
    })
    state = state.copy(members = Set())
  }

  private[this] def hasPermission(username: String, permission: String): Try[Unit] = {
    for {
      channelRid <- channelStore.getChatChannelRid(channelId)
      hasPermission <- permissionsStore.hasPermission(username, channelRid, permission)
    } yield {
      if (!hasPermission) {
        Failure(UnauthorizedException("Not authorized"))
      }
    }
  }
}
