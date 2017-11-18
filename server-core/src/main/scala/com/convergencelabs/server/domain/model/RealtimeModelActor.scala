package com.convergencelabs.server.domain.model

import java.lang.{ Long => JavaLong }
import java.time.Instant

import scala.collection.immutable.HashMap
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.ot.OperationTransformer
import com.convergencelabs.server.domain.model.ot.ServerConcurrencyControl
import com.convergencelabs.server.domain.model.ot.TransformationFunctionRegistry
import com.convergencelabs.server.domain.model.ot.UnprocessedOperationEvent
import com.convergencelabs.server.domain.model.ot.xform.ReferenceTransformer

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.Status
import akka.actor.Terminated
import akka.pattern.AskTimeoutException
import akka.pattern.Patterns
import com.convergencelabs.server.domain.model.RealTimeModelManager.EventHandler
import com.convergencelabs.server.datastore.domain.DomainPersistenceManagerActor
import scala.util.control.NonFatal
import scala.concurrent.duration.Duration
import akka.cluster.sharding.ShardRegion.Passivate
import akka.actor.ReceiveTimeout
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.domain.ModelDataGenerator
import akka.util.Timeout
import scala.concurrent.duration.FiniteDuration

case class ModelConfigResponse(sk: SessionKey, config: ClientAutoCreateModelConfigResponse)
case object PermissionsUpdated
case class ClientOpenFailure(sk: SessionKey, response: AnyRef)

/**
 * Provides a factory method for creating the RealtimeModelActor
 */
object RealtimeModelActor {
  def props(
    modelPemrissionResolver: ModelPermissionResolver,
    modelCreator: ModelCreator,
    clientDataResponseTimeout: FiniteDuration,
    receiveTimeout: FiniteDuration): Props =
    Props(new RealtimeModelActor(
      modelPemrissionResolver,
      modelCreator,
      clientDataResponseTimeout,
      receiveTimeout))

  case object ModelShutdown
  case class OperationCommitted(version: Long)
  case class ForceClose(reason: String)
  case object StreamCompleted
  case object StreamFailure

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
  private[this] val clientDataResponseTimeout: FiniteDuration,
  private[this] val receiveTimeout: FiniteDuration)
    extends Actor
    with ActorLogging {

  import RealtimeModelActor._

  private[this] var _persistenceProvider: Option[DomainPersistenceProvider] = None
  private[this] var _domainFqn: Option[DomainFqn] = None
  private[this] var _modelId: Option[String] = None
  private[this] var _modelManager: Option[RealTimeModelManager] = None

  //
  // Receive methods
  //

  def receive: Receive = receiveUninitialized

  private[this] def receiveUninitialized: Receive = {
    case msg: ModelMessage =>
      initialize(msg).map(_ => receiveClosed(msg))
    case unknown: Any =>
      unhandled(unknown)
  }

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

    case terminated: Terminated =>
      modelManager.handleTerminated(terminated)
    case ModelShutdown =>
      shutdown()
    case OperationCommitted(version) =>
      modelManager.commitVersion(version)
    case StreamFailure =>
      modelManager.forceCloseAllAfterError("There was an unexpected persitence error.")
    case dataResponse: ModelConfigResponse =>
      modelManager.onClientAutoCreateModelConfigResponse(dataResponse)

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

  private[this] def receivePassivating: Receive = {
    case msg: ReceiveTimeout =>
    // ignore
    case msg: RealTimeModelMessage =>
      // Forward this back to the shard region, it will be handled by the next actor that is stood up.
      this.context.parent.forward(msg)
    case msg: Any =>
      unhandled(msg)
  }

  private[this] def receiveShuttingDown: Receive = {
    case StreamCompleted =>
      this.context.stop(self)
    case StreamFailure =>
      this.context.stop(self)
    case unknown: Any =>
      unhandled(unknown)
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

  private[this] def initialize(msg: ModelMessage): Try[Unit] = {
    log.debug(s"Real Time Model Actor initializing: '{}/{}'", msg.domainFqn, msg.modelId)
    DomainPersistenceManagerActor.acquirePersistenceProvider(self, context, domainFqn) map { provider =>
      this._persistenceProvider = Some(provider)
      this._domainFqn = Some(msg.domainFqn)
      this._modelId = Some(msg.modelId)
      log.debug(s"Real Time Model Actor aquired persistence: '{}/{}'", domainFqn, modelId)
      context.become(receiveClosed)
      ()
    } recoverWith {
      case NonFatal(cause) =>
        log.debug(s"Error initializing Real Time Model Actor: '{}/{}'", domainFqn, modelId)
        Failure(cause)
    }
  }

  private def shutdown(): Unit = {
    log.debug(s"Model is shutting down: ${domainFqn}/${modelId}")
    this.modelManager.shutdown()
    this.context.become(receiveShuttingDown)
  }

  override def postStop(): Unit = {
    log.debug("Realtime Model stopped: {}/{}", domainFqn, modelId)
  }

  private[this] def becomeOpened(): Unit = {
    log.debug("Model '{}/{}' becoming open.", domainFqn, modelId)
    val mm = new RealTimeModelManager(
      self,
      domainFqn,
      modelId,
      persistenceProvider,
      modelPermissionResolver,
      Timeout(clientDataResponseTimeout),
      context,
      new EventHandler {
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
    log.debug("Model '{}/{}' becoming closed.", domainFqn, modelId)
    this._modelManager = None
    this.context.become(receiveClosed)
    this.context.setReceiveTimeout(this.receiveTimeout)
  }

  private[this] def passivate(): Unit = {
    log.debug("Model '{}/{}' passivating.", modelId, domainFqn)
    this.context.parent ! Passivate
    this.context.setReceiveTimeout(Duration.Undefined)
    this.context.become(receivePassivating)
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

  private[this] def onSetModelPermissions(request: SetModelPermissionsRequest): Unit = {
    val SetModelPermissionsRequest(domainFqn, modelId, sk, overrideCollection, world, setAllUsers, users) = request
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
              this._modelManager.map(_.reloadModelPermissions())
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

  def getModel(msg: GetRealtimeModel): Unit = {
    val GetRealtimeModel(domainFqn, modelId, sk) = msg
    // FIXME look at the real time. look at the sk.
    persistenceProvider.modelStore.getModel(modelId) map { result =>
      sender ! result
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
            Failure(new RuntimeException("Can not update an open model"))
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
          userPermissions)
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
    if (collectionId.length == 0) {
      sender ! Status.Failure(new IllegalArgumentException("The collecitonId can not be empty when creating a model"))
    } else {
      modelCreator.createModel(
        persistenceProvider,
        sk.map(_.uid),
        collectionId,
        modelId,
        ModelDataGenerator(data),
        overridePermissions,
        worldPermissions,
        userPermissions) map { model =>
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
}

case class SessionKey(uid: String, sid: String, admin: Boolean = false) {
  def serialize(): String = s"${uid}:${sid}"
}
