package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.datastore.domain.ModelOperationStoreActor.GetOperations
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.model.GetRealtimeModel
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.RealtimeModelSharding
import com.convergencelabs.server.frontend.realtime.ImplicitMessageConversions.instanceToTimestamp
import com.convergencelabs.server.frontend.realtime.ImplicitMessageConversions.objectValueToMessage
import com.convergencelabs.server.util.concurrent.AskFuture

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.util.Timeout
import io.convergence.proto.Historical
import io.convergence.proto.Request
import io.convergence.proto.model.HistoricalDataRequestMessage
import io.convergence.proto.model.HistoricalDataResponseMessage
import io.convergence.proto.model.HistoricalOperationRequestMessage
import io.convergence.proto.model.HistoricalOperationsResponseMessage

object HistoricModelClientActor {
  def props(
    session: DomainUserSessionId,
    domainFqn: DomainFqn,
    modelStoreActor: ActorRef,
    operationStoreActor: ActorRef): Props =
    Props(new HistoricModelClientActor(session, domainFqn, modelStoreActor, operationStoreActor))
}

class HistoricModelClientActor(
  private[this] val session: DomainUserSessionId,
  private[this] val domainFqn: DomainFqn,
  private[this] val modelStoreActor: ActorRef,
  private[this] val operationStoreActor: ActorRef)
    extends Actor with ActorLogging {

  import akka.pattern.ask

  private[this] implicit val timeout = Timeout(5 seconds)
  private[this] implicit val ec = context.dispatcher

  private[this] val modelClusterRegion: ActorRef = RealtimeModelSharding.shardRegion(this.context.system)

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[Request with Historical] =>
      onRequestReceived(message.asInstanceOf[Request with Historical], replyPromise)
    case x: Any => unhandled(x)
  }

  private[this] def onRequestReceived(message: Request with Historical, replyCallback: ReplyCallback): Unit = {
    message match {
      case dataRequest: HistoricalDataRequestMessage => onDataRequest(dataRequest, replyCallback)
      case operationRequest: HistoricalOperationRequestMessage => onOperationRequest(operationRequest, replyCallback)
    }
  }

  private[this] def onDataRequest(request: HistoricalDataRequestMessage, cb: ReplyCallback): Unit = {
    (modelClusterRegion ? GetRealtimeModel(domainFqn, request.modelId, None)).mapResponse[Option[Model]] onComplete {
      case (Success(Some(model))) => {
        cb.reply(
          HistoricalDataResponseMessage(
            model.metaData.collectionId,
            Some(model.data),
            model.metaData.version,
            Some(model.metaData.createdTime),
            Some(model.metaData.modifiedTime)))
      }
      case Success(None) => {
        cb.expectedError("model_not_found", "The model does not exist")
      }
      case Failure(cause) => {
        log.error(cause, "Unexpected error getting model history.")
        cb.unknownError()
      }
    }
  }

  private[this] def onOperationRequest(request: HistoricalOperationRequestMessage, cb: ReplyCallback): Unit = {
    val HistoricalOperationRequestMessage(modelId, first, last) = request
    (operationStoreActor ? GetOperations(modelId, first, last)).mapResponse[List[ModelOperation]] onComplete {
      case (Success(operations)) => {
        cb.reply(HistoricalOperationsResponseMessage(operations map ModelOperationMapper.mapOutgoing))
      }
      case Failure(cause) => {
        log.error(cause, "Unexpected error getting model history.")
        cb.unknownError()
      }
    }
  }
}
