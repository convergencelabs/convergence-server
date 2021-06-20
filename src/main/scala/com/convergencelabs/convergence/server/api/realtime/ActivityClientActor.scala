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
import com.convergencelabs.convergence.proto.core._
import com.convergencelabs.convergence.server.api.realtime.ActivityClientActor.Message
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.api.realtime.protocol.IdentityProtoConverters.domainUserIdToProto
import com.convergencelabs.convergence.server.api.realtime.protocol.{CommonProtoConverters, JsonProtoConverters, PermissionProtoConverters}
import com.convergencelabs.convergence.server.backend.services.domain.activity.{ActivityActor, ActivityAutoCreationOptions}
import com.convergencelabs.convergence.server.backend.services.domain.permissions.AllPermissions
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
 * @param domainId            The id of the domain that the user connected to.
 * @param session             The session id of the connected client.
 */
private final class ActivityClientActor private(context: ActorContext[Message],
                                                activityShardRegion: ActorRef[ActivityActor.Message],
                                                clientActor: ActorRef[ClientActor.SendServerMessage],
                                                domainId: DomainId,
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
        val updateMessage = ActivityActor.UpdateState(domainId, activityId, session.sessionId, mappedState, complete, removed.toList)
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
      ActivityActor.GetParticipantsRequest(domainId, id, _))
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

    val worldPermission = PermissionProtoConverters.protoToWorldPermissions(world)
    val userPermissions = PermissionProtoConverters.protoToUserPermissions(user)
    val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(group)
    val allPermissions = AllPermissions(worldPermission, userPermissions, groupPermissions)

    activityShardRegion
      .ask[ActivityActor.CreateResponse](
        ActivityActor.CreateRequest(domainId, id, Some(session), allPermissions, _))
      .map(_.response.fold({
        case ActivityActor.AlreadyExists() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyExists, s"The activity with the specified type and id already exists: {type: '$activityType', id: $activityId}.")
        case ActivityActor.UnauthorizedError(msg) =>
          cb.expectedError(ErrorCodes.Unauthorized, msg.getOrElse(""))
        case ActivityActor.UnknownError() =>
          cb.unknownError()
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
        ActivityActor.DeleteRequest(domainId, id, Some(session), _))
      .map(_.response.fold({
        case err: ActivityActor.CommonErrors =>
          handleCommonErrors(err, cb)
      }, { _ =>
        cb.reply(OkResponse())
      }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onActivityJoin(RequestMessage: ActivityJoinRequestMessage, cb: ReplyCallback): Unit = {
    val ActivityJoinRequestMessage(activityType, activityId, lurk, state, autoCreateMessage, _) = RequestMessage
    val id = ActivityId(activityType, activityId)
    val jsonState = JsonProtoConverters.valueMapToJValueMap(state)
    val autoCreateOptions = autoCreateMessage.map { data =>

      val worldPermission = PermissionProtoConverters.protoToWorldPermissions(data.worldPermissions)
      val userPermissions = PermissionProtoConverters.protoToUserPermissions(data.userPermissions)
      val groupPermissions = PermissionProtoConverters.protoToGroupPermissions(data.groupPermissions)

      ActivityAutoCreationOptions(data.ephemeral, worldPermission, userPermissions, groupPermissions)
    }
    val resource = resourceManager.getOrAssignResource(id)
    activityShardRegion
      .ask[ActivityActor.JoinResponse] { replyTo =>
        ActivityActor.JoinRequest(
          domainId, id, session.sessionId, lurk, jsonState, autoCreateOptions, context.self.narrow[OutgoingMessage], replyTo)
      }
      .map(_.response.fold({
        case ActivityActor.AlreadyJoined() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyJoined, s"The session is already joined to activity: {type: '$activityType', id: $activityId}.")
        case ActivityActor.NotFoundError() =>
          cb.expectedError(ErrorCodes.ActivityAlreadyJoined, s"The activity does not exist: {type: '$activityType', id: $activityId}.")
        case ActivityActor.UnauthorizedError(msg) =>
          cb.expectedError(ErrorCodes.Unauthorized, msg.getOrElse(""));
        case ActivityActor.UnknownError() =>
          cb.unknownError()
      }, { response =>
        val ActivityActor.Joined(ephemeral, created, state) = response
        val mappedState = state.view.mapValues(v => ActivityStateData(JsonProtoConverters.jValueMapToValueMap(v))).toMap
        cb.reply(ActivityJoinResponseMessage(resource, mappedState, ephemeral, Some(CommonProtoConverters.instantToTimestamp(created))))
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
            ActivityActor.LeaveRequest(domainId, activityId, session.sessionId, _))
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

  def onPermissionRequestReceived(message: GeneratedMessage with RequestMessage with PermissionsMessage with ClientMessage, replyCallback: ReplyCallback): Behavior[Message] = {
    message match {
      case message: AddPermissionsRequestMessage =>
        onAddPermissions(message, replyCallback)
      case message: RemovePermissionsRequestMessage =>
        onRemovePermissions(message, replyCallback)
      case message: SetPermissionsRequestMessage =>
        onSetActivityPermissions(message, replyCallback)
      case message: ResolvePermissionsForConnectedSessionRequestMessage =>
        onGetConnectedUserPermissionsRequestMessage(message, replyCallback)
      case message: GetPermissionsRequestMessage =>
        onGetPermissionsRequest(message, replyCallback)

    }

    Behaviors.same
  }

  private[this] def onAddPermissions(message: AddPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    getActivityIdFromPermissionTarget(message.target, cb).map { activityId =>
      val addPermissions = PermissionProtoConverters.protoToAddPermissions(message)
      activityShardRegion
        .ask[ActivityActor.AddPermissionsResponse](
          ActivityActor.AddPermissionsRequest(domainId, activityId, Some(session), addPermissions, _))
        .map(_.response.fold(
          {
            case error: ActivityActor.CommonErrors =>
              handleCommonErrors(error, cb)
          },
          _ => cb.reply(OkResponse())
        ))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onRemovePermissions(message: RemovePermissionsRequestMessage, cb: ReplyCallback): Unit = {
    getActivityIdFromPermissionTarget(message.target, cb).map { activityId =>
      val removePermissions = PermissionProtoConverters.protoToRemovePermissions(message)
      activityShardRegion
        .ask[ActivityActor.RemovePermissionsResponse](
          ActivityActor.RemovePermissionsRequest(domainId, activityId, Some(session), removePermissions, _))
        .map(_.response.fold(
          {
            case error: ActivityActor.CommonErrors =>
              handleCommonErrors(error, cb)
          },
          _ => cb.reply(OkResponse())
        ))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onSetActivityPermissions(message: SetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    getActivityIdFromPermissionTarget(message.target, cb).map { activityId =>
      val setPermissions = PermissionProtoConverters.protoToSetPermissions(message)
      activityShardRegion
        .ask[ActivityActor.SetPermissionsResponse](
          ActivityActor.SetPermissionsRequest(domainId, activityId, Some(session), setPermissions, _))
        .map(_.response.fold(
          {
            case error: ActivityActor.CommonErrors =>
              handleCommonErrors(error, cb)
          },
          _ => cb.reply(OkResponse())
        ))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def onGetConnectedUserPermissionsRequestMessage(message: ResolvePermissionsForConnectedSessionRequestMessage, cb: ReplyCallback): Unit = {
    val ResolvePermissionsForConnectedSessionRequestMessage(target, _) = message
    getActivityIdFromPermissionTarget(target, cb).map { activityId =>
      activityShardRegion
        .ask[ActivityActor.ResolvePermissionsResponse](ActivityActor.ResolvePermissionsRequest(domainId, activityId, Some(session), session, _))
        .map(_.permissions.fold(
          {
            case error: ActivityActor.CommonErrors =>
              handleCommonErrors(error, cb)
          },
          permissions => cb.reply(ResolvePermissionsForConnectedSessionResponseMessage(permissions.toSeq))
        ))
        .recover(_ => cb.timeoutError())
    }
  }


  private[this] def onGetPermissionsRequest(message: GetPermissionsRequestMessage, cb: ReplyCallback): Unit = {
    val GetPermissionsRequestMessage(target, _) = message
    getActivityIdFromPermissionTarget(target, cb).map { activityId =>
      activityShardRegion
        .ask[ActivityActor.GetPermissionsResponse](ActivityActor.GetPermissionsRequest(domainId, activityId, Some(session), _))
        .map(_.permissions.fold(
          {
            case error: ActivityActor.CommonErrors =>
              handleCommonErrors(error, cb)
          },
          { permissions =>
            val userPermissions = permissions.user.map { entry =>
              UserPermissionsEntry(Some(domainUserIdToProto(entry._1)), entry._2.toSeq)
            }.toSeq
            val groupPermissions = permissions.group.map(entry => (entry._1, PermissionsList(entry._2.toSeq)))
            val worldPermissions = permissions.world.toSeq

            cb.reply(GetPermissionsResponseMessage(userPermissions, groupPermissions, worldPermissions))
          }))
        .recover(_ => cb.timeoutError())
    }
  }

  private[this] def getActivityIdFromPermissionTarget(target: Option[PermissionTarget], cb: ReplyCallback): Option[ActivityId] = {
    target match {
      case Some(t) =>
        t.targetType match {
          case PermissionTarget.TargetType.Activity(target) =>
            Some(ActivityId(target.`type`, target.id))
          case _ =>
            cb.expectedError(ErrorCodes.InvalidMessage, "The permission target was not set")
            None
        }
      case None =>
        cb.expectedError(ErrorCodes.InvalidMessage, "The permission target was not set")
        None
    }
  }

  private[this] def handleCommonErrors(error: ActivityActor.CommonErrors, cb: ReplyCallback): Unit = {
    error match {
      case ActivityActor.NotFoundError() =>
        cb.expectedError(ErrorCodes.ActivityNotFound, "The specified activity does not exist.")
      case ActivityActor.UnauthorizedError(msg) =>
        cb.expectedError(ErrorCodes.Unauthorized, msg.getOrElse(""))
      case ActivityActor.UnknownError() =>
        cb.unknownError()
      case ActivityActor.NotJoinedError() =>
        cb.expectedError(ErrorCodes.ActivityNotJoined, "The session must be joined to the activity.")

    }
  }

  //
  // Outgoing Messages
  //

  private[this] def onOutgoingMessage(msg: ActivityClientActor.OutgoingMessage): Behavior[Message] = {
    resourceManager.getResource(msg.activityId) match {
      case Some(resource) =>
        val serverMessage: GeneratedMessage with ServerMessage with NormalMessage = msg match {
          case ActivitySessionJoined(_, sessionId, state) =>
            ActivitySessionJoinedMessage(resource, sessionId, JsonProtoConverters.jValueMapToValueMap(state))
          case ActivitySessionLeft(_, sessionId) =>
            ActivitySessionLeftMessage(resource, sessionId)
          case ActivityStateUpdated(_, sessionId, state, complete, removed) =>
            ActivityStateUpdatedMessage(
              resource, sessionId, JsonProtoConverters.jValueMapToValueMap(state), complete, removed)
        }

        clientActor ! ClientActor.SendServerMessage(serverMessage)
      case None =>
        warn("Received an outgoing message for an activity that is not open: " + msg.activityId)
    }

    Behaviors.same
  }
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
