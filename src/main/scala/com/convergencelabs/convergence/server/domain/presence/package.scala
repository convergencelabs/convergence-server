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

package com.convergencelabs.convergence.server.domain.presence

import org.json4s.JsonAST.JValue
import com.convergencelabs.convergence.server.domain.DomainUserId
import akka.actor.ActorRef
import com.convergencelabs.convergence.server.actor.CborSerializable

case class UserPresence(userId: DomainUserId, available: Boolean, state: Map[String, JValue], clients: Set[ActorRef])

case class GetPresenceRequest(userIds: List[DomainUserId]) extends CborSerializable
case class GetPresenceResponse(presence: List[UserPresence]) extends CborSerializable


case class UserConnected(userId: DomainUserId, client: ActorRef) extends CborSerializable

case class UserPresenceSetState(userId: DomainUserId, state: Map[String, JValue]) extends CborSerializable
case class UserPresenceRemoveState(userId: DomainUserId, keys: List[String]) extends CborSerializable
case class UserPresenceClearState(userId: DomainUserId) extends CborSerializable
case class UserPresenceAvailability(userId: DomainUserId, available: Boolean) extends CborSerializable

case class SubscribePresence(userIds: List[DomainUserId], client: ActorRef) extends CborSerializable
case class UnsubscribePresence(userIds: List[DomainUserId], client: ActorRef) extends CborSerializable