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

import akka.actor.{Actor, ActorLogging, ActorRef, Props}
import akka.util.Timeout
import com.convergencelabs.convergence.proto._
import com.convergencelabs.convergence.proto.model._
import com.convergencelabs.convergence.server.actor.CborSerializable
import com.convergencelabs.convergence.server.api.realtime.ImplicitMessageConversions.{instanceToTimestamp, objectValueToMessage}
import com.convergencelabs.convergence.server.datastore.domain.ModelOperationStoreActor.{GetOperationsRequest, GetOperationsResponse}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId}
import com.convergencelabs.convergence.server.domain.model.{GetRealtimeModel, Model, ModelOperation, RealtimeModelSharding}
import com.convergencelabs.convergence.server.util.concurrent.AskFuture
import akka.pattern.ask

import scala.concurrent.ExecutionContextExecutor
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.{Failure, Success}

class HistoricModelClientActor(
  private[this] val session: DomainUserSessionId,
  private[this] val domainFqn: DomainId,
  private[this] val modelStoreActor: ActorRef,
  private[this] val operationStoreActor: ActorRef)
    extends Actor with ActorLogging {

  import HistoricModelClientActor._

  private[this] implicit val timeout: Timeout = Timeout(5 seconds)
  private[this] implicit val ec: ExecutionContextExecutor = context.dispatcher

  private[this] val modelClusterRegion: ActorRef = RealtimeModelSharding.shardRegion(this.context.system)

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[RequestMessage with HistoricalMessage] =>
      onRequestReceived(message.asInstanceOf[RequestMessage with HistoricalMessage], replyPromise)
    case x: Any =>
      unhandled(x)
  }

  private[this] def onRequestReceived(message: RequestMessage with HistoricalMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case dataRequest: HistoricalDataRequestMessage =>
        onDataRequest(dataRequest, replyCallback)
      case operationRequest: HistoricalOperationRequestMessage =>
        onOperationRequest(operationRequest, replyCallback)
    }
  }

  private[this] def onDataRequest(request: HistoricalDataRequestMessage, cb: ReplyCallback): Unit = {
    (modelClusterRegion ? GetRealtimeModel(domainFqn, request.modelId, None)).mapResponse[Option[Model]] onComplete {
      case Success(Some(model)) =>
        cb.reply(
          HistoricalDataResponseMessage(
            model.metaData.collection,
            Some(model.data),
            model.metaData.version,
            Some(model.metaData.createdTime),
            Some(model.metaData.modifiedTime)))
      case Success(None) =>
        cb.expectedError("model_not_found", "The model does not exist")
      case Failure(cause) =>
        log.error(cause, "Unexpected error getting model history.")
        cb.unknownError()
    }
  }

  private[this] def onOperationRequest(request: HistoricalOperationRequestMessage, cb: ReplyCallback): Unit = {
    val HistoricalOperationRequestMessage(modelId, first, last) = request
    (operationStoreActor ? GetOperationsRequest(modelId, first, last)).mapResponse[GetOperationsResponse] onComplete {
      case Success(GetOperationsResponse(operations)) =>
        cb.reply(HistoricalOperationsResponseMessage(operations map ModelOperationMapper.mapOutgoing))
      case Failure(cause) =>
        log.error(cause, "Unexpected error getting model history.")
        cb.unknownError()
    }
  }
}

object HistoricModelClientActor {
  def props(
             session: DomainUserSessionId,
             domainFqn: DomainId,
             modelStoreActor: ActorRef,
             operationStoreActor: ActorRef): Props =
    Props(new HistoricModelClientActor(session, domainFqn, modelStoreActor, operationStoreActor))
}
