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

package com.convergencelabs.convergence.server.domain.model

import akka.actor.{ActorRef, Props, ReceiveTimeout, Status, Terminated}
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.{ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceManager, DomainPersistenceProvider, ModelPermissions}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelManager.EventHandler
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, UnauthorizedException}
import com.convergencelabs.convergence.server.util.ActorBackedEventLoop
import com.convergencelabs.convergence.server.util.ActorBackedEventLoop.TaskScheduled

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}

/**
  * Provides a factory method for creating the RealtimeModelActor
  */
object RealtimeModelActor {
  def props(
             modelPermissionResolver: ModelPermissionResolver,
             modelCreator: ModelCreator,
             persistenceManager: DomainPersistenceManager,
             clientDataResponseTimeout: FiniteDuration,
             receiveTimeout: FiniteDuration): Props =
    Props(new RealtimeModelActor(
      modelPermissionResolver,
      modelCreator,
      persistenceManager,
      clientDataResponseTimeout,
      receiveTimeout))
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

  private[this] var _persistenceProvider: Option[DomainPersistenceProvider] = None
  private[this] var _domainFqn: Option[DomainId] = None
  private[this] var _modelId: Option[String] = None
  private[this] var _modelManager: Option[RealtimeModelManager] = None

  //
  // Receive methods
  //

  protected override def receiveInitialized: Receive = this.receiveClosed

  private[this] def receiveClosed: Receive = {
    case _: ReceiveTimeout =>
      this.passivate()
    case msg: StatelessModelMessage =>
      handleStatelessMessage(msg)
    case msg @ (_: OpenRealtimeModelRequest | _:ModelReconnectRequest) =>
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

  private[this] def handleStatelessMessage(msg: StatelessModelMessage): Unit = {
    msg match {
      case msg: GetRealtimeModel =>
        this.retrieveModel(msg)
      case msg: CreateRealtimeModel =>
        this.createModel(msg)
      case msg: DeleteRealtimeModel =>
        this.deleteModel(msg)
      case msg: CreateOrUpdateRealtimeModel =>
        this.createOrUpdateModel(msg)
      case msg: GetModelPermissionsRequest =>
        this.retrieveModelPermissions(msg)
      case msg: SetModelPermissionsRequest =>
        this.setModelPermissions(msg)
    }
  }

  private[this] def retrieveModelPermissions(msg: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(_, modelId, session) = msg
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        modelPermissionResolver.getModelUserPermissions(modelId, session.userId, persistenceProvider).map(p => p.read).flatMap { canRead =>
          if (canRead) {
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
    val SetModelPermissionsRequest(_, modelId, session, overrideWorld, world, setAllUsers, addedUsers, removedUsers) = msg
    val users = scala.collection.mutable.Map[DomainUserId, Option[ModelPermissions]]()
    users ++= addedUsers.mapValues(Some(_))
    removedUsers.foreach(username => users += (username -> None))
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        modelPermissionResolver.getModelUserPermissions(modelId, session.userId, persistenceProvider).map(p => p.manage).flatMap { canSet =>
          if (canSet) {
            for {
              _ <- overrideWorld match {
                case Some(ov) => persistenceProvider.modelPermissionsStore.setOverrideCollectionPermissions(modelId, ov)
                case None => Success(())
              }
              _ <- world match {
                case Some(perms) => persistenceProvider.modelPermissionsStore.setModelWorldPermissions(modelId, perms)
                case None => Success(())
              }
              _ <- if(setAllUsers) {
                persistenceProvider.modelPermissionsStore.deleteAllModelUserPermissions(modelId)
              } else {
                Success(())
              }
              _ <- persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(modelId, users.toMap)
            } yield {
              this._modelManager.foreach { m => m.reloadModelPermissions() }
              ()
            }
          } else {
            Failure(UnauthorizedException("User must have 'manage' permissions on the model to set permissions"))
          }
        }
      } else {
        Failure(ModelNotFoundException(modelId))
      }
    } map { _ =>
      sender ! ((): Unit)
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
      case reconnectRequest: ModelReconnectRequest =>
        modelManager.onModelReconnectRequest(reconnectRequest, sender)
      case closeRequest: CloseRealtimeModelRequest =>
        modelManager.onCloseModelRequest(closeRequest, sender)
      case operationSubmission: OperationSubmission =>
        modelManager.onOperationSubmission(operationSubmission, sender)
      case referenceEvent: ModelReferenceEvent =>
        modelManager.onReferenceEvent(referenceEvent, sender)
    }
  }

  private[this] def modelManager: RealtimeModelManager = this._modelManager.getOrElse {
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
    this._domainFqn = Some(message.domainId)
    this._modelId = Some(message.modelId)
    Success(s"${message.domainId.namespace}/${message.domainId.domainId}/${message.modelId}")
  }

  override protected def initialize(msg: ModelMessage): Try[ShardedActorStatUpPlan] = {
    persistenceManager.acquirePersistenceProvider(self, context, msg.domainId) map { provider =>
      this._persistenceProvider = Some(provider)
      log.debug(s"$identityString: Acquired persistence")
      this.becomeClosed()
      StartUpRequired
    } recoverWith {
      case cause: Throwable =>
        log.debug(s"$identityString: Could not acquire persistence")
        Failure(cause)
    }
  }

  private[this] def becomeOpened(): Unit = {
    log.debug(s"$identityString: Becoming open.")
    val persistenceFactory = new RealtimeModelPersistenceStreamFactory(
      domainFqn,
      modelId,
      context.system,
      persistenceProvider.modelStore,
      persistenceProvider.modelSnapshotStore,
      persistenceProvider.modelOperationProcessor)

    val mm = new RealtimeModelManager(
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
          becomeClosed()
        }

        def onClientOpened(clientActor: ActorRef): Unit = {
          context.watch(clientActor)
        }

        def onClientClosed(clientActor: ActorRef): Unit = {
          context.unwatch(clientActor)
        }

        def closeModel(): Unit = {
          becomeClosed()
        }
      })
    this._modelManager = Some(mm)
    this.context.become(receiveOpened)
    this.context.setReceiveTimeout(Duration.Undefined)
  }

  private[this] def becomeClosed(): Unit = {
    log.debug(s"$identityString: Becoming closed.")
    this._modelManager = None
    this.context.become(receiveClosed)
    this.context.setReceiveTimeout(this.receiveTimeout)
  }

  override def passivate(): Unit = {
    this.context.setReceiveTimeout(Duration.Undefined)
    super.passivate()
  }

  private[this] def retrieveModel(msg: GetRealtimeModel): Unit = {
    val GetRealtimeModel(_, getModelId, session) = msg
    log.debug(s"$identityString: Getting model")
    (session match {
      case Some(s) =>
        modelPermissionResolver.getModelUserPermissions(getModelId, s.userId, persistenceProvider)
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
    val CreateOrUpdateRealtimeModel(_, modelId, collectionId, data, overridePermissions, worldPermissions, userPermissions, _) = msg
    persistenceProvider.modelStore.modelExists(modelId) flatMap { exists =>
      if (exists) {
        this._modelManager match {
          case Some(_) =>
            Failure(new RuntimeException("Cannot update an open model"))
          case None =>
            // FIXME we really need to actually create a synthetic operation
            //   and use the model operation processor to apply it.
            persistenceProvider.modelStore.updateModel(modelId, data, worldPermissions)

            // FIXME permissions
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
    }
  }

  private[this] def createModel(msg: CreateRealtimeModel): Unit = {
    val CreateRealtimeModel(_, modelId, collectionId, data, overridePermissions, worldPermissions, userPermissions, session) = msg
    log.debug(s"$identityString: Creating model.")
    if (collectionId.length == 0) {
      sender ! Status.Failure(new IllegalArgumentException("The collectionId can not be empty when creating a model"))
    } else {
      modelCreator.createModel(
        persistenceProvider,
        session.map(_.userId),
        collectionId,
        modelId,
        data,
        overridePermissions,
        worldPermissions,
        userPermissions) map { model =>
        log.debug(s"$identityString: Model created.")
        sender ! model.metaData.id
        ()
      } recover {
        case _: DuplicateValueException =>
          sender ! Status.Failure(ModelAlreadyExistsException(modelId))
        case e: UnauthorizedException =>
          sender ! Status.Failure(e)
        case e: Exception =>
          log.error(e, s"Could not create model: $modelId")
          sender ! Status.Failure(e)
      }
    }
  }

  private[this] def deleteModel(deleteRequest: DeleteRealtimeModel): Unit = {
    // FIXME I don't think we need to check exists first.
    val DeleteRealtimeModel(_, modelId, session) = deleteRequest
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        (session match {
          case Some(s) =>
            modelPermissionResolver.getModelUserPermissions(modelId, s.userId, persistenceProvider)
              .map { p => p.remove }
          case None =>
            Success(true)
        }) flatMap { canDelete =>
          if (canDelete) {
            this._modelManager.foreach(_.modelDeleted())
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
    } recover {
      case cause: Exception =>
        sender ! Status.Failure(cause)
    }
  }

  override def postStop(): Unit = {
    super.postStop()
    this._domainFqn foreach { _ =>
      persistenceManager.releasePersistenceProvider(self, context, domainFqn)
    }
  }
}
