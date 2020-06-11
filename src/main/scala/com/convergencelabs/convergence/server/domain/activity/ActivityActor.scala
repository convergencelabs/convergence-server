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

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.server.actor.{CborSerializable, ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor._
import com.convergencelabs.convergence.server.domain.DomainId
import com.convergencelabs.convergence.server.domain.activity.ActivityActor.Message
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JValue

import scala.util.{Success, Try}

class ActivityActor(context: ActorContext[Message],
                    shardRegion: ActorRef[Message],
                    shard: ActorRef[ClusterSharding.ShardCommand])
  extends ShardedActor[Message](context, shardRegion, shard)
    with Logging {

  import ActivityActor._

  /**
   * Will be initialized on the first message inside the initialize method.
   */
  private[this] var activityId: String = _
  private[this] var domain: DomainId = _

  private[this] var joinedClients = Map[ActorRef[OutgoingMessage], String]()
  private[this] var joinedSessions = Map[String, ActorRef[OutgoingMessage]]()
  private[this] val stateMap = new ActivityStateMap()

  override protected def setIdentityData(message: Message): Try[String] = {
    this.activityId = message.activityId
    this.domain = message.domain
    Success(s"${domain.namespace}/${domain.domainId}/${this.activityId}")
  }

  override def initialize(message: Message): Try[ShardedActorStatUpPlan] = {
    Success(StartUpRequired)
  }

  override def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case msg: GetParticipantsRequest =>
        onGetParticipantsRequest(msg)
      case msg: JoinRequest =>
        onJoinRequest(msg)
      case msg: LeaveRequest =>
        leave(msg)
      case msg: UpdateState =>
        onUpdateState(msg)
    }
  }

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = super.onSignal orElse {
    case Terminated(actor) =>
      handleClientDeath(actor.asInstanceOf[ActorRef[OutgoingMessage]])
  }

  private[this] def onJoinRequest(msg: JoinRequest): Behavior[Message] = {
    val JoinRequest(_, _, sessionId, state, client, replyTo) = msg
    this.joinedSessions.get(sessionId) match {
      case Some(_) =>
        replyTo ! JoinResponse(Left(AlreadyJoined()))

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

        replyTo ! JoinResponse(Right(Joined(stateMap.getState)))
    }

    Behaviors.same
  }

  private[this] def onGetParticipantsRequest(msg: GetParticipantsRequest): Behavior[Message] = {
    val GetParticipantsRequest(_, _, replyTo) = msg
    replyTo ! GetParticipantsResponse(stateMap.getState)
    Behaviors.same
  }

  private[this] def isSessionJoined(sessionId: String): Boolean = {
    this.joinedSessions.contains(sessionId)
  }

  private[this] def onUpdateState(msg: UpdateState): Behavior[Message] = {
    val UpdateState(_, _, sessionId, setState, complete, removed) = msg

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
      warn(s"Activity(${this.identityString}): Received a state update for a session($sessionId) that is not joined to the activity.")
    }

    Behaviors.same
  }

  private[this] def leave(msg: LeaveRequest): Behavior[Message] = {
    val LeaveRequest(_, _, sessionId, replyTo) = msg
    if (!isSessionJoined(sessionId)) {
      replyTo ! LeaveResponse(Left(NotJoinedError()))
      Behaviors.same
    } else {
      replyTo ! LeaveResponse(Right(()))
      handleSessionLeft(sessionId)
    }
  }

  private[this] def handleSessionLeft(sessionId: String): Behavior[Message] = {
    val leaver = this.joinedSessions(sessionId)
    val message = ActivitySessionLeft(activityId, sessionId)
    joinedSessions.values filter (_ != leaver) foreach (_ ! message)

    this.stateMap.leave(sessionId)
    this.joinedSessions -= sessionId
    this.joinedClients -= leaver

    this.context.unwatch(leaver)

    if (this.joinedSessions.isEmpty) {
      this.passivate()
    } else {
      Behaviors.same
    }
  }

  private[this] def handleClientDeath(actor: ActorRef[OutgoingMessage]): Behavior[Message] = {
    this.joinedClients.get(actor) match {
      case Some(sessionId) =>
        debug(s"$identityString: Client with session $sessionId was terminated.  Leaving activity.")
        this.handleSessionLeft(sessionId)
      case None =>
        warn(s"$identityString: Deathwatch on a client was triggered for an actor that did not have thi activity open")
        Behaviors.same
    }
  }
}

object ActivityActor {
  def apply(shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand]): Behavior[Message] =
    Behaviors.setup(context => new ActivityActor(context, shardRegion, shard))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    val domain: DomainId
    val activityId: String
  }

  //
  // Join
  //
  case class JoinRequest(domain: DomainId,
                         activityId: String,
                         sessionId: String,
                         state: Map[String, JValue],
                         client: ActorRef[OutgoingMessage],
                         replyTo: ActorRef[JoinResponse]) extends Message

  sealed trait JoinError

  case class AlreadyJoined() extends JoinError

  case class JoinResponse(response: Either[JoinError, Joined]) extends CborSerializable

  case class Joined(state: Map[String, Map[String, JValue]])


  //
  // Leave
  //
  case class LeaveRequest(domain: DomainId,
                          activityId: String,
                          sessionId: String,
                          replyTo: ActorRef[LeaveResponse]) extends Message

  sealed trait LeaveError

  case class NotJoinedError() extends LeaveError

  case class LeaveResponse(response: Either[LeaveError, Unit]) extends CborSerializable


  //
  // Update State
  //
  case class UpdateState(domain: DomainId,
                         activityId: String,
                         sessionId: String,
                         state: Map[String, JValue],
                         complete: Boolean,
                         removed: List[String]) extends Message

  //
  // GetParticipants
  //
  case class GetParticipantsRequest(domain: DomainId,
                                    activityId: String,
                                    replyTo: ActorRef[GetParticipantsResponse]) extends Message

  case class GetParticipantsResponse(state: Map[String, Map[String, JValue]]) extends CborSerializable

}