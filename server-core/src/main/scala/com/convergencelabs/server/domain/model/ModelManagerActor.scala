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
import com.convergencelabs.server.datastore.DuplicateValueExcpetion
import com.convergencelabs.server.datastore.InvalidValueExcpetion

case class QueryModelsRequest(query: String)
case class QueryOrderBy(field: String, ascending: Boolean)

case class QueryModelsResponse(result: List[Model])

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
    case message: GetModelPermissionsRequest => onGetModelPermissions(message)
    case message: SetModelPermissionsRequest => onSetModelPermissions(message)
    case Terminated(actor) => onActorDeath(actor)
    case message: Any => unhandled(message)
  }

  private[this] def onOpenRealtimeModel(openRequest: OpenRealtimeModelRequest): Unit = {
    // FIXME check permissions
    
    this.openRealtimeModels.get(openRequest.modelFqn) match {
      case Some(modelActor) =>
        // Model already open
        modelActor forward openRequest
      case None =>
        // Model not already open, load it
        val resourceId = "" + nextModelResourceId
        nextModelResourceId += 1
        val collectionId = openRequest.modelFqn.collectionId
        persistenceProvider.collectionStore.ensureCollectionExists(collectionId) flatMap { _ =>
          getSnapshotConfigForModel(collectionId)
        } map { snapshotConfig =>
          
          // FIXME need to load permissions from persistence
          val permissions = RealTimeModelPermissions(ModelPermissions(true, true, true, true), Map())
          
          val props = RealtimeModelActor.props(
            self,
            domainFqn,
            openRequest.modelFqn,
            resourceId,
            persistenceProvider.modelStore,
            persistenceProvider.modelOperationProcessor,
            persistenceProvider.modelSnapshotStore,
            5000, // FIXME hard-coded time.  Should this be part of the protocol?
            snapshotConfig, 
            permissions)

          val modelActor = context.actorOf(props, resourceId)
          this.openRealtimeModels += (openRequest.modelFqn -> modelActor)
          this.context.watch(modelActor)
          modelActor forward openRequest
          ()
        } recover {
          case cause: Exception =>
            log.error(cause, s"Error opening model: ${openRequest.modelFqn}")
            sender ! UnknownErrorResponse("Could not open model due to an unexpected server error.")
        }
    }
  }

  private[this] def getSnapshotConfigForModel(collectionId: String): Try[ModelSnapshotConfig] = {
    persistenceProvider.collectionStore.getOrCreateCollection(collectionId).flatMap { c =>
      if (c.overrideSnapshotConfig) {
        c.snapshotConfig match {
          case Some(config) =>
            Success(config)
          case None =>
            Failure(new IllegalStateException("Collection overrides snapshot config, but the config is missing."))
        }
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
      createModel(collectionId, modelId, data)
    }
  }

  private[this] def createModel(collectionId: String, modelId: Option[String], data: ObjectValue): Unit = {
    // FIXME check permissions for collection create.
    
    // FIXME all of this should work or not, together. We also do this in two different places
    // we should abstract this somewhere
    persistenceProvider.collectionStore.ensureCollectionExists(collectionId) flatMap { _ =>
      persistenceProvider.modelStore.createModel(collectionId, modelId, data)
    } flatMap { model =>
      val ModelMetaData(fqn, version, created, modeified) = model.metaData
      val snapshot = ModelSnapshot(ModelSnapshotMetaData(fqn, version, created), model.data)
      persistenceProvider.modelSnapshotStore.createSnapshot(snapshot) map { _ => model}
    } map { model =>
      sender ! ModelCreated(model.metaData.fqn)
    } recover {
      case e: DuplicateValueExcpetion =>
        sender ! ModelAlreadyExists
      case e: InvalidValueExcpetion =>
        sender ! UnknownErrorResponse("Could not create model beause it contained an invalid value")
      case e: Exception =>
        sender ! UnknownErrorResponse("Could not create model: " + e.getMessage)
    }
  }

  private[this] def onDeleteModelRequest(deleteRequest: DeleteModelRequest): Unit = {
    // FIXME check permissions
    if (openRealtimeModels.contains(deleteRequest.modelFqn)) {
      val closed = openRealtimeModels(deleteRequest.modelFqn)
      closed ! ModelDeleted
      openRealtimeModels -= deleteRequest.modelFqn
    }

    persistenceProvider.modelStore.deleteModel(deleteRequest.modelFqn) map { _ =>
      sender ! ModelDeleted
    } recover {
      case cause: Exception => sender ! Status.Failure(cause)
    }
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(query) = request
    persistenceProvider.modelStore.queryModels(query) match {
      case Success(result) => sender ! QueryModelsResponse(result)
      case Failure(cause) => sender ! Status.Failure(cause)
    }
  }
  
  private[this] def onGetModelPermissions(request: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(collectionId, modelId) = request
    // FIXME need to implement getting this from the database
    sender ! GetModelPermissionsResponse(ModelPermissions(true, true, true, true), Map())
  }
  
  private[this] def onSetModelPermissions(request: SetModelPermissionsRequest): Unit = {
    val SetModelPermissionsRequest(collectionId, modelId, world, users, all) = request
    
    // FIXME need to set the permissions in the database
    
    openRealtimeModels.get(ModelFqn(collectionId, modelId)) map { model =>
      // FIXME if the model is open need to get the new aggregate permissions
      // and send them to the open model.
      val permissions =  RealTimeModelPermissions(ModelPermissions(true, true, true, true), Map())
      model ! RealTimeModelPermissionsUpdated(permissions)
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
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) =>
        persistenceProvider = provider
      case Failure(cause) =>
        throw new IllegalStateException("Could not obtain a persistence provider", cause)
    }
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
