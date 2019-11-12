/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.activity

import scala.util.Success
import scala.util.Try

import org.json4s.JsonAST.JValue

import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.domain.DomainId

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.Terminated

object ActivityActor {
  def props(): Props = {
    Props(new ActivityActor())
  }
}

class ActivityActor()
  extends ShardedActor(classOf[IncomingActivityMessage])
  with ActorLogging {

  /**
   * Will be initialized on the first message inside the initialize method.
   */
  private[this] var activityId: String = _
  private[this] var domain: DomainId = _

  private[this] var joinedClients = Map[ActorRef, String]()
  private[this] var joinedSessions = Map[String, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  override protected def setIdentityData(message: IncomingActivityMessage): Try[String] = {
    this.activityId = message.activityId
    this.domain = message.domain
    Success(s"${domain.namespace}/${domain.domainId}/${this.activityId}")
  }

  override def initialize(message: IncomingActivityMessage): Try[ShardedActorStatUpPlan] = {
    Success(StartUpRequired)
  }

  override def receiveInitialized: Receive = {
    case ActivityParticipantsRequest(domain, id) =>
      participantsRequest()
    case ActivityJoinRequest(domain, id, sessionId, state, client) =>
      join(sessionId, state, client)
    case ActivityLeave(domain, id, sessionId) =>
      leave(sessionId)
    case ActivityUpdateState(domain, id, sessionId, set, complete, removed) =>
      onStateUpdated(sessionId, set, complete, removed)
    case Terminated(actor) =>
      handleClientDeath(actor)
  }

  private[this] def participantsRequest(): Unit = {
    sender ! ActivityParticipants(stateMap.getState())
  }

  private[this] def isSessionJoined(sessionId: String): Boolean = {
    this.joinedSessions.contains(sessionId)
  }

  private[this] def join(sessionId: String, state: Map[String, JValue], client: ActorRef): Unit = {
    this.joinedSessions.get(sessionId) match {
      case Some(x) =>
        this.sender ! Status.Failure(ActivityAlreadyJoinedException(this.activityId))
      case None =>
        this.joinedSessions += (sessionId -> client)
        this.joinedClients += (client -> sessionId)
        this.stateMap.join(sessionId)

        state.foreach {
          case (k, v) =>
            this.stateMap.setState(sessionId, k, v)
        }

        context.watch(client)

        val message = ActivitySessionJoined(activityId, sessionId, state)
        joinedSessions.values filter (_ != client) foreach (_ ! message)

        sender ! ActivityJoinResponse(stateMap.getState())
    }
  }
  
  private[this] def onStateUpdated(sessionId: String, setState: Map[String, JValue], complete: Boolean, removed: List[String]): Unit = {
    if (isSessionJoined(sessionId)) {
      if (complete) {
        stateMap.clear()
      }
      
      setState.foreach {
        case (key: String, value: Any) =>
          stateMap.setState(sessionId, key, value)
      }
      
      removed.foreach(key => stateMap.removeState(sessionId, key))

      val setter = this.joinedSessions(sessionId)
      val message = ActivityStateUpdated(activityId, sessionId, setState, complete, removed)
      joinedSessions.values.filter(_ != setter) foreach (_ ! message)
    } else {
      log.warning(s"Activity(${this.identityString}): Received a state update for a session(${sessionId}) that is not joined to the activity.")
    }
  }

  private[this] def leave(sessionId: String): Unit = {
    if (!isSessionJoined(sessionId)) {
      this.sender ! Status.Failure(ActivityNotJoinedException(this.activityId))
    } else {
      handleSessionLeft(sessionId)
    }
  }

  private[this] def handleSessionLeft(sessionId: String): Unit = {
    val leaver = this.joinedSessions(sessionId)
    val message = ActivitySessionLeft(activityId, sessionId)
    joinedSessions.values filter (_ != leaver) foreach (_ ! message)

    this.stateMap.leave(sessionId)
    this.joinedSessions -= sessionId
    this.joinedClients -= leaver

    this.context.unwatch(leaver)

    if (this.joinedSessions.isEmpty) {
      this.passivate()
    }
  }

  private[this] def handleClientDeath(actor: ActorRef): Unit = {
    this.joinedClients.get(actor) match {
      case Some(sessionId) =>
        log.debug(s"${identityString}: Client with session ${sessionId} was terminated.  Leaving activity.")
        this.handleSessionLeft(sessionId)
      case None =>
        log.warning(s"${identityString}: Deathwatch on a client was triggered for an actor that did not have thi activity open")
    }
  }
}