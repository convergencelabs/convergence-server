package com.convergencelabs.server.domain.model

import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.actor.ShardedActor
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.model.RealTimeModelManager.EventHandler
import com.convergencelabs.server.util.ActorBackedEventLoop
import com.convergencelabs.server.util.ActorBackedEventLoop.TaskScheduled
import com.convergencelabs.server.actor.ShardedActorStatUpPlan
import com.convergencelabs.server.actor.StartUpRequired
import com.convergencelabs.server.datastore.domain.ModelPermissions

import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.ReceiveTimeout
import akka.actor.Status
import akka.actor.Terminated
import akka.util.Timeout


/**
 * Provides a factory method for creating the RealtimeModelActor
 */
object RealtimeModelActor {
  def props(
    modelPemrissionResolver: ModelPermissionResolver,
    modelCreator: ModelCreator,
    persistenceManager: DomainPersistenceManager,
    clientDataResponseTimeout: FiniteDuration,
    receiveTimeout: FiniteDuration): Props =
    Props(new RealtimeModelActor(
      modelPemrissionResolver,
      modelCreator,
      persistenceManager,
      clientDataResponseTimeout,
      receiveTimeout))

  private object ErrorCodes extends Enumeration {
    val Unknown = "unknown"
    val ModelDeleted = "model_deleted"
  }
}

/**
 * An instance of the RealtimeModelActor manages the lifecycle of a single
 * realtime model.
 */
class RealtimeModelActor(
  private[this] val modelPermissionResolver: ModelPermissionResolver,
  private[this] val modelCreator: ModelCreator,
  private[this] val persistenceManager: DomainPersistenceManager,
  private[this] val clientDataResponseTimeout: FiniteDuration,
  private[this] val receiveTimeout: FiniteDuration)
  extends ShardedActor(classOf[ModelMessage]) {

  import RealtimeModelActor._

  private[this] var _persistenceProvider: Option[DomainPersistenceProvider] = None
  private[this] var _domainFqn: Option[DomainFqn] = None
  private[this] var _modelId: Option[String] = None
  private[this] var _modelManager: Option[RealTimeModelManager] = None

  //
  // Receive methods
  //

  override def receiveInitialized = this.receiveClosed

  private[this] def receiveClosed: Receive = {
    case msg: ReceiveTimeout =>
      this.passivate()
    case msg: StatelessModelMessage =>
      handleStatelessMessage(msg)
    case msg: OpenRealtimeModelRequest =>
      this.becomeOpened()
      this.receiveOpened(msg)
    case unknown: Any =>
      unhandled(unknown)
  }

  private[this] def receiveOpened: Receive = {
    case msg: StatelessModelMessage =>
      handleStatelessMessage(msg)

    case msg: RealTimeModelMessage =>
      handleRealtimeMessage(msg)

    case TaskScheduled(task) =>
      task()

    case terminated: Terminated =>
      modelManager.handleTerminated(terminated)

    case unknown: Any =>
      unhandled(unknown)
  }

  private def handleStatelessMessage(msg: StatelessModelMessage): Unit = {
    msg match {
      case msg: GetRealtimeModel =>
        this.getModel(msg)
      case msg: CreateRealtimeModel =>
        this.createModel(msg)
      case msg: DeleteRealtimeModel =>
        this.deleteModel(msg)
      case msg: CreateOrUpdateRealtimeModel =>
        this.createOrUpdateModel(msg)
      case msg: GetModelPermissionsRequest =>
        this.getModelPermissions(msg)
      case msg: SetModelPermissionsRequest =>
        this.setModelPermissions(msg)
    }
  }

  private[this] def getModelPermissions(msg: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(domainFqn, modelId, sk) = msg
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
      case cause: Throwable =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def setModelPermissions(msg: SetModelPermissionsRequest): Unit = {
    val SetModelPermissionsRequest(domainFqn, modelId, sk, overrideCollection, world, setAllUsers, addedUsers, removedUsers) = msg
    val users = scala.collection.mutable.Map[String, Option[ModelPermissions]]()
    users ++= addedUsers.mapValues(Some(_))
    removedUsers.foreach(username => users += (username -> None))
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
              _ <- persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(modelId, users.toMap)
            } yield {
              this._modelManager.foreach { m => m.reloadModelPermissions() }
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
      case cause: Throwable =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private def handleRealtimeMessage(msg: RealTimeModelMessage): Unit = {
    msg match {
      case openRequest: OpenRealtimeModelRequest =>
        modelManager.onOpenRealtimeModelRequest(openRequest, sender)
      case closeRequest: CloseRealtimeModelRequest =>
        modelManager.onCloseModelRequest(closeRequest, sender)
      case operationSubmission: OperationSubmission =>
        modelManager.onOperationSubmission(operationSubmission, sender)
      case referenceEvent: ModelReferenceEvent =>
        modelManager.onReferenceEvent(referenceEvent, sender)
    }
  }

  private[this] def modelManager: RealTimeModelManager = this._modelManager.getOrElse {
    throw new IllegalStateException("The model manager can not be access when the model is not open.")
  }

  private[this] def domainFqn = this._domainFqn.getOrElse {
    throw new IllegalStateException("Can not access domainFqn before the model is initialized.")
  }

  private[this] def modelId = this._modelId.getOrElse {
    throw new IllegalStateException("Can not access domainFqn before the model is initialized.")
  }

  private[this] def persistenceProvider = this._persistenceProvider.getOrElse {
    throw new IllegalStateException("Can not access persistenceProvider before the model is initialized.")
  }

  override protected def setIdentityData(message: ModelMessage): Try[String] = {
    this._domainFqn = Some(message.domainFqn)
    this._modelId = Some(message.modelId)
    Success(s"${message.domainFqn.namespace}/${message.domainFqn.domainId}/${message.modelId}")
  }

  override protected def initialize(msg: ModelMessage): Try[ShardedActorStatUpPlan] = {
    persistenceManager.acquirePersistenceProvider(self, context, msg.domainFqn) map { provider =>
      this._persistenceProvider = Some(provider)
      log.debug(s"${identityString}: Acquired persistence")
      this.becomeClosed()
      StartUpRequired
    } recoverWith {
      case cause: Throwable =>
        log.debug(s"${identityString}: Could not acquire persistence")
        Failure(cause)
    }
  }

  private[this] def becomeOpened(): Unit = {
    log.debug(s"${identityString}: Becoming open.")
    val persistenceFactory = new RealtimeModelPersistenceStreamFactory(
      domainFqn,
      modelId,
      context.system,
      persistenceProvider.modelStore,
      persistenceProvider.modelSnapshotStore,
      persistenceProvider.modelOperationProcessor)

    val mm = new RealTimeModelManager(
      persistenceFactory,
      new ActorBackedEventLoop(self),
      domainFqn,
      modelId,
      persistenceProvider,
      modelPermissionResolver,
      modelCreator,
      Timeout(clientDataResponseTimeout),
      context,
      new EventHandler() {
        def onInitializationError(): Unit = {
          becomeClosed();
        }
        def onClientOpened(clientActor: ActorRef): Unit = {
          context.watch(clientActor)
        }
        def onClientClosed(clientActor: ActorRef): Unit = {
          context.unwatch(clientActor)
        }
        def closeModel() = {
          becomeClosed();
        }
      })
    this._modelManager = Some(mm)
    this.context.become(receiveOpened)
    this.context.setReceiveTimeout(Duration.Undefined)
  }

  private[this] def becomeClosed(): Unit = {
    log.debug(s"${identityString}: Becoming closed.")
    this._modelManager = None
    this.context.become(receiveClosed)
    this.context.setReceiveTimeout(this.receiveTimeout)
  }

  override def passivate(): Unit = {
    this.context.setReceiveTimeout(Duration.Undefined)
    super.passivate()
  }

  //
  // Stuff to add
  //

  private[this] def onGetModelPermissions(request: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(domainFqn, modelId, sk) = request
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

  def getModel(msg: GetRealtimeModel): Unit = {
    val GetRealtimeModel(domainFqn, getModelId, sk) = msg
    log.debug(s"${identityString}: Getting model")
    (sk match {
      case Some(sk) =>
        modelPermissionResolver.getModelUserPermissions(getModelId, sk, persistenceProvider)
          .map { p => p.read }
      case None =>
        Success(true)
    }) flatMap { canRead =>
      if (canRead) {
        // FIXME we could dump this into a future to get it of the actor thread.
        persistenceProvider.modelStore.getModel(getModelId)
      } else {
        val message = "User must have 'read' permissions on the model to get it."
        Failure(UnauthorizedException(message))
      }
    } map { model =>
      sender ! model
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def createOrUpdateModel(msg: CreateOrUpdateRealtimeModel): Unit = {
    val CreateOrUpdateRealtimeModel(domainFqn, modelId, collectionId, data, overridePermissions, worldPermissions, userPermissions, sk) = msg
    persistenceProvider.modelStore.modelExists(modelId) flatMap { exists =>
      if (exists) {
        this._modelManager match {
          case Some(m) =>
            Failure(new RuntimeException("Cannot update an open model"))
          case None =>
            persistenceProvider.modelStore.updateModel(modelId, data, worldPermissions)
        }
      } else {
        modelCreator.createModel(
          persistenceProvider,
          None,
          collectionId,
          modelId,
          data,
          overridePermissions,
          worldPermissions,
          userPermissions.get)
      }
    } map { _ =>
      sender ! (())
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  private[this] def createModel(msg: CreateRealtimeModel): Unit = {
    val CreateRealtimeModel(domainFqn, modelId, collectionId, data, overridePermissions, worldPermissions, userPermissions, sk) = msg
    log.debug(s"${identityString}: Creating model.")
    if (collectionId.length == 0) {
      sender ! Status.Failure(new IllegalArgumentException("The collecitonId can not be empty when creating a model"))
    } else {
      modelCreator.createModel(
        persistenceProvider,
        sk.map(_.uid),
        collectionId,
        modelId,
        data,
        overridePermissions,
        worldPermissions,
        userPermissions) map { model =>
          log.debug(s"${identityString}: Model created.")
          sender ! model.metaData.modelId
          ()
        } recover {
          case e: DuplicateValueException =>
            sender ! Status.Failure(ModelAlreadyExistsException(modelId))
          case e: UnauthorizedException =>
            sender ! Status.Failure(e)
          case e: Exception =>
            log.error(e, s"Could not create model: ${modelId}")
            sender ! Status.Failure(e)
        }
    }
  }

  private[this] def deleteModel(deleteRequest: DeleteRealtimeModel): Unit = {
    // FIXME I don't think we need to check exists first.
    val DeleteRealtimeModel(domainFqn, modelId, sk) = deleteRequest
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        (sk match {
          case Some(sk) =>
            modelPermissionResolver.getModelUserPermissions(modelId, sk, persistenceProvider)
              .map { p => p.remove }
          case None =>
            Success(true)
        }) flatMap { canDelete =>
          if (canDelete) {
            this._modelManager.map { m =>
              m.modelDeleted()
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
      sender ! (())
      ()
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
        ()
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    this._domainFqn foreach { d =>
      persistenceManager.releasePersistenceProvider(self, context, domainFqn)
    }
  }
}

case class SessionKey(uid: String, sid: String, admin: Boolean = false) {
  def serialize(): String = s"${uid}:${sid}"
}
