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
import akka.actor.Terminated
import com.convergencelabs.server.domain.model.data.ObjectValue
import scala.util.control.NonFatal
import com.convergencelabs.server.datastore.CreateSuccess
import com.convergencelabs.server.datastore.DuplicateValue
import com.convergencelabs.server.datastore.InvalidValue
import com.convergencelabs.server.datastore.DeleteSuccess
import com.convergencelabs.server.datastore.NotFound

case class QueryModelsRequest(collection: Option[String], limit: Option[Int], offset: Option[Int], orderBy: Option[QueryOrderBy])
case class QueryOrderBy(field: String, ascending: Boolean)

case class QueryModelsResponse(result: List[ModelMetaData])

class ModelManagerActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val protocolConfig: ProtocolConfiguration)
    extends Actor with ActorLogging {

  private[this] var openRealtimeModels = Map[ModelFqn, ActorRef]()
  private[this] var nextModelResourceId: Long = 0

  var persistenceProvider: DomainPersistenceProvider = _

  def receive: Receive = {
    case message: OpenRealtimeModelRequest => onOpenRealtimeModel(message)
    case message: CreateModelRequest => onCreateModelRequest(message)
    case message: DeleteModelRequest => onDeleteModelRequest(message)
    case message: QueryModelsRequest => onQueryModelsRequest(message)
    case message: ModelShutdownRequest => onModelShutdownRequest(message)
    case Terminated(actor) => onActorDeath(actor)
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
        val collectionId = openRequest.modelFqn.collectionId
        persistenceProvider.collectionStore.ensureCollectionExists(collectionId) flatMap (_ =>
          getSnapshotConfigForModel(collectionId)) flatMap { snapshotConfig =>
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
          this.openRealtimeModels += (openRequest.modelFqn -> modelActor)
          this.context.watch(modelActor)
          modelActor forward openRequest
          Success(())
        } recover {
          case e: Exception =>
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
    val CreateModelRequest(collectionId, modelId, data) = createRequest
    // FIXME perhaps these should be some expected error type, like InvalidArgument
    if (collectionId.length == 0) {
      sender ! UnknownErrorResponse("The collecitonId can not be empty when creating a model")
    } else {
      // FIXME is this worth doing, there is still a race condition here.  We should probably
      // just create and have that method let us know of a duplicate
      modelId match {
        case Some(id) =>
          persistenceProvider.modelStore.modelExists(ModelFqn(collectionId, id)) map {
            case true =>
              sender ! ModelAlreadyExists
            case false =>
              createModel(collectionId, modelId, data)
          } recover {
            case cause: Exception =>
              sender ! akka.actor.Status.Failure(cause)
          }
        case None =>
          createModel(collectionId, None, data)
      }
    }
  }

  private[this] def createModel(collectionId: String, modelId: Option[String], data: ObjectValue): Unit = {
    // FIXME all of this should work or not, together. We also do this in two different places
    // we should abstract this somewhere
    persistenceProvider.collectionStore.ensureCollectionExists(collectionId) flatMap (_ =>
      persistenceProvider.modelStore.createModel(collectionId, modelId, data)) map {
      case CreateSuccess(model) =>
        val ModelMetaData(fqn, version, created, modeified) = model.metaData
        val snapshot = ModelSnapshot(ModelSnapshotMetaData(fqn, version, created), model.data)
        persistenceProvider.modelSnapshotStore.createSnapshot(snapshot)
        sender ! ModelCreated
      case DuplicateValue =>
        sender ! ModelAlreadyExists
      case InvalidValue =>
        // FIXME better error message
        sender ! UnknownErrorResponse("Could not create model beause it contained an invalid value")
    } recover {
      case e: Exception =>
        sender ! UnknownErrorResponse("Could not create model: " + e.getMessage)
    }
  }

  private[this] def onDeleteModelRequest(deleteRequest: DeleteModelRequest): Unit = {
    if (openRealtimeModels.contains(deleteRequest.modelFqn)) {
      val closed = openRealtimeModels(deleteRequest.modelFqn)
      closed ! ModelDeleted
      openRealtimeModels -= deleteRequest.modelFqn
    }

    persistenceProvider.modelStore.deleteModel(deleteRequest.modelFqn) map {
      case DeleteSuccess =>
        sender ! ModelDeleted
      case NotFound =>
        sender ! ModelNotFound
    } recover {
      case cause: Exception => sender ! Status.Failure
    }
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(collection, limit, offset, orderBy) = request
    persistenceProvider.modelStore.queryModels(collection, limit, offset, orderBy map { ob => (ob.field, ob.ascending) }) match {
      case Success(result) => sender ! QueryModelsResponse(result)
      case Failure(cause) => sender ! Status.Failure(cause)
    }
  }

  private[this] def onModelShutdownRequest(shutdownRequest: ModelShutdownRequest): Unit = {
    val fqn = shutdownRequest.modelFqn
    openRealtimeModels.get(fqn) map (_ ! ModelShutdown)
    openRealtimeModels -= (fqn)
  }

  private[this] def onActorDeath(actor: ActorRef): Unit = {
    // TODO might be more efficient ay to do this.
    openRealtimeModels = openRealtimeModels filter {
      case (fqn, modelActorRef) =>
        actor != modelActorRef
    }
  }

  override def postStop(): Unit = {
    log.debug("ModelManagerActor({}) received shutdown command.  Shutting down all Realtime Models.", this.domainFqn)
    openRealtimeModels = Map()
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
