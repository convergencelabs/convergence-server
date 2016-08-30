package com.convergencelabs.server.domain.model

import akka.actor.ActorLogging
import akka.actor.Actor
import scala.collection.mutable
import akka.actor.ActorRef
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.ProtocolConfiguration
import akka.actor.PoisonPill
import scala.compat.Platform
import java.io.IOException
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import akka.actor.Props
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import java.time.Instant
import java.time.Duration
import java.time.temporal.TemporalUnit
import java.time.temporal.ChronoUnit
import com.convergencelabs.server.domain.ModelSnapshotConfig
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.UnknownErrorResponse
import scala.util.Try
import akka.actor.Status

case class QueryModelsRequest(collection: Option[String], limit: Option[Int], offset: Option[Int], orderBy: Option[QueryOrderBy])
case class QueryOrderBy(field: String, ascending: Boolean)

case class QueryModelsResponse(result: List[ModelMetaData])

class ModelManagerActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  private[this] val openRealtimeModels = mutable.Map[ModelFqn, ActorRef]()
  private[this] var nextModelResourceId: Long = 0

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case message: OpenRealtimeModelRequest => onOpenRealtimeModel(message)
    case message: CreateModelRequest => onCreateModelRequest(message)
    case message: DeleteModelRequest => onDeleteModelRequest(message)
    case message: QueryModelsRequest => onQueryModelsRequest(message)
    case message: ModelShutdownRequest => onModelShutdownRequest(message)
    case message: Any => unhandled(message)
  }

  private[this] def onOpenRealtimeModel(openRequest: OpenRealtimeModelRequest): Unit = {
    this.openRealtimeModels.get(openRequest.modelFqn) match {
      case Some(modelActor) =>
        // Model already open
        modelActor forward openRequest
      case None =>
        // Model not already open, load it
        val resourceId = "" + nextModelResourceId
        nextModelResourceId += 1
        getSnapshotConfigForModel(openRequest.modelFqn.collectionId) match {
          case Success(snapshotConfig) =>
            val props = RealtimeModelActor.props(
              self,
              domainFqn,
              openRequest.modelFqn,
              resourceId,
              persistenceProvider.modelStore,
              persistenceProvider.modelOperationProcessor,
              persistenceProvider.modelSnapshotStore,
              5000, // FIXME hard-coded time.  Should this be part of the protocol?
              snapshotConfig)

            val modelActor = context.actorOf(props, resourceId)
            this.openRealtimeModels.put(openRequest.modelFqn, modelActor)
            modelActor forward openRequest
          case Failure(e) =>
            sender ! UnknownErrorResponse("Could not open model")
        }
    }
  }

  private[this] def getSnapshotConfigForModel(collectionId: String): Try[ModelSnapshotConfig] = {
    persistenceProvider.collectionStore.getOrCreateCollection(collectionId).flatMap { c =>
      if (c.overrideSnapshotConfig) {
        Success(c.snapshotConfig.get)
      } else {
        persistenceProvider.configStore.getModelSnapshotConfig()
      }
    }
  }

  private[this] def onCreateModelRequest(createRequest: CreateModelRequest): Unit = {
    val ModelFqn(collectionId, modelId) = createRequest.modelFqn
    // FIXME perhaps these should be some expected error type, like InvalidArgument
    if (collectionId.length == 0) {
      sender ! UnknownErrorResponse("The collecitonId can not be empty when creating a model")
    } else if (modelId.length == 0) {
      sender ! UnknownErrorResponse("The modelId can not be empty when creating a model")
    } else {
      persistenceProvider.modelStore.modelExists(createRequest.modelFqn) match {
        case Success(true) =>
          sender ! ModelAlreadyExists
        case Success(false) =>
          val createTime = Instant.now()
          val model = Model(
            ModelMetaData(
              createRequest.modelFqn,
              0,
              createTime,
              createTime),
            createRequest.modelData)

          try {
            // FIXME all of this should work or not, together.
            persistenceProvider.collectionStore.ensureCollectionExists(createRequest.modelFqn.collectionId)
            persistenceProvider.modelStore.createModel(model)
            persistenceProvider.modelSnapshotStore.createSnapshot(
              ModelSnapshot(ModelSnapshotMetaData(createRequest.modelFqn, 0, createTime),
                createRequest.modelData))

            sender ! ModelCreated
          } catch {
            case e: IOException =>
              sender ! UnknownErrorResponse("Could not create model: " + e.getMessage)
          }
        case Failure(cause) =>
          sender ! akka.actor.Status.Failure(cause)
      }
    }
  }

  private[this] def onDeleteModelRequest(deleteRequest: DeleteModelRequest): Unit = {
    persistenceProvider.modelStore.modelExists(deleteRequest.modelFqn) match {
      case Success(true) =>
        if (openRealtimeModels.contains(deleteRequest.modelFqn)) {
          openRealtimeModels.remove(deleteRequest.modelFqn).get ! ModelDeleted
        }

        persistenceProvider.modelStore.deleteModel(deleteRequest.modelFqn)

        sender ! ModelDeleted
      case Success(false) => sender ! ModelNotFound
      case Failure(cause) => sender ! Status.Failure
    }
  }
  
  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(collection, limit, offset, orderBy) = request
    persistenceProvider.modelStore.queryModels(collection, limit, offset, orderBy) match {
      case Success(result) => sender ! QueryModelsResponse(result)
      case Failure(cause) => sender ! Status.Failure(cause)
    }
  }

  private[this] def onModelShutdownRequest(shutdownRequest: ModelShutdownRequest): Unit = {
    val fqn = shutdownRequest.modelFqn
    val modelActor = openRealtimeModels.remove(fqn)
    if (!modelActor.isEmpty) {
      modelActor.get ! PoisonPill
    }
  }

  override def postStop(): Unit = {
    log.debug("ModelManagerActor({}) received shutdown command.  Shutting down all Realtime Models.", this.domainFqn)
    openRealtimeModels.clear()
    DomainPersistenceManagerActor.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    // FIXME Handle none better with logging.
    persistenceProvider = DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn).get
  }
}

object ModelManagerActor {

  val RelativePath = "modelManager"

  def props(domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration): Props = Props(
    new ModelManagerActor(
      domainFqn,
      protocolConfig))
}
