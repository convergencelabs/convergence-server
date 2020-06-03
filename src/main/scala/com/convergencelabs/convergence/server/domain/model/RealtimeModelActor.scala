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

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.actor.typed.{ActorRef, Behavior, Signal, Terminated}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.convergencelabs.convergence.server.actor.{CborSerializable, ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor.OutgoingMessage
import com.convergencelabs.convergence.server.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceManager, DomainPersistenceProvider, ModelPermissions}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelManager.EventHandler
import com.convergencelabs.convergence.server.domain.model.data.ObjectValue
import com.convergencelabs.convergence.server.domain.model.ot.Operation
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserId, DomainUserSessionId, UnauthorizedException}
import com.convergencelabs.convergence.server.util.ActorBackedEventLoop
import com.convergencelabs.convergence.server.util.ActorBackedEventLoop.TaskScheduled

import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


/**
 * An instance of the RealtimeModelActor manages the lifecycle of a single
 * realtime model.
 */
class RealtimeModelActor(context: ActorContext[RealtimeModelActor.Message],
                         shardRegion: ActorRef[RealtimeModelActor.Message],
                         shard: ActorRef[ClusterSharding.ShardCommand],
                         private[this] val modelPermissionResolver: ModelPermissionResolver,
                         private[this] val modelCreator: ModelCreator,
                         private[this] val persistenceManager: DomainPersistenceManager,
                         private[this] val clientDataResponseTimeout: FiniteDuration,
                         private[this] val receiveTimeout: FiniteDuration,
                         private[this] val resyncTimeout: FiniteDuration)
  extends ShardedActor(context, shardRegion, shard) {

  import RealtimeModelActor._

  private[this] var _persistenceProvider: Option[DomainPersistenceProvider] = None
  private[this] var _domainFqn: Option[DomainId] = None
  private[this] var _modelId: Option[String] = None
  private[this] var _modelManager: Option[RealtimeModelManager] = None

  //
  // Receive methods
  //

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = super.onSignal orElse {
    case Terminated(actor) =>
      modelManager.handleTerminated(actor.unsafeUpcast[ModelClientActor.OutgoingMessage])
      Behaviors.same
  }

  protected override def receiveInitialized(msg: Message): Behavior[Message] = this.receiveClosed(msg)

  private[this] def receiveClosed(msg: Message): Behavior[Message] = {
    msg match {
      case _: ReceiveTimeout =>
        this.passivate()
      case msg: StatelessModelMessage =>
        handleStatelessMessage(msg)
      case msg@(_: OpenRealtimeModelRequest | _: ModelResyncRequest) =>
        this.becomeOpened()
        this.receiveOpened(msg)
      case _ =>
        Behaviors.unhandled
    }
  }

  private[this] def receiveOpened(msg: Message): Behavior[Message] = {
    msg match {
      case msg: StatelessModelMessage =>
        handleStatelessMessage(msg)
      case msg: RealTimeModelMessage =>
        handleRealtimeMessage(msg)
      case ExecuteTask(_, _, task) =>
        task()
        Behaviors.same
      case _ =>
        Behaviors.unhandled
    }
  }

  private[this] def handleStatelessMessage(msg: StatelessModelMessage): Behavior[Message] = {
    msg match {
      case msg: GetRealtimeModelRequest =>
        this.retrieveModel(msg)
      case msg: CreateRealtimeModelRequest =>
        this.createModel(msg)
      case msg: DeleteRealtimeModelRequest =>
        this.deleteModel(msg)
      case msg: CreateOrUpdateRealtimeModelRequest =>
        this.createOrUpdateModel(msg)
      case msg: GetModelPermissionsRequest =>
        this.retrieveModelPermissions(msg)
      case msg: SetModelPermissionsRequest =>
        this.setModelPermissions(msg)
    }

    Behaviors.same
  }

  private[this] def retrieveModelPermissions(msg: GetModelPermissionsRequest): Unit = {
    val GetModelPermissionsRequest(_, modelId, session, replyTo) = msg
    persistenceProvider.modelStore.modelExists(modelId).flatMap { exists =>
      if (exists) {
        modelPermissionResolver.getModelUserPermissions(modelId, session.userId, persistenceProvider).map(p => p.read).flatMap { canRead =>
          if (canRead) {
            modelPermissionResolver.getModelPermissions(modelId, persistenceProvider).map { p =>
              val ModelPermissionResult(overrideCollection, modelWorld, modelUsers) = p
              GetModelPermissionsResponse(Right(GetModelPermissionsSuccess(overrideCollection, modelWorld, modelUsers)))
            }
          } else {
            val message = "User must have 'read' permissions on the model to get permissions."
            Success(GetModelPermissionsResponse(Left(UnauthorizedError(message))))
          }
        }
      } else {
        Success(GetModelPermissionsResponse(Left(ModelNotFoundError())))
      }
    } match {
      case Success(response) =>
        replyTo ! response
      case Failure(cause) =>
        error("unexpected error getting model permissions", cause)
        replyTo ! GetModelPermissionsResponse(Left(UnknownError()))
    }
  }

  private[this] def setModelPermissions(msg: SetModelPermissionsRequest): Unit = {
    val SetModelPermissionsRequest(_, modelId, session, overrideWorld, world, setAllUsers, addedUsers, removedUsers, replyTo) = msg
    val users = scala.collection.mutable.Map[DomainUserId, Option[ModelPermissions]]()
    users ++= addedUsers.transform((_, v) => Some(v))
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
              _ <- if (setAllUsers) {
                persistenceProvider.modelPermissionsStore.deleteAllModelUserPermissions(modelId)
              } else {
                Success(())
              }
              _ <- persistenceProvider.modelPermissionsStore.updateAllModelUserPermissions(modelId, users.toMap)
            } yield {
              this._modelManager.foreach { m => m.reloadModelPermissions() }
              SetModelPermissionsResponse(Right(()))
            }
          } else {
            val message = "User must have 'manage' permissions on the model to set permissions"
            Success(SetModelPermissionsResponse(Left(UnauthorizedError(message))))
          }
        }
      } else {
        Success(SetModelPermissionsResponse(Left(ModelNotFoundError())))
      }
    } match {
      case Success(response) =>
        replyTo ! response
      case Failure(cause) =>
        error("unexpected error setting model permissions", cause)
        replyTo ! SetModelPermissionsResponse(Left(UnknownError()))
    }
  }

  private def handleRealtimeMessage(msg: RealTimeModelMessage): Behavior[Message] = {
    msg match {
      case openRequest: OpenRealtimeModelRequest =>
        modelManager.onOpenRealtimeModelRequest(openRequest)
      case resyncRequest: ModelResyncRequest =>
        modelManager.onModelResyncRequest(resyncRequest)
      case resyncCompleteRequest: ModelResyncClientComplete =>
        modelManager.onModelResyncClientComplete(resyncCompleteRequest)
      case closeRequest: CloseRealtimeModelRequest =>
        modelManager.onCloseModelRequest(closeRequest)
      case operationSubmission: OperationSubmission =>
        modelManager.onOperationSubmission(operationSubmission)
      case referenceEvent: ModelReferenceEvent =>
        modelManager.onReferenceEvent(referenceEvent)
    }

    Behaviors.same
  }

  private[this] def modelManager: RealtimeModelManager = this._modelManager.getOrElse {
    throw new IllegalStateException("The model manager can not be access when the model is not open.")
  }

  private[this] def domainFqn = this._domainFqn.getOrElse {
    throw new IllegalStateException("Can not access domainId before the model is initialized.")
  }

  private[this] def modelId = this._modelId.getOrElse {
    throw new IllegalStateException("Can not access modelId before the model is initialized.")
  }

  private[this] def persistenceProvider = this._persistenceProvider.getOrElse {
    throw new IllegalStateException("Can not access persistenceProvider before the model is initialized.")
  }

  override protected def setIdentityData(message: Message): Try[String] = {
    this._domainFqn = Some(message.domainId)
    this._modelId = Some(message.modelId)
    Success(s"${message.domainId.namespace}/${message.domainId.domainId}/${message.modelId}")
  }

  override protected def initialize(msg: Message): Try[ShardedActorStatUpPlan] = {
    persistenceManager.acquirePersistenceProvider(context.self, context.system, msg.domainId) map { provider =>
      this._persistenceProvider = Some(provider)
      debug(s"$identityString: Acquired persistence")
      this.becomeClosed()
      StartUpRequired
    } recoverWith {
      case cause: Throwable =>
        debug(s"$identityString: Could not acquire persistence")
        Failure(cause)
    }
  }

  private[this] def becomeOpened(): Unit = {
    debug(s"$identityString: Becoming open.")
    val persistenceFactory = new RealtimeModelPersistenceStreamFactory(
      domainFqn,
      modelId,
      context.system.toClassic,
      persistenceProvider.modelStore,
      persistenceProvider.modelSnapshotStore,
      persistenceProvider.modelOperationProcessor)

    val mm = new RealtimeModelManager(
      persistenceFactory,
      new ActorBackedEventLoop(context.messageAdapter[TaskScheduled] {
        case TaskScheduled(task) => ExecuteTask(domainFqn, modelId, task)
      }),
      domainFqn,
      modelId,
      persistenceProvider,
      modelPermissionResolver,
      modelCreator,
      Timeout(clientDataResponseTimeout),
      resyncTimeout,
      context.self,
      context.system,
      new EventHandler() {
        def onInitializationError(): Unit = {
          becomeClosed()
        }

        def onClientOpened(clientActor: ActorRef[ModelClientActor.OutgoingMessage]): Unit = {
          context.watch(clientActor)
        }

        def onClientClosed(clientActor: ActorRef[ModelClientActor.OutgoingMessage]): Unit = {
          context.unwatch(clientActor)
        }

        def closeModel(): Unit = {
          becomeClosed()
        }
      })
    this._modelManager = Some(mm)
    this.context.cancelReceiveTimeout()
  }

  private[this] def becomeClosed(): Unit = {
    debug(s"$identityString: Becoming closed.")
    this._modelManager = None

    this.context.setReceiveTimeout(this.receiveTimeout, ReceiveTimeout(this.domainFqn, this.modelId))
  }

  override def passivate(): Behavior[Message] = {
    this.context.cancelReceiveTimeout()
    super.passivate()
  }

  private[this] def retrieveModel(msg: GetRealtimeModelRequest): Unit = {
    Future {
      val GetRealtimeModelRequest(_, getModelId, session, replyTo: ActorRef[GetRealtimeModelResponse]) = msg
      (session match {
        case Some(s) =>
          modelPermissionResolver.getModelUserPermissions(getModelId, s.userId, persistenceProvider)
            .map { p => p.read }
        case None =>
          Success(true)
      }) flatMap { canRead =>
        if (canRead) {
          persistenceProvider.modelStore.getModel(getModelId)
            .map(_
              .map(m => GetRealtimeModelResponse(Right(m)))
              .getOrElse(GetRealtimeModelResponse(Left(ModelNotFoundError()))))
        } else {
          val message = "User must have 'read' permissions on the model to get it."
          Failure(UnauthorizedException(message))
        }
      } match {
        case Success(response) =>
          replyTo ! response
        case Failure(cause) =>
          error("Unexpected error getting model", cause)
          replyTo ! GetRealtimeModelResponse(Left(UnknownError()))
      }
    }(context.system.executionContext)
  }

  private[this] def createOrUpdateModel(msg: CreateOrUpdateRealtimeModelRequest): Unit = {
    val CreateOrUpdateRealtimeModelRequest(_, modelId, collectionId, data, overridePermissions, worldPermissions, userPermissions, _, replyTo) = msg
    persistenceProvider.modelStore.modelExists(modelId) flatMap { exists =>
      if (exists) {
        this._modelManager match {
          case Some(_) =>
            Success(CreateOrUpdateRealtimeModelResponse(Left(ModelOpenError())))
          case None =>
            // FIXME we really need to actually create a synthetic operation
            //   and use the model operation processor to apply it.
            persistenceProvider.modelStore.updateModel(modelId, data, worldPermissions)
              .map(_ => CreateOrUpdateRealtimeModelResponse(Right(())))

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
          .map(_ => CreateOrUpdateRealtimeModelResponse(Right(())))
      }
    } match {
      case Success(response) =>
        replyTo ! response
      case Failure(cause) =>
        error("Unexpected error creating or updating model", cause)
        replyTo ! CreateOrUpdateRealtimeModelResponse(Left(UnknownError()))
    }
  }

  private[this] def createModel(msg: CreateRealtimeModelRequest): Unit = {
    val CreateRealtimeModelRequest(_, modelId, collectionId, data, overridePermissions, worldPermissions, userPermissions, session, replyTo) = msg
    debug(s"$identityString: Creating model.")
    if (collectionId.length == 0) {
      replyTo ! CreateRealtimeModelResponse(Left(InvalidCreationDataError("The collectionId can not be empty when creating a model")))
    } else {
      modelCreator
        .createModel(
          persistenceProvider,
          session.map(_.userId),
          collectionId,
          modelId,
          data,
          overridePermissions,
          worldPermissions,
          userPermissions)
        .map { model =>
          debug(s"$identityString: Model created.")
          CreateRealtimeModelResponse(Right(model.metaData.id))
        }
        .recover {
          case _: DuplicateValueException =>
            CreateRealtimeModelResponse(Left(ModelAlreadyExistsError()))
          case e: UnauthorizedException =>
            CreateRealtimeModelResponse(Left(UnauthorizedError("Not allowed to create a model in this collection")))
          case e: Exception =>
            error(s"Could not create model: $modelId", e)
            CreateRealtimeModelResponse(Left(UnknownError()))
        }
        .foreach(replyTo ! _)
    }
  }

  private[this] def deleteModel(deleteRequest: DeleteRealtimeModelRequest): Unit = {
    // FIXME I don't think we need to check exists first.
    val DeleteRealtimeModelRequest(_, modelId, session, replyTo) = deleteRequest
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
              .map(_ => DeleteRealtimeModelResponse(Right(())))
          } else {
            val message = "User must have 'remove' permissions on the model to remove it."
            Success(DeleteRealtimeModelResponse(Left(UnauthorizedError(message))))
          }
        }
      } else {
        Success(DeleteRealtimeModelResponse(Left(ModelNotFoundError())))
      }
    } recover {
      case cause: Exception =>
        error("Unexpected error deleting model", cause)
        DeleteRealtimeModelResponse(Left(UnknownError()))
    } foreach (replyTo ! _)
  }

  override def postStop(): Unit = {
    this._domainFqn foreach { _ =>
      persistenceManager.releasePersistenceProvider(context.self, context.system, domainFqn)
    }

    super.postStop()
  }
}


/**
 * Provides a factory method for creating the RealtimeModelActor
 */
object RealtimeModelActor {
  def apply(shardRegion: ActorRef[Message],
            shard: ActorRef[ClusterSharding.ShardCommand],
            modelPermissionResolver: ModelPermissionResolver,
            modelCreator: ModelCreator,
            persistenceManager: DomainPersistenceManager,
            clientDataResponseTimeout: FiniteDuration,
            receiveTimeout: FiniteDuration,
            resyncTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup { context =>
    new RealtimeModelActor(
      context,
      shardRegion,
      shard,
      modelPermissionResolver,
      modelCreator,
      persistenceManager,
      clientDataResponseTimeout,
      receiveTimeout,
      resyncTimeout)
  }

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
    val modelId: String
  }

  case class ReceiveTimeout(domainId: DomainId, modelId: String) extends Message

  private case class ExecuteTask(domainId: DomainId, modelId: String, task: () => Unit) extends Message

  //
  // Messages that apply when the model is open or closed.
  //
  sealed trait StatelessModelMessage extends Message

  // Basic Model CRUD

  //
  // GetRealtimeModel
  case class GetRealtimeModelRequest(domainId: DomainId,
                                     modelId: String,
                                     session: Option[DomainUserSessionId],
                                     replyTo: ActorRef[GetRealtimeModelResponse]) extends StatelessModelMessage

  sealed trait GetRealtimeModelError

  case class GetRealtimeModelResponse(model: Either[GetRealtimeModelError, Model]) extends CborSerializable

  //
  // CreateOrUpdateRealtimeModel
  //
  case class CreateOrUpdateRealtimeModelRequest(domainId: DomainId,
                                                modelId: String,
                                                collectionId: String,
                                                data: ObjectValue,
                                                overridePermissions: Option[Boolean],
                                                worldPermissions: Option[ModelPermissions],
                                                userPermissions: Map[DomainUserId, ModelPermissions],
                                                session: Option[DomainUserSessionId],
                                                replyTo: ActorRef[CreateOrUpdateRealtimeModelResponse]) extends StatelessModelMessage

  sealed trait CreateOrUpdateRealtimeModelError

  case class ModelOpenError() extends CreateOrUpdateRealtimeModelError

  case class CreateOrUpdateRealtimeModelResponse(response: Either[CreateOrUpdateRealtimeModelError, Unit]) extends CborSerializable

  //
  // CreateRealtimeModel
  //
  case class CreateRealtimeModelRequest(domainId: DomainId,
                                        modelId: String,
                                        collectionId: String,
                                        data: ObjectValue,
                                        overridePermissions: Option[Boolean],
                                        worldPermissions: Option[ModelPermissions],
                                        userPermissions: Map[DomainUserId, ModelPermissions],
                                        session: Option[DomainUserSessionId],
                                        replyTo: ActorRef[CreateRealtimeModelResponse]) extends StatelessModelMessage

  sealed trait CreateRealtimeModelError

  case class InvalidCreationDataError(message: String) extends CreateRealtimeModelError

  case class CreateRealtimeModelResponse(response: Either[CreateRealtimeModelError, String]) extends CborSerializable


  //
  // DeleteRealtimeModel
  //
  case class DeleteRealtimeModelRequest(domainId: DomainId,
                                        modelId: String,
                                        session: Option[DomainUserSessionId],
                                        replyTo: ActorRef[DeleteRealtimeModelResponse]) extends StatelessModelMessage

  sealed trait DeleteRealtimeModelError

  case class DeleteRealtimeModelResponse(response: Either[DeleteRealtimeModelError, Unit]) extends CborSerializable

  //
  // GetModelPermissions
  //
  case class GetModelPermissionsRequest(domainId: DomainId,
                                        modelId: String,
                                        session: DomainUserSessionId,
                                        replyTo: ActorRef[GetModelPermissionsResponse]) extends StatelessModelMessage


  sealed trait GetModelPermissionsError

  case class GetModelPermissionsResponse(response: Either[GetModelPermissionsError, GetModelPermissionsSuccess]) extends CborSerializable

  case class GetModelPermissionsSuccess(overridesCollection: Boolean,
                                        worldPermissions: ModelPermissions,
                                        userPermissions: Map[DomainUserId, ModelPermissions])


  //
  // SetModelPermissions
  //
  case class SetModelPermissionsRequest(domainId: DomainId,
                                        modelId: String,
                                        session: DomainUserSessionId,
                                        overrideCollection: Option[Boolean],
                                        worldPermissions: Option[ModelPermissions],
                                        setAllUserPermissions: Boolean,
                                        addedUserPermissions: Map[DomainUserId, ModelPermissions],
                                        removedUserPermissions: List[DomainUserId],
                                        replyTo: ActorRef[SetModelPermissionsResponse]) extends StatelessModelMessage

  sealed trait SetModelPermissionsError

  case class SetModelPermissionsResponse(response: Either[SetModelPermissionsError, Unit]) extends CborSerializable

  //
  // Messages targeted specifically at "open" models.
  //
  sealed trait RealTimeModelMessage extends Message

  //
  // OpenRealtimeModelRequest
  //
  case class OpenRealtimeModelRequest(domainId: DomainId,
                                      modelId: String,
                                      autoCreateId: Option[Int],
                                      session: DomainUserSessionId,
                                      clientActor: ActorRef[OutgoingMessage],
                                      replyTo: ActorRef[OpenRealtimeModelResponse]) extends RealTimeModelMessage

  sealed trait OpenRealtimeModelError

  case class ClientErrorResponse(message: String) extends OpenRealtimeModelError

  case class OpenRealtimeModelResponse(response: Either[OpenRealtimeModelError, OpenModelSuccess]) extends CborSerializable

  case class OpenModelSuccess(valuePrefix: Long,
                              metaData: OpenModelMetaData,
                              connectedClients: Set[DomainUserSessionId],
                              resyncingClients: Set[DomainUserSessionId],
                              referencesBySession: Set[ReferenceState],
                              modelData: ObjectValue,
                              modelPermissions: ModelPermissions)

  //
  // CloseRealtimeModel
  //
  case class CloseRealtimeModelRequest(domainId: DomainId,
                                       modelId: String, session: DomainUserSessionId,
                                       replyTo: ActorRef[CloseRealtimeModelResponse]) extends RealTimeModelMessage

  sealed trait CloseRealtimeModelError

  case class CloseRealtimeModelResponse(response: Either[CloseRealtimeModelError, Unit]) extends CborSerializable


  //
  // ModelResync
  //
  case class ModelResyncRequest(domainId: DomainId,
                                modelId: String,
                                session: DomainUserSessionId,
                                contextVersion: Long,
                                clientActor: ActorRef[OutgoingMessage],
                                replyTo: ActorRef[ModelResyncResponse]) extends RealTimeModelMessage

  sealed trait ModelResyncError

  case class ModelResyncResponseData(currentVersion: Long, modelPermissions: ModelPermissions)

  case class ModelResyncResponse(response: Either[ModelResyncError, ModelResyncResponseData]) extends CborSerializable


  //
  // ModelResyncClientComplete
  //
  case class ModelResyncClientComplete(domainId: DomainId,
                                       modelId: String,
                                       session: DomainUserSessionId,
                                       open: Boolean) extends RealTimeModelMessage


  //
  // One one messages
  //

  case class OperationSubmission(domainId: DomainId,
                                 modelId: String,
                                 session: DomainUserSessionId,
                                 seqNo: Int,
                                 contextVersion: Long,
                                 operation: Operation) extends RealTimeModelMessage

  //
  // One Way Reference Events
  //

  sealed trait ModelReferenceEvent extends RealTimeModelMessage {
    val valueId: Option[String]
    val session: DomainUserSessionId
  }

  case class ShareReference(domainId: DomainId, modelId: String, session: DomainUserSessionId, valueId: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent

  case class SetReference(domainId: DomainId, modelId: String, session: DomainUserSessionId, valueId: Option[String], key: String, referenceType: ReferenceType.Value, values: List[Any], contextVersion: Long) extends ModelReferenceEvent

  case class ClearReference(domainId: DomainId, modelId: String, session: DomainUserSessionId, valueId: Option[String], key: String) extends ModelReferenceEvent

  case class UnshareReference(domainId: DomainId, modelId: String, session: DomainUserSessionId, valueId: Option[String], key: String) extends ModelReferenceEvent


  //
  // Common Errors
  //

  sealed trait RequestError

  case class ModelAlreadyOpenError() extends RequestError
    with OpenRealtimeModelError
    with ModelResyncError

  case class ModelAlreadyOpeningError() extends RequestError
    with OpenRealtimeModelError
    with ModelResyncError

  case class ModelNotOpenError() extends RequestError
    with CloseRealtimeModelError

  case class ModelDeletedWhileOpeningError() extends RequestError
    with OpenRealtimeModelError

  case class ModelAlreadyExistsError() extends RequestError
    with CreateRealtimeModelError

  case class ClientDataRequestError(message: String) extends RequestError
    with OpenRealtimeModelError

  case class ModelClosingAfterErrorError() extends RequestError
    with OpenRealtimeModelError
    with ModelResyncError

  case class ModelNotFoundError() extends RequestError
    with GetRealtimeModelError
    with OpenRealtimeModelError
    with ModelResyncError
    with DeleteRealtimeModelError
    with GetModelPermissionsError
    with SetModelPermissionsError

  case class UnauthorizedError(message: String) extends RequestError
    with GetRealtimeModelError
    with OpenRealtimeModelError
    with CreateOrUpdateRealtimeModelError
    with CreateRealtimeModelError
    with ModelResyncError
    with DeleteRealtimeModelError
    with GetModelPermissionsError
    with SetModelPermissionsError

  case class UnknownError() extends RequestError
    with GetRealtimeModelError
    with OpenRealtimeModelError
    with CreateOrUpdateRealtimeModelError
    with CreateRealtimeModelError
    with DeleteRealtimeModelError
    with GetModelPermissionsError
    with SetModelPermissionsError
    with ModelResyncError

}
