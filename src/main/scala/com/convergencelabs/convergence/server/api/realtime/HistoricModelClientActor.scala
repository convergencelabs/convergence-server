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
import com.convergencelabs.convergence.proto.model._
import com.convergencelabs.convergence.server.util.actor.AskUtils
import com.convergencelabs.convergence.server.api.realtime.ProtocolConnection.ReplyCallback
import com.convergencelabs.convergence.server.api.realtime.protocol.CommonProtoConverters._
import com.convergencelabs.convergence.server.api.realtime.protocol.DataValueProtoConverters._
import com.convergencelabs.convergence.server.api.realtime.protocol.ModelOperationConverters._
import com.convergencelabs.convergence.server.backend.services.domain.model.{ModelOperationStoreActor, RealtimeModelActor}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import grizzled.slf4j.Logging
import scalapb.GeneratedMessage

import scala.concurrent.ExecutionContextExecutor
import scala.language.postfixOps

private final class HistoricModelClientActor(context: ActorContext[HistoricModelClientActor.Message],
                                             domain: DomainId,
                                             operationStoreActor: ActorRef[ModelOperationStoreActor.Message],
                                             modelShardRegion: ActorRef[RealtimeModelActor.Message],
                                             defaultTimeout: Timeout)
  extends AbstractBehavior[HistoricModelClientActor.Message](context) with Logging with AskUtils {

  import HistoricModelClientActor._

  private[this] implicit val timeout: Timeout = defaultTimeout
  private[this] implicit val ec: ExecutionContextExecutor = context.executionContext
  private[this] implicit val system: ActorSystem[_] = context.system

  override def onMessage(msg: HistoricModelClientActor.Message): Behavior[HistoricModelClientActor.Message] = {
    msg match {
      case IncomingProtocolRequest(message, replyCallback) =>
        onRequestReceived(message, replyCallback)
    }
    Behaviors.same
  }

  private[this] def onRequestReceived(message: IncomingRequest, replyCallback: ReplyCallback): Unit = {
    message match {
      case dataRequest: HistoricalDataRequestMessage =>
        onDataRequest(dataRequest, replyCallback)
      case operationRequest: HistoricalOperationRequestMessage =>
        onOperationRequest(operationRequest, replyCallback)
    }
  }

  private[this] def onDataRequest(request: HistoricalDataRequestMessage, cb: ReplyCallback): Unit = {
    modelShardRegion.ask[RealtimeModelActor.GetRealtimeModelResponse](RealtimeModelActor.GetRealtimeModelRequest(domain, request.modelId, None, _))
      .map(_.model.fold(
        {
          case RealtimeModelActor.ModelNotFoundError() =>
            cb.expectedError(ErrorCodes.ModelNotFound, s"A model with id '${request.modelId}' does not exist.")
          case RealtimeModelActor.UnauthorizedError(message) =>
            cb.expectedError(ErrorCodes.Unauthorized, message)
          case RealtimeModelActor.UnknownError() =>
            cb.unexpectedError("Unexpected error getting historical model data.")
        },
        { model =>
          val response = HistoricalDataResponseMessage(
            model.metaData.collection,
            Some(objectValueToProto(model.data)),
            model.metaData.version,
            Some(instanceToTimestamp(model.metaData.createdTime)),
            Some(instanceToTimestamp(model.metaData.modifiedTime))
          )
          cb.reply(response)
        }))
      .recoverWith(handleAskFailure(_, cb))
  }

  private[this] def onOperationRequest(request: HistoricalOperationRequestMessage, cb: ReplyCallback): Unit = {
    val HistoricalOperationRequestMessage(modelId, first, last, _) = request
    operationStoreActor.ask[ModelOperationStoreActor.GetOperationsResponse](ModelOperationStoreActor.GetOperationsRequest(request.modelId, first, last, _))
      .map(_.operations.fold(
        {
          case ModelOperationStoreActor.ModelNotFoundError() =>
            cb.expectedError(ErrorCodes.ModelNotFound, s"A model with id '$modelId' does not exist.")
          case ModelOperationStoreActor.UnknownError() =>
            cb.unexpectedError("Unexpected error getting historical model operations.")
        },
        { operations =>
          val response = HistoricalOperationsResponseMessage(operations map modelOperationToProto)
          cb.reply(response)
        }))
      .recoverWith(handleAskFailure(_, cb))
  }
}

object HistoricModelClientActor {
  private[realtime] def apply(domain: DomainId,
                              operationStoreActor: ActorRef[ModelOperationStoreActor.Message],
                              modelShardRegion: ActorRef[RealtimeModelActor.Message],
                              defaultTimeout: Timeout): Behavior[Message] =
    Behaviors.setup(context => new HistoricModelClientActor(
      context, domain, operationStoreActor, modelShardRegion, defaultTimeout))

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable

  sealed trait IncomingMessage extends Message

  type IncomingRequest = GeneratedMessage with RequestMessage with HistoricalModelMessage with ClientMessage

  final case class IncomingProtocolRequest(message: IncomingRequest, replyCallback: ReplyCallback) extends IncomingMessage

}
