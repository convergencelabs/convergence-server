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

package com.convergencelabs.convergence.server.datastore.domain.chat

import java.time.Instant

import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}


@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
@JsonSubTypes(Array(
  new JsonSubTypes.Type(value = classOf[ChatCreatedEvent], name = "created"),
  new JsonSubTypes.Type(value = classOf[ChatMessageEvent], name = "message"),
  new JsonSubTypes.Type(value = classOf[ChatUserJoinedEvent], name = "joined"),
  new JsonSubTypes.Type(value = classOf[ChatUserLeftEvent], name = "left"),
  new JsonSubTypes.Type(value = classOf[ChatUserAddedEvent], name = "user_added"),
  new JsonSubTypes.Type(value = classOf[ChatUserRemovedEvent], name = "user_removed"),
  new JsonSubTypes.Type(value = classOf[ChatNameChangedEvent], name = "name_changed"),
  new JsonSubTypes.Type(value = classOf[ChatTopicChangedEvent], name = "topic_changed"),
))
sealed trait ChatEvent {
  val eventNumber: Long
  val id: String
  val user: DomainUserId
  val timestamp: Instant
}

final case class ChatCreatedEvent(eventNumber: Long,
                                  id: String,
                                  user: DomainUserId,
                                  timestamp: Instant,
                                  name: String,
                                  topic: String,
                                  members: Set[DomainUserId]) extends ChatEvent

sealed trait ExistingChatEvent extends ChatEvent

final case class ChatMessageEvent(eventNumber: Long,
                                  id: String,
                                  user: DomainUserId,
                                  timestamp: Instant,
                                  message: String) extends ExistingChatEvent

final case class ChatUserJoinedEvent(eventNumber: Long,
                                     id: String,
                                     user: DomainUserId,
                                     timestamp: Instant) extends ExistingChatEvent

final case class ChatUserLeftEvent(eventNumber: Long,
                                   id: String,
                                   user: DomainUserId,
                                   timestamp: Instant) extends ExistingChatEvent

final case class ChatUserAddedEvent(eventNumber: Long,
                                    id: String,
                                    user: DomainUserId,
                                    timestamp: Instant,
                                    userAdded: DomainUserId) extends ExistingChatEvent

final case class ChatUserRemovedEvent(eventNumber: Long,
                                      id: String,
                                      user: DomainUserId,
                                      timestamp: Instant,
                                      userRemoved: DomainUserId) extends ExistingChatEvent

final case class ChatNameChangedEvent(eventNumber: Long,
                                      id: String,
                                      user: DomainUserId,
                                      timestamp: Instant,
                                      name: String) extends ExistingChatEvent

final case class ChatTopicChangedEvent(eventNumber: Long,
                                       id: String,
                                       user: DomainUserId,
                                       timestamp: Instant,
                                       topic: String) extends ExistingChatEvent




