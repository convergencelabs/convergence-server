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

package com.convergencelabs.convergence.server.backend.services.domain.model

import akka.Done
import akka.actor.CoordinatedShutdown
import akka.actor.typed._
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.{ActorContext, Behaviors}
import akka.cluster.sharding.typed.scaladsl.ClusterSharding
import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor.OutgoingMessage
import com.convergencelabs.convergence.server.backend.datastore.DuplicateValueException
import com.convergencelabs.convergence.server.backend.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.backend.services.domain.model.RealtimeModelManager.EventHandler
import com.convergencelabs.convergence.server.backend.services.domain.model.ot.Operation
import com.convergencelabs.convergence.server.backend.services.domain.{DomainPersistenceManager, UnauthorizedException}
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.domain.model._
import com.convergencelabs.convergence.server.model.domain.session.DomainSessionAndUserId
import com.convergencelabs.convergence.server.model.domain.user.DomainUserId
import com.convergencelabs.convergence.server.util.ActorBackedEventLoop
import com.convergencelabs.convergence.server.util.ActorBackedEventLoop.TaskScheduled
import com.convergencelabs.convergence.server.util.actor.{ShardedActor, ShardedActorStatUpPlan, StartUpRequired}
import com.convergencelabs.convergence.server.util.serialization.akka.CborSerializable
import com.fasterxml.jackson.annotation.{JsonSubTypes, JsonTypeInfo}

import java.time.Instant
import java.util.concurrent.TimeUnit
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.{Failure, Success, Try}


/**
 * An instance of the RealtimeModelActor manages the lifecycle of a single
 * realtime model.
 */
private final class RealtimeModelActor(context: ActorContext[RealtimeModelActor.Message],
                                       shardRegion: ActorRef[RealtimeModelActor.Message],
                                       shard: ActorRef[ClusterSharding.ShardCommand],
                                       modelPermissionResolver: ModelPermissionResolver,
                                       modelCreator: ModelCreator,
                                       persistenceManager: DomainPersistenceManager,
                                       clientDataResponseTimeout: FiniteDuration,
                                       receiveTimeout: FiniteDuration)
  extends ShardedActor(context, shardRegion, shard) {

  import RealtimeModelActor._

  private[this] var _persistenceProvider: Option[DomainPersistenceProvider] = None
  private[this] var _domainId: Option[DomainId] = None
  private[this] var _modelId: Option[String] = None
  private[this] var _modelManager: Option[RealtimeModelManager] = None

  private[this] var _open: Boolean = false
  private[this] val _shutdownTask = {
    val self = context.self
    implicit val scheduler: Scheduler = context.system.scheduler
    CoordinatedShutdown(context.system)
      .addCancellableTask(CoordinatedShutdown.PhaseServiceUnbind, "RealtimeModelShutdown") { () =>
        debug("RealtimeModelActor executing coordinated shutdown: " + this.modelId)
        implicit val t = Timeout(5, TimeUnit.SECONDS)
        self.ask[Done](r => ShutdownRequest(this.domainId, this.modelId, r))
      }
  }

  //
  // Receive methods
  //

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = super.onSignal orElse {
    case Terminated(actor) =>
      modelManager.handleTerminated(actor.unsafeUpcast[ModelClientActor.OutgoingMessage])
      Behaviors.same
    case PostStop =>
      this._shutdownTask.cancel()
      Behaviors.same
  }

  protected override def receiveInitialized(msg: Message): Behavior[Message] = {
    if (_open) {
      receiveOpened(msg)
    } else {
      receiveClosed(msg)
    }
  }

  private[this] def receiveClosed(msg: Message): Behavior[Message] = {
    msg match {
      case _: ReceiveTimeout =>
        this.passivate()
      case msg: StatelessModelMessage =>
        handleStatelessMessage(msg)
      case msg@(_: OpenRealtimeModelRequest | _: ModelResyncRequest) =>
        this.becomeOpened()
        this.receiveOpened(msg)
      case msg: ShutdownRequest =>
        this.handleShutdownMessage(msg)
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
      case msg: ShutdownRequest =>
        this.handleShutdownMessage(msg)
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
              val ResolvedModelPermission(overrideCollection, modelWorld, modelUsers) = p
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
              SetModelPermissionsResponse(Right(Ok()))
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

  private def handleShutdownMessage(msg: ShutdownRequest): Behavior[Message] = {
    this._modelManager.foreach(mm => mm.coordinatedShutdown())
    msg.replyTo ! Done
    Behaviors.stopped
  }

  private[this] def modelManager: RealtimeModelManager = this._modelManager.getOrElse {
    throw new IllegalStateException("The model manager can not be access when the model is not open.")
  }

  private[this] def domainId = this._domainId.getOrElse {
    throw new IllegalStateException("Can not access domainId before the model is initialized.")
  }

  private[this] def modelId = this._modelId.getOrElse {
    throw new IllegalStateException("Can not access modelId before the model is initialized.")
  }

  private[this] def persistenceProvider = this._persistenceProvider.getOrElse {
    throw new IllegalStateException("Can not access persistenceProvider before the model is initialized.")
  }

  override protected def setIdentityData(message: Message): Try[String] = {
    this._domainId = Some(message.domainId)
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
      domainId,
      modelId,
      context.system,
      persistenceProvider.modelStore,
      persistenceProvider.modelSnapshotStore,
      persistenceProvider.modelOperationProcessor)

    val mm = new RealtimeModelManager(
      persistenceFactory,
      new ActorBackedEventLoop(context.messageAdapter[TaskScheduled] {
        case TaskScheduled(task) => ExecuteTask(domainId, modelId, task)
      }),
      domainId,
      modelId,
      persistenceProvider,
      modelPermissionResolver,
      modelCreator,
      Timeout(clientDataResponseTimeout),
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
    this._open = true
  }

  private[this] def becomeClosed(): Unit = {
    debug(s"$identityString: Becoming closed.")
    this._modelManager = None
    this._open = false
    this.context.setReceiveTimeout(this.receiveTimeout, ReceiveTimeout(this.domainId, this.modelId))
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
              .map(_ => CreateOrUpdateRealtimeModelResponse(Right(Ok())))

          // FIXME permissions
        }
      } else {
        modelCreator.createModel(
          persistenceProvider,
          None,
          collectionId,
          modelId,
          data,
          None,
          overridePermissions,
          worldPermissions,
          userPermissions)
          .map(_ => CreateOrUpdateRealtimeModelResponse(Right(Ok())))
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
    val CreateRealtimeModelRequest(_, modelId, collectionId, data, createdTime, overridePermissions, worldPermissions, userPermissions, session, replyTo) = msg
    debug(s"$identityString: Creating model.")
    if (collectionId.isEmpty) {
      replyTo ! CreateRealtimeModelResponse(Left(InvalidCreationDataError("The collectionId can not be empty when creating a model")))
    } else {
      modelCreator
        .createModel(
          persistenceProvider,
          session.map(_.userId),
          collectionId,
          modelId,
          data,
          createdTime,
          overridePermissions,
          worldPermissions,
          userPermissions)
        .map { model =>
          debug(s"$identityString: Model created.")
          Right(model.metaData.id)
        }
        .recover {
          case _: DuplicateValueException =>
            // TODO improve model fingerprint logic when fingerprint added
            //  to the database.
            val fingerprint: Option[String] = this.persistenceProvider.modelStore.getModelMetaData(modelId)
              .map(_.map(_.createdTime.toEpochMilli.toString)).getOrElse(None)
            Left(ModelAlreadyExistsError(fingerprint))
          case e: UnauthorizedException =>
            Left(UnauthorizedError(e.getMessage))
          case e: CollectionAutoCreateDisabledException =>
            Left(CollectionDoesNotExistError(e.message))
          case e: Exception =>
            error(s"Could not create model: $modelId", e)
            Left(UnknownError())
        }
        .foreach(replyTo ! CreateRealtimeModelResponse(_))
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
              .map(_ => DeleteRealtimeModelResponse(Right(Ok())))
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
    this._domainId foreach { _ =>
      persistenceManager.releasePersistenceProvider(context.self, context.system, domainId)
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
            receiveTimeout: FiniteDuration): Behavior[Message] = Behaviors.setup[Message] { context =>
    new RealtimeModelActor(
      context,
      shardRegion,
      shard,
      modelPermissionResolver,
      modelCreator,
      persistenceManager,
      clientDataResponseTimeout,
      receiveTimeout)
  }.narrow[Message]

  /////////////////////////////////////////////////////////////////////////////
  // Message Protocol
  /////////////////////////////////////////////////////////////////////////////

  sealed trait Message extends CborSerializable {
    val domainId: DomainId
    val modelId: String
  }

  private case class ShutdownRequest(domainId: DomainId, modelId: String, replyTo: ActorRef[Done]) extends Message

  final case class ReceiveTimeout(domainId: DomainId, modelId: String) extends Message

  private final case class ExecuteTask(domainId: DomainId, modelId: String, task: () => Unit) extends Message

  //
  // Messages that apply when the model is open or closed.
  //
  sealed trait StatelessModelMessage extends Message

  // Basic Model CRUD

  //
  // GetRealtimeModel
  //
  final case class GetRealtimeModelRequest(domainId: DomainId,
                                           modelId: String,
                                           session: Option[DomainSessionAndUserId],
                                           replyTo: ActorRef[GetRealtimeModelResponse]) extends StatelessModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetRealtimeModelError

  final case class GetRealtimeModelResponse(model: Either[GetRealtimeModelError, Model]) extends CborSerializable

  //
  // CreateOrUpdateRealtimeModel
  //
  final case class CreateOrUpdateRealtimeModelRequest(domainId: DomainId,
                                                      modelId: String,
                                                      collectionId: String,
                                                      data: ObjectValue,
                                                      overridePermissions: Option[Boolean],
                                                      worldPermissions: Option[ModelPermissions],
                                                      userPermissions: Map[DomainUserId, ModelPermissions],
                                                      session: Option[DomainSessionAndUserId],
                                                      replyTo: ActorRef[CreateOrUpdateRealtimeModelResponse]) extends StatelessModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelOpenError], name = "model_open"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CreateOrUpdateRealtimeModelError

  final case class ModelOpenError() extends CreateOrUpdateRealtimeModelError

  final case class CreateOrUpdateRealtimeModelResponse(response: Either[CreateOrUpdateRealtimeModelError, Ok]) extends CborSerializable


  //
  // CreateRealtimeModel
  //
  final case class CreateRealtimeModelRequest(domainId: DomainId,
                                              modelId: String,
                                              collectionId: String,
                                              data: ObjectValue,
                                              createdTime: Option[Instant],
                                              overridePermissions: Option[Boolean],
                                              worldPermissions: Option[ModelPermissions],
                                              userPermissions: Map[DomainUserId, ModelPermissions],
                                              session: Option[DomainSessionAndUserId],
                                              replyTo: ActorRef[CreateRealtimeModelResponse]) extends StatelessModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelAlreadyExistsError], name = "model_exists"),
    new JsonSubTypes.Type(value = classOf[InvalidCreationDataError], name = "invalid_data"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown"),
    new JsonSubTypes.Type(value = classOf[CollectionDoesNotExistError], name = "no_collection")
  ))
  sealed trait CreateRealtimeModelError

  final case class InvalidCreationDataError(message: String) extends CreateRealtimeModelError

  final case class CollectionDoesNotExistError(message: String) extends CreateRealtimeModelError

  final case class CreateRealtimeModelResponse(response: Either[CreateRealtimeModelError, String]) extends CborSerializable


  //
  // DeleteRealtimeModel
  //
  final case class DeleteRealtimeModelRequest(domainId: DomainId,
                                              modelId: String,
                                              session: Option[DomainSessionAndUserId],
                                              replyTo: ActorRef[DeleteRealtimeModelResponse]) extends StatelessModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait DeleteRealtimeModelError

  final case class DeleteRealtimeModelResponse(response: Either[DeleteRealtimeModelError, Ok]) extends CborSerializable

  //
  // GetModelPermissions
  //
  final case class GetModelPermissionsRequest(domainId: DomainId,
                                              modelId: String,
                                              session: DomainSessionAndUserId,
                                              replyTo: ActorRef[GetModelPermissionsResponse]) extends StatelessModelMessage


  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait GetModelPermissionsError

  final case class GetModelPermissionsResponse(response: Either[GetModelPermissionsError, GetModelPermissionsSuccess]) extends CborSerializable

  final case class GetModelPermissionsSuccess(overridesCollection: Boolean,
                                              worldPermissions: ModelPermissions,
                                              userPermissions: Map[DomainUserId, ModelPermissions])


  //
  // SetModelPermissions
  //
  final case class SetModelPermissionsRequest(domainId: DomainId,
                                              modelId: String,
                                              session: DomainSessionAndUserId,
                                              overrideCollection: Option[Boolean],
                                              worldPermissions: Option[ModelPermissions],
                                              setAllUserPermissions: Boolean,
                                              addedUserPermissions: Map[DomainUserId, ModelPermissions],
                                              removedUserPermissions: List[DomainUserId],
                                              replyTo: ActorRef[SetModelPermissionsResponse]) extends StatelessModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait SetModelPermissionsError

  final case class SetModelPermissionsResponse(response: Either[SetModelPermissionsError, Ok]) extends CborSerializable

  //
  // Messages targeted specifically at "open" models.
  //
  sealed trait RealTimeModelMessage extends Message

  //
  // OpenRealtimeModelRequest
  //
  final case class OpenRealtimeModelRequest(domainId: DomainId,
                                            modelId: String,
                                            autoCreateId: Option[Int],
                                            session: DomainSessionAndUserId,
                                            clientActor: ActorRef[OutgoingMessage],
                                            replyTo: ActorRef[OpenRealtimeModelResponse]) extends RealTimeModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ClientDataRequestError], name = "client_data_error"),
    new JsonSubTypes.Type(value = classOf[ClientErrorResponse], name = "client_error"),
    new JsonSubTypes.Type(value = classOf[ModelAlreadyOpenError], name = "already_open"),
    new JsonSubTypes.Type(value = classOf[ModelAlreadyOpeningError], name = "already_opening"),
    new JsonSubTypes.Type(value = classOf[ModelClosingAfterErrorError], name = "closing_after_error"),
    new JsonSubTypes.Type(value = classOf[ModelDeletedWhileOpeningError], name = "deleted"),
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait OpenRealtimeModelError

  final case class ClientErrorResponse(message: String) extends OpenRealtimeModelError

  final case class OpenRealtimeModelResponse(response: Either[OpenRealtimeModelError, OpenModelSuccess]) extends CborSerializable

  final case class OpenModelSuccess(valuePrefix: Long,
                                    metaData: OpenModelMetaData,
                                    connectedClients: Set[DomainSessionAndUserId],
                                    resyncingClients: Set[DomainSessionAndUserId],
                                    referencesBySession: Set[ReferenceState],
                                    modelData: ObjectValue,
                                    modelPermissions: ModelPermissions)

  final case class OpenModelMetaData(id: String,
                                     collection: String,
                                     version: Long,
                                     createdTime: Instant,
                                     modifiedTime: Instant)

  //
  // CloseRealtimeModel
  //
  final case class CloseRealtimeModelRequest(domainId: DomainId,
                                             modelId: String,
                                             session: DomainSessionAndUserId,
                                             replyTo: ActorRef[CloseRealtimeModelResponse]) extends RealTimeModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelNotOpenError], name = "not_open"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait CloseRealtimeModelError

  final case class CloseRealtimeModelResponse(response: Either[CloseRealtimeModelError, Ok]) extends CborSerializable


  //
  // ModelResync
  //
  final case class ModelResyncRequest(domainId: DomainId,
                                      modelId: String,
                                      session: DomainSessionAndUserId,
                                      contextVersion: Long,
                                      clientActor: ActorRef[OutgoingMessage],
                                      replyTo: ActorRef[ModelResyncResponse]) extends RealTimeModelMessage

  @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
  @JsonSubTypes(Array(
    new JsonSubTypes.Type(value = classOf[ModelAlreadyOpenError], name = "already_open"),
    new JsonSubTypes.Type(value = classOf[ModelAlreadyOpeningError], name = "already_opening"),
    new JsonSubTypes.Type(value = classOf[ModelClosingAfterErrorError], name = "closing_after_error"),
    new JsonSubTypes.Type(value = classOf[ModelNotFoundError], name = "not_found"),
    new JsonSubTypes.Type(value = classOf[UnauthorizedError], name = "unauthorized"),
    new JsonSubTypes.Type(value = classOf[UnknownError], name = "unknown")
  ))
  sealed trait ModelResyncError

  final case class ModelResyncResponseData(currentVersion: Long, modelPermissions: ModelPermissions)

  final case class ModelResyncResponse(response: Either[ModelResyncError, ModelResyncResponseData]) extends CborSerializable


  //
  // ModelResyncClientComplete
  //
  final case class ModelResyncClientComplete(domainId: DomainId,
                                             modelId: String,
                                             session: DomainSessionAndUserId,
                                             open: Boolean) extends RealTimeModelMessage


  //
  // One one messages
  //

  final case class OperationSubmission(domainId: DomainId,
                                       modelId: String,
                                       session: DomainSessionAndUserId,
                                       seqNo: Int,
                                       contextVersion: Long,
                                       operation: Operation) extends RealTimeModelMessage

  //
  // One Way Reference Events
  //

  /**
   * Trait for messages relating to references on this model.
   */
  sealed trait ModelReferenceEvent extends RealTimeModelMessage {
    val valueId: Option[String]
    val session: DomainSessionAndUserId
  }

  /**
   * Indicates that a session has shared a new reference related to a model.
   *
   * @param domainId       The id of the domain the model belongs to.
   * @param modelId        The id of the model the reference
   * @param session        The id of the session that shared the reference.
   * @param valueId        The value the reference relates to, or None if it
   *                       relates to the model itself.
   * @param key            The unique key that identifies this reference. The
   *                       key is unique for the model, session, and
   *                       potentially the element.
   * @param values         The current value of the reference.
   * @param contextVersion The client's current context version for the model
   *                       the reference is part of.
   */
  final case class ShareReference(domainId: DomainId,
                                  modelId: String,
                                  session: DomainSessionAndUserId,
                                  valueId: Option[String],
                                  key: String,
                                  values: ModelReferenceValues,
                                  contextVersion: Long) extends ModelReferenceEvent

  /**
   * Indicates that a session has set an existing reference related to a model.
   *
   * @param domainId       The id of the domain the model belongs to.
   * @param modelId        The id of the model the reference
   * @param session        The id of the session that shared the reference.
   * @param valueId        The value the reference relates to, or None if it
   *                       relates to the model itself.
   * @param key            The unique key that identifies this reference. The
   *                       key is unique for the model, session, and
   *                       potentially the element.
   * @param values         The current value of the reference.
   * @param contextVersion The client's current context version for the model
   *                       the reference is part of.
   */
  final case class SetReference(domainId: DomainId,
                                modelId: String,
                                session: DomainSessionAndUserId,
                                valueId: Option[String],
                                key: String,
                                values: ModelReferenceValues,
                                contextVersion: Long) extends ModelReferenceEvent

  /**
   * Indicates that a session has cleared an existing reference related to a
   * model.
   *
   * @param domainId The id of the domain the model belongs to.
   * @param modelId  The id of the model the reference
   * @param session  The id of the session that shared the reference.
   * @param valueId  The value the reference relates to, or None if it
   *                 relates to the model itself.
   * @param key      The unique key that identifies this reference. The
   *                 key is unique for the model, session, and
   *                 potentially the element.
   */
  final case class ClearReference(domainId: DomainId, modelId: String,
                                  session: DomainSessionAndUserId,
                                  valueId: Option[String],
                                  key: String) extends ModelReferenceEvent

  /**
   * Indicates that a session has unshared an existing reference related to a
   * model.
   *
   * @param domainId The id of the domain the model belongs to.
   * @param modelId  The id of the model the reference
   * @param session  The id of the session that shared the reference.
   * @param valueId  The value the reference relates to, or None if it
   *                 relates to the model itself.
   * @param key      The unique key that identifies this reference. The
   *                 key is unique for the model, session, and
   *                 potentially the element.
   */
  final case class UnShareReference(domainId: DomainId,
                                    modelId: String,
                                    session: DomainSessionAndUserId,
                                    valueId: Option[String],
                                    key: String) extends ModelReferenceEvent


  //
  // Common Errors
  //
  final case class ModelAlreadyOpenError() extends AnyRef
    with OpenRealtimeModelError
    with ModelResyncError

  final case class ModelAlreadyOpeningError() extends AnyRef
    with OpenRealtimeModelError
    with ModelResyncError

  final case class ModelNotOpenError() extends AnyRef
    with CloseRealtimeModelError

  final case class ModelDeletedWhileOpeningError() extends AnyRef
    with OpenRealtimeModelError

  final case class ModelAlreadyExistsError(fingerprint: Option[String]) extends AnyRef
    with CreateRealtimeModelError

  final case class ClientDataRequestError(message: String) extends AnyRef
    with OpenRealtimeModelError

  final case class ModelClosingAfterErrorError() extends AnyRef
    with OpenRealtimeModelError
    with ModelResyncError

  final case class ModelNotFoundError() extends AnyRef
    with GetRealtimeModelError
    with OpenRealtimeModelError
    with ModelResyncError
    with DeleteRealtimeModelError
    with GetModelPermissionsError
    with SetModelPermissionsError

  final case class UnauthorizedError(message: String) extends AnyRef
    with GetRealtimeModelError
    with OpenRealtimeModelError
    with CreateOrUpdateRealtimeModelError
    with CreateRealtimeModelError
    with ModelResyncError
    with DeleteRealtimeModelError
    with GetModelPermissionsError
    with SetModelPermissionsError

  final case class UnknownError() extends AnyRef
    with GetRealtimeModelError
    with OpenRealtimeModelError
    with CreateOrUpdateRealtimeModelError
    with CreateRealtimeModelError
    with DeleteRealtimeModelError
    with GetModelPermissionsError
    with SetModelPermissionsError
    with ModelResyncError
    with CloseRealtimeModelError

}
