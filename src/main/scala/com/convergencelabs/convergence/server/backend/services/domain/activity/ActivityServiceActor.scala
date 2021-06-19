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
import com.convergencelabs.convergence.common.PagedData
import com.convergencelabs.convergence.server.backend.services.domain.{BaseDomainShardedActor, DomainPersistenceManager}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.{Activity, ActivityId}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}
import com.fasterxml.jackson.databind.annotation.JsonDeserialize

import scala.concurrent.duration.FiniteDuration

class ActivityServiceActor private(domainId: DomainId,
                                   context: ActorContext[ActivityServiceActor.Message],
                                   shardRegion: ActorRef[ActivityServiceActor.Message],
                                   shard: ActorRef[ClusterSharding.ShardCommand],
                                   domainPersistenceManager: DomainPersistenceManager,
                                   receiveTimeout: FiniteDuration)
  extends BaseDomainShardedActor[ActivityServiceActor.Message](domainId, context, shardRegion, shard, domainPersistenceManager, receiveTimeout) {

  import ActivityServiceActor._

  override def receiveInitialized(msg: Message): Behavior[Message] = {
    msg match {
      case message: GetActivitiesRequest =>
        onGetActivities(message)
      case message: GetActivityRequest =>
        onGetActivity(message)
      case ReceiveTimeout(_) =>
        this.passivate()
    }
  }

  private[this] def onGetActivities(message: GetActivitiesRequest): Behavior[Message] = {
    val GetActivitiesRequest(_, typeFilter, idFilter, limit, offset, replyTo) = message
    this.persistenceProvider.activityStore
      .searchActivities(typeFilter, idFilter, limit, offset)
      .map(activities => Right(activities))
      .recover { cause =>
        error("unexpected error searching chats", cause)
        Left(UnknownError())
      }.foreach(replyTo ! GetActivitiesResponse(_))

    Behaviors.same
  }

  private[this] def onGetActivity(message: GetActivityRequest): Behavior[Message] = {
    val GetActivityRequest(_, activityId, replyTo) = message
    persistenceProvider.activityStore.findActivity(activityId).map {
      case Some(activity) =>
        Right(activity)
      case None =>
        Left(ActivityNotFound())
    }.recover {
      case t: Throwable =>
        error("error getting activity: " + activityId, t)
        Left(UnknownError())
    }.foreach(replyTo ! GetActivityResponse(_))

    Behaviors.same
  }

  override protected def getDomainId(msg: Message): DomainId = msg.domainId

  override protected def getReceiveTimeoutMessage(): Message = ReceiveTimeout(this.domainId)
}


object ActivityServiceActor {
  def apply(domainId: DomainId,
            shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            domainPersistenceManager: DomainPersistenceManager,
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup(context =>
    new ActivityServiceActor(
      domainId,
      context,
      shardRegion,
      shard,
      domainPersistenceManager,
      receiveTimeout
    ))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[GetActivitiesRequest], name = "get_activities"),
    new JsonSubTypes.Type(value = classOf[GetActivityRequest], name = "get_activity")
  ))
  sealed trait Message extends CborSerializable {
    val domainId: DomainId
  }

  private case class ReceiveTimeout(domainId: DomainId) extends Message

  //
  // Get Activities
  //

  final case class GetActivitiesRequest(domainId: DomainId,
                                        typeFilter: Option[String],
                                        idFilter: Option[String],
                                        @JsonDeserialize(contentAs = classOf[Long])
                                        limit: QueryLimit,
                                        @JsonDeserialize(contentAs = classOf[Long])
                                        offset: QueryOffset,
                                        replyTo: ActorRef[GetActivitiesResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetActivitiesError

  final case class GetActivitiesResponse(activities: Either[GetActivitiesError, PagedData[Activity]]) extends CborSerializable

  //
  // Get Activities
  //

  final case class GetActivityRequest(domainId: DomainId,
                                      activityId: ActivityId,
                                      replyTo: ActorRef[GetActivityResponse]) extends Message

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ActivityNotFound], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetActivityError

  final case class ActivityNotFound() extends GetActivityError

  final case class GetActivityResponse(activity: Either[GetActivityError, Activity]) extends CborSerializable


  //
  // Common Errors
  //
  final case class UnknownError() extends AnyRef
    with GetActivitiesError
    with GetActivityError
}

