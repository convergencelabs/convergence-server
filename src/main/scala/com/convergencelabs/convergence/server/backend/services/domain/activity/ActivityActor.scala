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

package com.convergencelabs.convergence.server.backend.services.domain.activity

import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor._
import com.convergencelabs.convergence.server.backend.services.domain.activity.ActivityActor.Message
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.actor.{ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import org.json4s.JsonAST.JValue

import scala.util.{Success, Try}

/**
 * The [[ActivityActor]] represents a single activity in the system. Activities
 * are generally used for sharing collaborative cues and presence in sub
 * sections of a collaborative application.
 *
 * @param context     The ActorContext this actor is created in.
 * @param shardRegion The shard region ActivityActors are created in.
 * @param shard       The specific shard this actor resides in.
 */
private final class ActivityActor(domainId: DomainId,
                                  activityId: String,
                                  context: ActorContext[Message],
                                  shardRegion: ActorRef[Message],
                                  shard: ActorRef[ClusterSharding.ShardCommand])
  extends ShardedActor[Message](
    context,
    shardRegion,
    shard,
    entityDescription = s"${domainId.namespace}/${domainId.domainId}/$activityId") {

  import ActivityActor._

  private[this] var joinedClients = Map[ActorRef[OutgoingMessage], String]()
  private[this] var joinedSessions = Map[String, ActorRef[OutgoingMessage]]()
  private[this] val stateMap = new ActivityStateMap()


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

  override protected def onTerminated(actor: ActorRef[Nothing]): Behavior[Message] = {
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
      replyTo ! LeaveResponse(Right(Ok()))
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
  def apply(domainId: DomainId,
            activityId: String,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand]): Behavior[Message] = Behaviors.setup(context =>
    new ActivityActor(
      domainId,
      activityId,
      context,
      shardRegion,
      shard))

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
  final case class JoinRequest(domain: DomainId,
                               activityId: String,
                               sessionId: String,
                               state: Map[String, JValue],
                               client: ActorRef[OutgoingMessage],
                               replyTo: ActorRef[JoinResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[AlreadyJoined], name = "already_joined")
  ))
  sealed trait JoinError

  final case class AlreadyJoined() extends JoinError

  final case class Joined(state: Map[String, Map[String, JValue]])

  final case class JoinResponse(response: Either[JoinError, Joined]) extends CborSerializable


  //
  // Leave
  //
  final case class LeaveRequest(domain: DomainId,
                                activityId: String,
                                sessionId: String,
                                replyTo: ActorRef[LeaveResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[NotJoinedError], name = "not_joined")
  ))
  sealed trait LeaveError

  final case class NotJoinedError() extends LeaveError

  final case class LeaveResponse(response: Either[LeaveError, Ok]) extends CborSerializable


  //
  // Update State
  //
  final case class UpdateState(domain: DomainId,
                               activityId: String,
                               sessionId: String,
                               state: Map[String, JValue],
                               complete: Boolean,
                               removed: List[String]) extends Message

  //
  // GetParticipants
  //
  final case class GetParticipantsRequest(domain: DomainId,
                                          activityId: String,
                                          replyTo: ActorRef[GetParticipantsResponse]) extends Message

  final case class GetParticipantsResponse(state: Map[String, Map[String, JValue]]) extends CborSerializable

}
