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

case class PresenceRequest(userIds: List[DomainUserId])
case class UserPresence(userId: DomainUserId, available: Boolean, state: Map[String, JValue], clients: Set[ActorRef])

case class UserConnected(userId: DomainUserId, client: ActorRef)

case class UserPresenceSetState(userId: DomainUserId, state: Map[String, JValue])
case class UserPresenceRemoveState(userId: DomainUserId, keys: List[String])
case class UserPresenceClearState(userId: DomainUserId)
case class UserPresenceAvailability(userId: DomainUserId, available: Boolean)

case class SubscribePresence(userIds: List[DomainUserId], client: ActorRef)
case class UnsubscribePresence(userIds: List[DomainUserId], client: ActorRef)