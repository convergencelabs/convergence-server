/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.presence

import org.json4s.JsonAST.JValue

import com.convergencelabs.server.domain.DomainUserId

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