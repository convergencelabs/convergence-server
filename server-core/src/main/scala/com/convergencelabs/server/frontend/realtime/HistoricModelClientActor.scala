package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.util.concurrent.AskFuture

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.util.Timeout
import akka.actor.Terminated
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.ModelStoreActor
import com.convergencelabs.server.datastore.ModelStoreActor.GetModel
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.datastore.ModelOperationStoreActor
import com.convergencelabs.server.datastore.ModelOperationStoreActor.GetOperations
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.SessionKey
import com.convergencelabs.server.domain.model.ModelFqn

object HistoricModelClientActor {
  def props(
    sk: SessionKey,
    domainFqn: DomainFqn): Props =
    Props(new HistoricModelClientActor(sk, domainFqn))
}

class HistoricModelClientActor(
  sessionKey: SessionKey,
  domainFqn: DomainFqn)
    extends Actor with ActorLogging {

  private[this] implicit val timeout = Timeout(5 seconds)
  private[this] implicit val ec = context.dispatcher

  private var modelStoreActor: ActorRef = _
  private var operationStoreActor: ActorRef = _

  def receive: Receive = {
    case RequestReceived(message, replyPromise) if message.isInstanceOf[IncomingHistoricalModelRequestMessage] =>
      onRequestReceived(message.asInstanceOf[IncomingHistoricalModelRequestMessage], replyPromise)
    case x: Any => unhandled(x)
  }

  private[this] def onRequestReceived(message: IncomingHistoricalModelRequestMessage, replyCallback: ReplyCallback): Unit = {
    message match {
      case dataRequest: HistoricalDataRequestMessage           => onDataRequest(dataRequest, replyCallback)
      case operationRequest: HistoricalOperationRequestMessage => onOperationRequest(operationRequest, replyCallback)
    }
  }

  private[this] def onDataRequest(request: HistoricalDataRequestMessage, cb: ReplyCallback): Unit = {
    (modelStoreActor ? GetModel(ModelFqn(request.c, request.m))).mapResponse[Option[Model]] onComplete {
      case (Success(Some(model))) => {
        cb.reply(
          HistoricalDataResponseMessage(
            model.data,
            model.metaData.version,
            model.metaData.createdTime.toEpochMilli,
            model.metaData.modifiedTime.toEpochMilli))
      }
      case Success(None) => {
        ???
      }
      case Failure(cause) => {
        log.error(cause, "Unexpected error getting model history.")
        cb.unknownError()
      }
    }
  }

  private[this] def onOperationRequest(request: HistoricalOperationRequestMessage, cb: ReplyCallback): Unit = {
    val HistoricalOperationRequestMessage(collection, modelId, first, last) = request
    (operationStoreActor ? GetOperations(ModelFqn(collection, modelId), first, last)).mapResponse[List[ModelOperation]] onComplete {
      case (Success(operations)) => {
        cb.reply(HistoricalOperationsResponseMessage(operations map ModelOperationMapper.mapOutgoing))
      }
      case Failure(cause) => {
        log.error(cause, "Unexpected error getting model history.")
        cb.unknownError()
      }
    }
  }

  override def preStart(): Unit = {
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) =>
        modelStoreActor = context.actorOf(ModelStoreActor.props(provider.modelStore, provider.collectionStore))
        operationStoreActor = context.actorOf(ModelOperationStoreActor.props(provider.modelOperationStore))
      case Failure(cause) =>
        log.error(cause, "Unable to obtain a domain persistence provider.")
    }
  }

  override def postStop(): Unit = {
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }
}
