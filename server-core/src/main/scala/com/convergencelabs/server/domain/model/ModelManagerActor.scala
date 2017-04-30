package com.convergencelabs.server.domain.model

import java.util.UUID

import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.UnauthorizedException

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.Terminated

case class QueryModelsRequest(sk: SessionKey, query: String)
case class QueryOrderBy(field: String, ascending: Boolean)
case class QueryModelsResponse(result: List[ModelQueryResult])

object ModelManagerActor {

  val RelativePath = "modelManager"

  def props(domainFqn: DomainFqn,
    protocolConfig: ProtocolConfiguration,
    persistenceManager: DomainPersistenceManager,
    modelPermissionResolver: ModelPermissionResolver,
    modelCreator: ModelCreator): Props = Props(
    new ModelManagerActor(
      domainFqn,
      protocolConfig,
      persistenceManager,
      modelPermissionResolver,
      modelCreator))
}

class ModelManagerActor(
  private[this] val domainFqn: DomainFqn,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] val persistenceManager: DomainPersistenceManager,
  private[this] val modelPermissionResolver: ModelPermissionResolver,
  private[this] val modelCreator: ModelCreator)
    extends Actor with ActorLogging {

  private[this] var openRealtimeModels = Map[String, ActorRef]()
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
    val OpenRealtimeModelRequest(sk, modelId, initializerProvided, clientActor) = openRequest
    val id = modelId.getOrElse(UUID.randomUUID().toString())
    this.openRealtimeModels.get(id) match {
      case Some(modelActor) =>
        // Model already open
        modelActor forward openRequest
      case None =>
        createModelActorAndForwarOpenRequest(id, openRequest)
    }
  }

  private[this] def createModelActorAndForwarOpenRequest(modelId: String, openRequest: OpenRealtimeModelRequest): Unit = {
    // TODO We could optimize this such that the real time model actor only asks for this when it established that
    // it is going to actually go live. That way we don't unessisarily burn through these.
    val resourceId = "" + nextModelResourceId
    nextModelResourceId += 1

    val props = RealtimeModelActor.props(
      self,
      domainFqn,
      modelId,
      resourceId,
      persistenceProvider,
      modelPermissionResolver,
      modelCreator,
      5000 // FIXME hard-coded time.  Should this be part of the protocol?
      )

    val modelActor = context.actorOf(props, resourceId)
    this.openRealtimeModels += (modelId -> modelActor)
    this.context.watch(modelActor)

    // FIXME we probably want a new message, we don't really need to send some of the info.
    modelActor forward openRequest
  }

  private[this] def onCreateModelRequest(createRequest: CreateModelRequest): Unit = {
    val CreateModelRequest(sk, collectionId, modelId, data, overridePermissions, worldPermissions, userPermissions) = createRequest
    // FIXME perhaps these should be some expected error type, like InvalidArgument
    if (collectionId.length == 0) {
      sender ! Status.Failure(new IllegalArgumentException("The collecitonId can not be empty when creating a model"))
    } else {
      modelCreator.createModel(
        persistenceProvider,
        Some(sk.uid),
        collectionId,
        modelId,
        data,
        overridePermissions,
        worldPermissions,
        userPermissions) map { model =>
          sender ! model.metaData.modelId
          ()
        } recover {
          case e: DuplicateValueException =>
            sender ! Status.Failure(ModelAlreadyExistsException(modelId.getOrElse("???")))
          case e: Exception =>
            log.error(e, s"Could not create model: ${modelId}")
            sender ! Status.Failure(e)
        }
    }
  }

  private[this] def onDeleteModelRequest(deleteRequest: DeleteModelRequest): Unit = {
    val DeleteModelRequest(sk, modelId) = deleteRequest
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        modelPermissionResolver.getModelUserPermissions(modelId, sk, persistenceProvider)
          .map { p => p.remove }
          .flatMap { canDelete =>
            if (canDelete) {
              if (openRealtimeModels.contains(modelId)) {
                val closed = openRealtimeModels(modelId)
                closed ! ModelDeleted()
                openRealtimeModels -= modelId
              }
              persistenceProvider.modelStore.deleteModel(modelId)
            } else {
              val message = "User must have 'remove' permissions on the model to remove it."
              Failure(UnauthorizedException(message))
            }
          }
      } else {
        Failure(ModelNotFoundException(modelId))
      }
    } map { _ =>
      sender ! ModelDeleted()
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def onGetModelPermissions(request: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(sk, modelId) = request
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        modelPermissionResolver.getModelUserPermissions(modelId, sk, persistenceProvider).map(p => p.read).flatMap { canRead =>
          if (canRead) {
            val permissionsStore = persistenceProvider.modelPermissionsStore
            modelPermissionResolver.getModelPermissions(modelId, persistenceProvider).map { p =>
              val ModelPemrissionResult(overrideCollection, modelWorld, modelUsers) = p
              GetModelPermissionsResponse(overrideCollection, modelWorld, modelUsers)
            }
          } else {
            val message = "User must have 'read' permissions on the model to get permissions."
            Failure(UnauthorizedException(message))
          }
        }
      } else {
        Failure(ModelNotFoundException(modelId))
      }
    } map { response =>
      sender ! response
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def onSetModelPermissions(request: SetModelPermissionsRequest): Unit = {
    val SetModelPermissionsRequest(sk, modelId, overrideCollection, world, setAllUsers, users) = request
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        modelPermissionResolver.getModelUserPermissions(modelId, sk, persistenceProvider).map(p => p.manage).flatMap { canSet =>
          if (canSet) {
            (for {
              _ <- overrideCollection match {
                case Some(ov) => persistenceProvider.modelPermissionsStore.setOverrideCollectionPermissions(modelId, ov)
                case None => Success(())
              }
              _ <- world match {
                case Some(perms) => persistenceProvider.modelPermissionsStore.setModelWorldPermissions(modelId, perms)
                case None => Success(())
              }
              _ <- setAllUsers match {
                case true => persistenceProvider.modelPermissionsStore.deleteAllModelUserPermissions(modelId)
                case falese => Success(())
              }
              _ <- persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(modelId, users)
            } yield {
              this.openRealtimeModels.get(modelId).foreach { _ forward request }
              ()
            })
          } else {
            Failure(UnauthorizedException("User must have 'manage' permissions on the model to set permissions"))
          }
        }
      } else {
        Failure(ModelNotFoundException(modelId))
      }
    } map { _ =>
      sender ! (())
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def onQueryModelsRequest(request: QueryModelsRequest): Unit = {
    val QueryModelsRequest(sk, query) = request
    val username = request.sk.admin match {
      case true => None
      case false => Some(request.sk.uid)
    }
    persistenceProvider.modelStore.queryModels(query, username) map { result =>
      sender ! QueryModelsResponse(result)
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def onModelShutdownRequest(shutdownRequest: ModelShutdownRequest): Unit = {
    val modelId = shutdownRequest.modelId
    openRealtimeModels.get(modelId) map (_ ! ModelShutdown)
    openRealtimeModels -= (modelId)
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
    persistenceManager.releasePersistenceProvider(self, context, domainFqn)
  }

  override def preStart(): Unit = {
    persistenceManager.acquirePersistenceProvider(self, context, domainFqn) match {
      case Success(provider) =>
        persistenceProvider = provider
      case Failure(cause) =>
        throw new IllegalStateException("Could not obtain a persistence provider", cause)
    }
  }
}
