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

package com.convergencelabs.convergence.server.domain.activity

import akka.actor.{ActorLogging, ActorRef, Props, Status, Terminated}
import com.convergencelabs.convergence.server.actor.{CborSerializable, ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor._
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.activity.ActivityActor.ActivityActorMessage
import org.json4s.JsonAST.JValue

import scala.util.{Success, Try}


class ActivityActor()
  extends ShardedActor(classOf[ActivityActorMessage])
  with ActorLogging {

  import ActivityActor._

  /**
   * Will be initialized on the first message inside the initialize method.
   */
  private[this] var activityId: String = _
  private[this] var domain: DomainId = _

  private[this] var joinedClients = Map[ActorRef, String]()
  private[this] var joinedSessions = Map[String, ActorRef]()
  private[this] var stateMap = new ActivityStateMap()

  override protected def setIdentityData(message: ActivityActorMessage): Try[String] = {
    this.activityId = message.activityId
    this.domain = message.domain
    Success(s"${domain.namespace}/${domain.domainId}/${this.activityId}")
  }

  override def initialize(message: ActivityActorMessage): Try[ShardedActorStatUpPlan] = {
    Success(StartUpRequired)
  }

  override def receiveInitialized: Receive = {
    case ActivityParticipantsRequest(_, _) =>
      participantsRequest()
    case ActivityJoinRequest(_, _, sessionId, state, client) =>
      join(sessionId, state, client)
    case ActivityLeave(_, _, sessionId) =>
      leave(sessionId)
    case ActivityUpdateState(_, _, sessionId, set, complete, removed) =>
      onStateUpdated(sessionId, set, complete, removed)
    case Terminated(actor) =>
      handleClientDeath(actor)
  }

  private[this] def participantsRequest(): Unit = {
    sender ! ActivityParticipants(stateMap.getState)
  }

  private[this] def isSessionJoined(sessionId: String): Boolean = {
    this.joinedSessions.contains(sessionId)
  }

  private[this] def join(sessionId: String, state: Map[String, JValue], client: ActorRef): Unit = {
    this.joinedSessions.get(sessionId) match {
      case Some(_) =>
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

        sender ! ActivityJoinResponse(stateMap.getState)
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
      log.warning(s"Activity(${this.identityString}): Received a state update for a session($sessionId) that is not joined to the activity.")
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
        log.debug(s"$identityString: Client with session $sessionId was terminated.  Leaving activity.")
        this.handleSessionLeft(sessionId)
      case None =>
        log.warning(s"$identityString: Deathwatch on a client was triggered for an actor that did not have thi activity open")
    }
  }
}

object ActivityActor {
  def props(): Props = {
    Props(new ActivityActor())
  }

  sealed trait ActivityActorMessage extends CborSerializable {
    val domain: DomainId
    val activityId: String
  }

  case class ActivityParticipantsRequest(domain: DomainId, activityId: String) extends ActivityActorMessage

  case class ActivityJoinRequest(domain: DomainId, activityId: String, sessionId: String, state: Map[String, JValue], actorRef: ActorRef) extends ActivityActorMessage
  case class ActivityLeave(domain: DomainId, activityId: String, sessionId: String) extends ActivityActorMessage

  case class ActivityUpdateState(domain: DomainId,
                                  activityId: String,
                                  sessionId: String,
                                  state: Map[String, JValue],
                                  complete: Boolean,
                                  removed: List[String]) extends ActivityActorMessage

  // Exceptions
  case class ActivityAlreadyJoinedException(activityId: String) extends Exception(s"Activity '$activityId' is already joined.")
  case class ActivityNotJoinedException(activityId: String) extends Exception(s"Activity '$activityId' is not joined.")
}