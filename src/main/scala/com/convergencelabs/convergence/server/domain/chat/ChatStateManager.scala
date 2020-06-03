/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.chat

import java.time.Instant

import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.datastore.EntityNotFoundException
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.domain.chat.ChatStateManager.{AllChatChannelPermissions, ChatPermissions, DefaultChatPermissions}
import com.convergencelabs.convergence.server.domain.{DomainUserId, UnauthorizedException}
import grizzled.slf4j.Logging

import scala.util.{Failure, Success, Try}

/**
 * The [[ChatStateManager]] manages the persistent state of Chat's in
 * response to the various events over te lifecycle of the Chat. Its
 * primary function is to translate Chat events into appropriate calls
 * to the [[ChatStore]].
 */
private[chat] object ChatStateManager extends Logging {
  def create(chatId: String, chatChannelStore: ChatStore, permissionsStore: PermissionsStore): Try[ChatStateManager] = {
    chatChannelStore.getChatInfo(chatId) map { info =>
      val ChatInfo(id, channelType, created, isPrivate, name, topic, lastEventNo, lastEventTime, members) = info
      val memberMap = members.map(member => (member.userId, member)).toMap
      val state = ChatState(id, channelType, created, isPrivate, name, topic, lastEventTime, lastEventNo, memberMap)
      new ChatStateManager(chatId, state, chatChannelStore, permissionsStore)
    } recoverWith {
      case cause: EntityNotFoundException =>
        logger.error(cause)
        Failure(ChatNotFoundException(chatId))
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

  val AllChatPermissions: Set[String] = AllChatChannelPermissions + ChatPermissions.CreateChannel

  val DefaultChatPermissions = Set(ChatPermissions.JoinChannel, ChatPermissions.LeaveChannel)
}

private[chat] class ChatStateManager(private[this] val chatId: String,
                                     private[this] var state: ChatState,
                                     private[this] val chatStore: ChatStore,
                                     private[this] val permissionsStore: PermissionsStore) extends Logging {

  def state(): ChatState = {
    state
  }

  def onRemoveChannel(chatId: String, userId: DomainUserId): Try[Unit] = {
    hasPermission(userId, ChatPermissions.RemoveChannel).flatMap { _ =>
      chatStore.removeChat(chatId)
    }
  }

  def onJoinChannel(userId: DomainUserId): Try[ChatUserJoinedEvent] = {
    hasPermission(userId, ChatPermissions.JoinChannel).flatMap { _ =>
      val members = state.members
      if (members contains userId) {
        Failure(ChatActor.ChatAlreadyJoinedException(chatId))
      } else {
        val newMembers = members + (userId -> ChatMember(chatId, userId, 0))

        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserJoinedEvent(eventNo, chatId, userId, timestamp)

        for {
          _ <- chatStore.addChatUserJoinedEvent(event)
          _ <- chatStore.addChatMember(chatId, userId, None)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }
  }

  def onLeaveChannel(userId: DomainUserId): Try[ChatUserLeftEvent] = {
    hasPermission(userId, ChatPermissions.LeaveChannel).flatMap { _ =>
      val members = state.members
      if (members contains userId) {
        val newMembers = members - userId
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserLeftEvent(eventNo, chatId, userId, timestamp)

        for {
          _ <- chatStore.addChatUserLeftEvent(event)
          _ <- chatStore.removeChatMember(chatId, userId)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      } else {
        Failure(ChatActor.ChatNotJoinedException(chatId))
      }
    }
  }

  def onAddUserToChannel(chatId: String, userId: DomainUserId, userToAdd: DomainUserId): Try[ChatUserAddedEvent] = {
    hasPermission(userId, ChatPermissions.AddUser).flatMap { _ =>
      val members = state.members
      if (members contains userToAdd) {
        Failure(ChatActor.ChatAlreadyJoinedException(chatId))
      } else {
        val newMembers = members + (userToAdd -> ChatMember(chatId, userToAdd, 0))
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserAddedEvent(eventNo, chatId, userId, timestamp, userToAdd)
        for {
          _ <- chatStore.addChatUserAddedEvent(event)
          _ <- chatStore.addChatMember(chatId, userToAdd, None)
          channel <- chatStore.getChatRid(chatId)
          _ <- permissionsStore.addUserPermissions(DefaultChatPermissions, userToAdd, Some(channel))
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      }
    }
  }

  def onRemoveUserFromChannel(chatId: String, userId: DomainUserId, userToRemove: DomainUserId): Try[ChatUserRemovedEvent] = {
    hasPermission(userId, ChatPermissions.RemoveUser).flatMap { _ =>
      val members = state.members
      if (members contains userToRemove) {
        val newMembers = members - userToRemove
        val eventNo = state.lastEventNumber + 1
        val timestamp = Instant.now()

        val event = ChatUserRemovedEvent(eventNo, chatId, userId, timestamp, userToRemove)

        for {
          _ <- chatStore.addChatUserRemovedEvent(event)
          _ <- chatStore.removeChatMember(chatId, userToRemove)
        } yield {
          val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, members = newMembers)
          this.state = newState
          event
        }
      } else {
        Failure(ChatActor.ChatNotJoinedException(chatId))
      }
    }
  }

  def onSetChatChannelName(chatId: String, userId: DomainUserId, name: String): Try[ChatNameChangedEvent] = {
    hasPermission(userId, ChatPermissions.SetName).flatMap { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatNameChangedEvent(eventNo, chatId, userId, timestamp, name)

      for {
        _ <- chatStore.addChatNameChangedEvent(event)
        _ <- chatStore.updateChat(chatId, Some(name), None)
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, name = name)
        this.state = newState
        event
      }
    }
  }

  def onSetChatChannelTopic(chatId: String, userId: DomainUserId, topic: String): Try[ChatTopicChangedEvent] = {
    hasPermission(userId, ChatPermissions.SetTopic).flatMap { _ =>
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatTopicChangedEvent(eventNo, chatId, userId, timestamp, topic)

      for {
        _ <- chatStore.addChatTopicChangedEvent(event)
        _ <- chatStore.updateChat(chatId, None, Some(topic))
      } yield {
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp, topic = topic)
        this.state = newState
        event
      }
    }
  }

  def onMarkEventsSeen(chatId: String, userId: DomainUserId, eventNumber: Long): Try[Unit] = {
    if (state.members contains userId) {
      val newMembers = state.members + (userId -> ChatMember(chatId, userId, eventNumber))
      val newState = state.copy(members = newMembers)
      this.state = newState
      chatStore.markSeen(chatId, userId, eventNumber)
    } else {
      Failure(UnauthorizedException("Not authorized"))
    }
  }

  def onGetHistory(chatId: String,
                   offset: Option[Long],
                   limit: Option[Long],
                   startEvent: Option[Long],
                   forward: Option[Boolean],
                   eventTypes: Option[Set[String]],
                   messageFilter: Option[String]): Try[PagedData[ChatEvent]] = {
    chatStore.getChatEvents(chatId, eventTypes, startEvent, offset, limit, forward, messageFilter)
  }

  def onPublishMessage(chatId: String, userId: DomainUserId, message: String): Try[ChatMessageEvent] = {
    if (state.members contains userId) {
      val eventNo = state.lastEventNumber + 1
      val timestamp = Instant.now()

      val event = ChatMessageEvent(eventNo, chatId, userId, timestamp, message)

      chatStore.addChatMessageEvent(event).map { _ =>
        val newState = state.copy(lastEventNumber = eventNo, lastEventTime = timestamp)
        this.state = newState
        event
      }
    } else {
      Failure(ChatActor.ChatNotJoinedException(this.chatId))
    }
  }

  def onAddPermissions(chatId: String, userId: DomainUserId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(userId, ChatPermissions.Manage).flatMap { _ =>
      for {
        channel <- chatStore.getChatRid(chatId)
      } yield {
        world foreach {
          permissionsStore.addWorldPermissions(_, Some(channel)).get
        }

        user foreach {
          _ foreach {
            case UserPermissions(userId, permissions) =>
              permissionsStore.addUserPermissions(permissions, userId, Some(channel)).get
          }
        }

        group foreach {
          _ foreach {
            case GroupPermissions(group, permissions) =>
              permissionsStore.addGroupPermissions(permissions, group, Some(channel)).get
          }
        }
      }
    }
  }

  def onRemovePermissions(chatId: String, userId: DomainUserId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(userId, ChatPermissions.Manage).flatMap { _ =>
      for {
        channel <- chatStore.getChatRid(chatId)
      } yield {
        world foreach {
          permissionsStore.removeWorldPermissions(_, Some(channel)).get
        }

        user foreach {
          _ foreach {
            case UserPermissions(userId, permissions) =>
              permissionsStore.removeUserPermissions(permissions, userId, Some(channel)).get
          }
        }

        group foreach {
          _ foreach {
            case GroupPermissions(group, permissions) =>
              permissionsStore.removeGroupPermissions(permissions, group, Some(channel)).get
          }
        }
      }
    }
  }

  def onSetPermissions(chatId: String, userId: DomainUserId, world: Option[Set[String]], user: Option[Set[UserPermissions]], group: Option[Set[GroupPermissions]]): Try[Unit] = {
    hasPermission(userId, ChatPermissions.Manage).flatMap { _ =>
      for {
        channel <- chatStore.getChatRid(chatId)
      } yield {
        world foreach {
          permissionsStore.setWorldPermissions(_, Some(channel)).get
        }

        user foreach {
          _ foreach {
            case UserPermissions(userId, permissions) =>
              permissionsStore.setUserPermissions(permissions, userId, Some(channel)).get
          }
        }

        group foreach {
          _ foreach {
            case GroupPermissions(group, permissions) =>
              permissionsStore.setGroupPermissions(permissions, group, Some(channel)).get
          }
        }
      }
    }
  }

  def onGetClientPermissions(chatId: String, userId: DomainUserId): Try[Set[String]] = {
    chatStore.getChatRid(chatId) flatMap {
      permissionsStore.getAggregateUserPermissions(userId, _, AllChatChannelPermissions)
    }
  }

  def onGetWorldPermissions(chatId: String): Try[Set[String]] = {
    val worldPermissions = chatStore.getChatRid(chatId) flatMap { channel => permissionsStore.getWorldPermissions(Some(channel)) }
    worldPermissions map { permissions =>
      permissions.map {
        _.permission
      }
    }
  }

  def onGetAllUserPermissions(chatId: String): Try[Set[UserPermission]] = {
    chatStore.getChatRid(chatId) flatMap { channel => permissionsStore.getAllUserPermissions(Some(channel)) }
  }

  def onGetAllGroupPermissions(chatId: String): Try[Set[GroupPermission]] = {
    chatStore.getChatRid(chatId) flatMap { channel => permissionsStore.getAllGroupPermissions(Some(channel)) }
  }

  def onGetUserPermissions(chatId: String, userId: DomainUserId): Try[Set[String]] = {
    chatStore.getChatRid(chatId) flatMap { channel => permissionsStore.getUserPermissions(userId, Some(channel)) }
  }

  def onGetGroupPermissions(chatId: String, groupId: String): Try[Set[String]] = {
    chatStore.getChatRid(chatId) flatMap { channel => permissionsStore.getGroupPermissions(groupId, Some(channel)) }
  }

  def removeAllMembers(): Unit = {
    this.state().members.values.foreach(member => {
      this.chatStore.removeChatMember(chatId, member.userId) recover {
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
        channelRid <- chatStore.getChatRid(chatId)
        hasPermission <- permissionsStore.hasPermission(userId, channelRid, permission)
      } yield {
        if (!hasPermission) {
          Failure(UnauthorizedException("Not authorized"))
        }
      }
    }
  }
}
