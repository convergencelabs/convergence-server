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

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{AbstractBehavior, ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, ActorSystem, Behavior}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.activity._
import com.convergencelabs.convergence.proto.core.OkResponse
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor.Message
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.api.realtime.protocol.{JsonProtoConverters, PermissionProtoConverters}
import com.convergencelabs.convergence.server.backend.datastore.domain.permissions.WorldPermission
import com.convergencelabs.convergence.server.backend.services.domain.activity.{ActivityActor, ActivityAutoCreationOptions}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.activity.ActivityId
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.util.actor.AskUtils
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import grizzled.slf4j.Logging
import org.json4s.JsonAST.JValue
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

/**
 * The ActivityClientActor handles messages to and from the client for the
 * Activity subsystem. It is generally spawned by a [[ClientActor]] and the
 * [[ClientActor]] will forward all Activity related messages to this
 * child actor.
 *
 * @param context             The ActorContext for this actor.
 * @param activityShardRegion The shard region that contains ActivityActor entities.
 * @param clientActor         The ClientActor that this ActivityClientActor is a child of.
 * @param domain              The id of the domain that the user connected to.
 * @param session             The session id of the connected client.
 */
private final class ActivityClientActor private(context: ActorContext[Message],
                                                activityShardRegion: ActorRef[ActivityActor.Message],
                                                clientActor: ActorRef[ClientActor.SendServerMessage],
                                                domain: DomainId,
                                                session: DomainSessionAndUserId,
                                                defaultTimeout: Timeout)
  extends AbstractBehavior[Message](context) with Logging with AskUtils {

  import ActivityClientActor._

  private[this] val resourceManager = new ResourceManager[ActivityId]()

  private[this] implicit val timeout: Timeout = defaultTimeout
  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  override def onMessage(msg: ActivityClientActor.Message): Behavior[Message] = {
    msg match {
      case msg: IncomingMessage =>
        onIncomingMessage(msg)
      case msg: OutgoingMessage =>
        onOutgoingMessage(msg)
    }
  }

  //
  // Incoming Messages
  //

  private[this] def onIncomingMessage(msg: ActivityClientActor.IncomingMessage): Behavior[Message] = {
    msg match {
      case IncomingProtocolMessage(message) =>
        onMessageReceived(message)
      case IncomingProtocolRequest(message, replyCallback) =>
        onRequestReceived(message, replyCallback)
      case IncomingProtocolPermissionsRequest(message, replyCallback) =>
        onPermissionRequestReceived(message, replyCallback)
    }

    Behaviors.same
  }


  private[this] def onMessageReceived(message: IncomingNormalMessage): Unit = {
    message match {
      case setState: ActivityUpdateStateMessage =>
        onActivityUpdateState(setState)
    }
  }

  private[this] def onActivityUpdateState(message: ActivityUpdateStateMessage): Unit = {
    resourceManager.getId(message.resourceId) match {
      case Some(activityId) =>
        val ActivityUpdateStateMessage(_, state, complete, removed, _) = message
        val mappedState = JsonProtoConverters.valueMapToJValueMap(state)
        val updateMessage = ActivityActor.UpdateState(domain, activityId, session.sessionId, mappedState, complete, removed.toList)
        this.activityShardRegion ! updateMessage
      case None =>
        warn("Received an activity update message for an unregistered resource id: " + message.resourceId)
    }
  }

  private[this] def onRequestReceived(message: IncomingRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case join: ActivityJoinRequestMessage =>
        onActivityJoin(join, replyCallback)
      case leave: ActivityLeaveRequestMessage =>
        onActivityLeave(leave, replyCallback)
      case participant: ActivityParticipantsRequestMessage =>
        onParticipantsRequest(participant, replyCallback)
      case create: ActivityCreateRequestMessage =>
        onActivityCreateRequest(create, replyCallback)
      case delete: ActivityDeleteRequestMessage =>
        onActivityDeleteRequest(delete, replyCallback)
    }
  }

  private[this] def onParticipantsRequest(RequestMessage: ActivityParticipantsRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityParticipantsRequestMessage(activityType, activityId, _) = RequestMessage
    val id = ActivityId(activityType, activityId)
    activityShardRegion.ask[ActivityActor.GetParticipantsResponse](
      ActivityActor.GetParticipantsRequest(domain, id, _))
      .map { response =>
        cb.reply(ActivityParticipantsResponseMessage(response.state.map {
          case (k, v) => k -> ActivityStateData(JsonProtoConverters.jValueMapToValueMap(v))
        }))
      }
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityCreateRequest(RequestMessage: ActivityCreateRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityCreateRequestMessage(activityType, activityId, world, user, group, _) = RequestMessage
    val id = ActivityId(activityType, activityId)
    val worldPermission = world.toSet.map(WorldPermission)
    val userPermissions = PermissionProtoConverters.protoToUserPermissions(user)
    val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(group)

    activityShardRegion
      .ask[ActivityActor.CreateResponse](
        ActivityActor.CreateRequest(domain, id, Some(session), worldPermission, userPermissions, groupPermissions, _))
      .map(_.response.fold({
        case ActivityActor.AlreadyExists() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyExists, s"The activity with the specified type and id already exists: {type: '$activityType', id: $activityId}.")
      }, { _ =>
        cb.reply(OkResponse())
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityDeleteRequest(RequestMessage: ActivityDeleteRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityDeleteRequestMessage(activityType, activityId, _) = RequestMessage
    val id = ActivityId(activityType, activityId)

    activityShardRegion
      .ask[ActivityActor.DeleteResponse](
        ActivityActor.DeleteRequest(domain, id, Some(session), _))
      .map(_.response.fold({
        case ActivityActor.NotFound() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyExists, s"The activity with the specified type and id doest not exist: {type: '$activityType', id: $activityId}.")
        case ActivityActor.Unauthorized() =>
          cb.expectedError(ErrorCodes.Unauthorized, "The user does not have permissions to delete the activity");

      }, { _ =>
        cb.reply(OkResponse())
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityJoin(RequestMessage: ActivityJoinRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinRequestMessage(activityType, activityId, state, autoCreateMessage, _) = RequestMessage
    val id = ActivityId(activityType, activityId)
    val jsonState = JsonProtoConverters.valueMapToJValueMap(state)
    val autoCreateOptions = autoCreateMessage.map { data =>
      val worldPermission = data.worldPermissions.toSet.map(WorldPermission)
      val userPermissions = PermissionProtoConverters.protoToUserPermissions(data.userPermissions)
      val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(data.groupPermissions)
      ActivityAutoCreationOptions(data.ephemeral, worldPermission, userPermissions, groupPermissions)
    }
    val resource = resourceManager.getOrAssignResource(id)
    activityShardRegion
      .ask[ActivityActor.JoinResponse](
        ActivityActor.JoinRequest(domain, id, session.sessionId, jsonState, autoCreateOptions, context.self.narrow[OutgoingMessage], _))
      .map(_.response.fold({
        case ActivityActor.AlreadyJoined() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyJoined, s"The session is already joined to activity: {type: '$activityType', id: $activityId}.")
        case ActivityActor.Unauthorized() =>
          cb.expectedError(ErrorCodes.Unauthorized, "The user does not have permissions to join the activity");
      }, { response =>
        val mappedState = response.state.view.mapValues(v => ActivityStateData(JsonProtoConverters.jValueMapToValueMap(v))).toMap
        cb.reply(ActivityJoinResponseMessage(resource, mappedState))
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityLeave(message: ActivityLeaveRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityLeaveRequestMessage(resource, _) = message
    resourceManager.getId(resource) match {
      case Some(activityId) =>
        resourceManager.releaseResource(resource)
        activityShardRegion
          .ask[ActivityActor.LeaveResponse](
            ActivityActor.LeaveRequest(domain, activityId, session.sessionId, _))
          .map(_.response.fold({
            case ActivityActor.NotJoinedError() =>
              cb.expectedError(ErrorCodes.ActivityNotJoined, s"The session is not joined to activity: {type: '${activityId.activityType}', id: ${activityId.id}}.")
          }, { _ =>
            cb.reply(OkResponse())
          }))
          .recoverWith(handleAskFailure(_, cb))
      case None =>
        cb.expectedError(ErrorCodes.ActivityNoSuchResource, s"Received a request for an unknown resource id")

    }
  }


  //
  // Outgoing Messages
  //

  private[this] def onOutgoingMessage(msg: ActivityClientActor.OutgoingMessage): Behavior[Message] = {
    resourceManager.getResource(msg.activityId) match {
      case Some(resource) =>
        val serverMessage: GeneratedMessage with ServerMessage with NormalMessage = msg match {
          case ActivitySessionJoined(activityId, sessionId, state) =>
            ActivitySessionJoinedMessage(resource, sessionId, JsonProtoConverters.jValueMapToValueMap(state))
          case ActivitySessionLeft(activityId, sessionId) =>
            ActivitySessionLeftMessage(resource, sessionId)
          case ActivityStateUpdated(activityId, sessionId, state, complete, removed) =>
            ActivityStateUpdatedMessage(
              resource, sessionId, JsonProtoConverters.jValueMapToValueMap(state), complete, removed)
        }

        clientActor ! ClientActor.SendServerMessage(serverMessage)
      case None =>
        warn("Received an outgoing message for an activity that is not open: " + msg.activityId)
    }

    Behaviors.same
  }

  def onPermissionRequestReceived(message: GeneratedMessage with RequestMessage with PermissionsMessage with ClientMessage, replyCallback: ReplyCallback): Behavior[Message] = ???
}

object ActivityClientActor {
  private[realtime] def apply(domain: DomainId,
                              session: DomainSessionAndUserId,
                              clientActor: ActorRef[ClientActor.SendServerMessage],
                              activityServiceActor: ActorRef[ActivityActor.Message],
                              defaultTimeout: Timeout
                             ): Behavior[ActivityClientActor.Message] =
    Behaviors.setup(context => new ActivityClientActor(context, activityServiceActor, clientActor, domain, session, defaultTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message

  //
  // Messages from the client
  //
  private[realtime] sealed trait IncomingMessage extends Message

  private[realtime] type IncomingNormalMessage = GeneratedMessage with NormalMessage with ActivityMessage with ClientMessage

  private[realtime] final case class IncomingProtocolMessage(message: IncomingNormalMessage) extends IncomingMessage

  private[realtime] type IncomingRequestMessage = GeneratedMessage with RequestMessage with ActivityMessage with ClientMessage

  private[realtime] final case class IncomingProtocolRequest(message: IncomingRequestMessage, replyCallback: ReplyCallback) extends IncomingMessage

  private[realtime] final case class IncomingProtocolPermissionsRequest(message: GeneratedMessage with RequestMessage with PermissionsMessage with ClientMessage, replyCallback: ReplyCallback) extends ActivityClientActor.IncomingMessage


  //
  // Messages from within the server
  //
  sealed trait OutgoingMessage extends Message with CborSerializable {
    val activityId: ActivityId
  }

  final case class ActivitySessionJoined(activityId: ActivityId, sessionId: String, state: Map[String, JValue]) extends OutgoingMessage

  final case class ActivitySessionLeft(activityId: ActivityId, sessionId: String) extends OutgoingMessage

  final case class ActivityStateUpdated(activityId: ActivityId,
                                        sessionId: String,
                                        state: Map[String, JValue],
                                        complete: Boolean,
                                        removed: List[String]) extends OutgoingMessage

}
