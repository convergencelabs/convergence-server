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

import java.time.Instant

import akka.actor.Cancellable
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.util.Timeout
import com.convergencelabs.convergence.common.Ok
import com.convergencelabs.convergence.server.UnknownErrorResponse
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor
import com.convergencelabs.convergence.server.api.realtime.ModelClientActor.ForceModelCloseReasonCode
import com.convergencelabs.convergence.server.datastore.domain.{DomainPersistenceProvider, ModelDataGenerator}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor.{ModelResyncResponse, OpenRealtimeModelResponse}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelPersistence.PersistenceEventHandler
import com.convergencelabs.convergence.server.domain.model.ot.xform.{OperationTransformer, ReferenceTransformer, TransformationFunctionRegistry}
import com.convergencelabs.convergence.server.domain.model.ot.{ServerConcurrencyControl, UnprocessedOperationEvent}
import com.convergencelabs.convergence.server.domain.{DomainId, DomainUserSessionId, ModelSnapshotConfig}
import com.convergencelabs.convergence.server.util.EventLoop
import grizzled.slf4j.Logging

import scala.collection.immutable.HashMap
import scala.concurrent.duration._
import scala.concurrent.{ExecutionContextExecutor, Future, TimeoutException}
import scala.util.{Failure, Success, Try}

object RealtimeModelManager {
  val DatabaseInitializationFailure: UnknownErrorResponse = UnknownErrorResponse("Unexpected persistence error initializing the model.")

  trait EventHandler {
    def onInitializationError(): Unit

    def onClientOpened(clientActor: ActorRef[ModelClientActor.OutgoingMessage]): Unit

    def onClientClosed(clientActor: ActorRef[ModelClientActor.OutgoingMessage]): Unit

    def closeModel(): Unit
  }

  object State extends Enumeration {
    val Uninitialized, Initializing, InitializationError, Initialized, Error = Value
  }

  trait RealtimeModelClient {
    def send(message: Any): Unit

    def request(message: Any): Future[Any]
  }

  trait Requester {
    def reply(message: Any): Unit
  }

  case class OpenRequestRecord(clientActor: ActorRef[ModelClientActor.OutgoingMessage], replyTo: ActorRef[OpenRealtimeModelResponse])

  case class ResynchronizationRequestRecord(contextVersion: Long, clientActor: ActorRef[ModelClientActor.OutgoingMessage], replyTo: ActorRef[ModelResyncResponse])

  case class ResynchronizationRecord(clientActor: ActorRef[ModelClientActor.OutgoingMessage], timeout: Cancellable)

}

/**
 * The RealtimeModelManager manages the lifecycle of a RealtimeModel.  It is
 * primarily concerned with the opening and closing of the model, and how the
 * model is initialized when it is first opened. This class also keeps track
 * of connected clients to both distribute messages appropriately and also to
 * determine when it time to shut down. This class is delegated to from the
 * [[com.convergencelabs.convergence.server.domain.model.RealtimeModelActor]].
 */
class RealtimeModelManager(private[this] val persistenceFactory: RealtimeModelPersistenceFactory,
                           private[this] val workQueue: EventLoop,
                           private[this] val domainFqn: DomainId,
                           private[this] val modelId: String,
                           private[this] val persistenceProvider: DomainPersistenceProvider,
                           private[this] val permissionsResolver: ModelPermissionResolver,
                           private[this] val modelCreator: ModelCreator,
                           private[this] val clientDataResponseTimeout: Timeout,
                           private[this] val resyncTimeout: FiniteDuration,
                           private[this] implicit val sender: ActorRef[RealtimeModelActor.Message],
                           private[this] val system: ActorSystem[_],
                           private[this] val eventHandler: RealtimeModelManager.EventHandler) extends Logging {

  import RealtimeModelActor._
  import RealtimeModelManager._

  private[this] implicit val ec: ExecutionContextExecutor = system.executionContext

  private[this] val persistence = persistenceFactory.create(new PersistenceEventHandler() {
    def onError(message: String): Unit = {
      workQueue.schedule {
        setState(State.Error)
        forceCloseAllAfterError(message)
      }
    }

    def onClosed(): Unit = {
      // No-Op
    }

    def onOperationCommitted(version: Long): Unit = {
      workQueue.schedule {
        commitVersion(version)
      }
    }

    def onOperationError(message: String): Unit = {
      workQueue.schedule {
        setState(State.Error)
        forceCloseAllAfterError(message, ForceModelCloseReasonCode.ErrorApplyingOperation)
      }
    }
  })

  private[this] val modelStore = persistenceProvider.modelStore
  private[this] val modelSnapshotStore = persistenceProvider.modelSnapshotStore

  private[this] var openClients = HashMap[DomainUserSessionId, ActorRef[ModelClientActor.OutgoingMessage]]()
  private[this] var resyncingClients = HashMap[DomainUserSessionId, ResynchronizationRecord]()
  private[this] var clientToSessionId = HashMap[ActorRef[ModelClientActor.OutgoingMessage], DomainUserSessionId]()
  private[this] var queuedOpeningClients = HashMap[DomainUserSessionId, OpenRequestRecord]()
  private[this] var queuedReconnectingClients = HashMap[DomainUserSessionId, ResynchronizationRequestRecord]()

  private[this] var model: RealTimeModel = _
  private[this] var metaData: ModelMetaData = _
  private[this] var permissions: RealTimeModelPermissions = _

  private[this] var snapshotConfig: ModelSnapshotConfig = _
  private[this] var latestSnapshot: ModelSnapshotMetaData = _
  private[this] var snapshotCalculator: ModelSnapshotCalculator = _
  private[this] var ephemeral: Boolean = false

  private[this] val operationTransformer = new OperationTransformer(new TransformationFunctionRegistry())
  private[this] val referenceTransformer = new ReferenceTransformer(new TransformationFunctionRegistry())

  private[this] var committedVersion: Long = -1
  private[this] var state = State.Uninitialized

  //
  // Opening and Closing
  //

  /**
   * Handles a request by a new client to open the model.
   *
   * @param request The request received by the RealtimeModelActor.
   */
  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequest): Unit = {
    state match {
      case State.Uninitialized =>
        onOpenModelWhileUninitialized(request)
      case State.Initializing =>
        onOpenModelWhileInitializing(request)
      case State.Initialized =>
        onOpenModelWhileInitialized(request)
      case State.Error | State.InitializationError =>
        request.replyTo ! OpenRealtimeModelResponse(Left(ModelClosingAfterErrorError()))
    }
  }

  /**
   * Starts the open process from an uninitialized model.  This only happens
   * when the first client it connecting.  Unless there is an error, after this
   * method is called, the actor will be an in initializing state.
   */
  private[this] def onOpenModelWhileUninitialized(request: OpenRealtimeModelRequest): Unit = {
    debug(s"$domainFqn/$modelId: Handling a request to open the model while it is uninitialized.")
    setState(State.Initializing)

    queuedOpeningClients += (request.session -> OpenRequestRecord(request.clientActor, request.replyTo))
    modelStore.modelExists(modelId) map { exists =>
      if (exists) {
        requestModelDataFromDataStore()
      } else {
        debug(s"$domainFqn/$modelId: Model does not exist, will request from clients.")
        request.autoCreateId match {
          case Some(id) =>
            requestAutoCreateConfigFromClient(request.session, request.clientActor, id)
          case None =>
            request.replyTo ! OpenRealtimeModelResponse(Left(ModelNotFoundError()))
            eventHandler.closeModel()
        }
      }
    } recover { case cause =>
      handleInitializationFailure(
        s"$domainFqn/$modelId: Unable to determine if a model exists.",
        Some(cause),
        OpenRealtimeModelResponse(Left(UnknownError())))
    }
  }

  /**
   * Handles an additional request for opening the model, while the model is
   * already initializing.
   */
  private[this] def onOpenModelWhileInitializing(request: OpenRealtimeModelRequest): Unit = {
    if (queuedOpeningClients.contains(request.session) || queuedReconnectingClients.contains(request.session)) {
      request.replyTo ! OpenRealtimeModelResponse(Left(ModelAlreadyOpeningError()))
    } else {
      debug(s"$domainFqn/$modelId: Handling a request to open the model while it is already initializing.")
      // We know we are already INITIALIZING.  This means we are at least the second client
      // to open the model before it was fully initialized.
      queuedOpeningClients += (request.session -> OpenRequestRecord(request.clientActor, request.replyTo))

      // If we are persistent, then the data is already loading, so there is nothing to do.
      // However, if we are not persistent, we have already asked the previous opening clients
      // for the data, but we will ask this client too, in case the others fail.
      modelStore.modelExists(modelId) map { exists =>
        if (!exists) {
          // If there is an auto create id we can ask this client for data.  If there isn't an auto create
          // id, we can't ask them, but that is ok since we assume the previous client supplied the data
          // else it would have bombed out.
          request.autoCreateId.foreach(id => requestAutoCreateConfigFromClient(request.session, request.clientActor, id))
        }
        // Else no action required, the model must have been persistent, which means we are in the process of
        // loading it from the database.
      } recover { case cause =>
        handleInitializationFailure(
          s"$domainFqn/$modelId: Unable to determine if model exists while handling an open request for an initializing model.",
          Some(cause),
          OpenRealtimeModelResponse(Left(UnknownError())))
      }
    }
  }

  /**
   * Asynchronously requests model data from the database.
   */
  def requestModelDataFromDataStore(): Unit = {
    debug(s"$domainFqn/$modelId: Requesting model data from the database.")
    (for {
      snapshotMetaData <- modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)
      model <- modelStore.getModel(modelId)
    } yield {
      (model, snapshotMetaData) match {
        case (Some(m), Some(s)) =>
          val collectionId = m.metaData.collection
          (for {
            permissions <- this.permissionsResolver.getModelAndCollectionPermissions(modelId, collectionId, persistenceProvider)
            snapshotConfig <- getSnapshotConfigForModel(collectionId)
          } yield {
            this.onDatabaseModelResponse(m, s, snapshotConfig, permissions)
          }) recover {
            case cause: Throwable =>
              this.handleInitializationFailure(
                s"Error getting model permissions (${this.modelId})",
                Some(cause),
                OpenRealtimeModelResponse(Left(UnknownError())))
          }
        case _ =>
          val mMessage = model.map(_ => "found").getOrElse("not found")
          val sMessage = snapshotMetaData.map(_ => "found").getOrElse("not found")
          val message = s"$domainFqn/$modelId: Error getting model data: model: $mMessage, snapshot: $sMessage"
          val cause = new IllegalStateException(message)
          this.handleInitializationFailure(message, Some(cause), OpenRealtimeModelResponse(Left(UnknownError())))
      }
    }) recover { case cause =>
      this.handleInitializationFailure(
        s"$domainFqn/$modelId: Error getting model data",
        Some(cause),
        OpenRealtimeModelResponse(Left(UnknownError())))
    }
  }

  def reloadModelPermissions(): Try[Unit] = {
    // Build a map of all current permissions so we can detect what changes.
    val currentPerms = this.openClients.map { case (session, _) =>
      val sessionPerms = this.permissions.resolveSessionPermissions(session.userId)
      (session, sessionPerms)
    }

    this.permissionsResolver
      .getModelAndCollectionPermissions(modelId, this.metaData.collection, persistenceProvider)
      .map { p =>
        this.permissions = p
        this.metaData = this.metaData.copy(overridePermissions = p.overrideCollection, worldPermissions = p.modelWorld)

        // Send and update to any client that has permissions that have changed.
        this.openClients.foreach { case (session, client) =>
          val current = this.permissions.resolveSessionPermissions(session.userId)
          val previous = currentPerms.get(session)
          if (!previous.contains(current)) {
            if (current.read) {
              client ! ModelClientActor.ModelPermissionsChanged(this.modelId, current)
            } else {
              this.forceCloseModel(
                session,
                ForceModelCloseReasonCode.PermissionsChanged,
                reason = "Permissions changed and this client no longer has read permissions.")
            }
          }
        }
        ()
      }
      .recover { case cause =>
        error(s"$domainFqn/$modelId: Error updating permissions", cause)
        this.forceCloseAllAfterError("Error updating permissions", ForceModelCloseReasonCode.PermissionError)
      }
  }

  /**
   * Handles model initialization data coming back from the database and attempts to
   * complete the initialization process.
   */
  private[this] def onDatabaseModelResponse(modelData: Model,
                                            snapshotMetaData: ModelSnapshotMetaData,
                                            snapshotConfig: ModelSnapshotConfig,
                                            permissions: RealTimeModelPermissions): Unit = {

    debug(s"$domainFqn/$modelId: Model loaded from database.")

    try {
      this.permissions = permissions
      this.latestSnapshot = snapshotMetaData
      this.metaData = modelData.metaData
      this.snapshotConfig = snapshotConfig
      this.snapshotCalculator = new ModelSnapshotCalculator(snapshotConfig)

      this.committedVersion = this.metaData.version

      val concurrencyControl = new ServerConcurrencyControl(
        operationTransformer,
        referenceTransformer,
        this.metaData.version)

      this.model = new RealTimeModel(
        domainFqn,
        modelId,
        concurrencyControl,
        modelData.data)

      // We need too respond to the opening clients first
      // this way the opening clients will get notified of
      // the resyncing clients.
      queuedOpeningClients foreach {
        case (session, record) =>
          respondToClientOpenRequest(session, modelData, record)
      }
      this.queuedOpeningClients = HashMap[DomainUserSessionId, OpenRequestRecord]()

      queuedReconnectingClients foreach {
        case (session, record) =>
          respondToModelResyncRequest(session, record)
      }
      this.queuedReconnectingClients = HashMap[DomainUserSessionId, ResynchronizationRequestRecord]()

      if (this.openClients.isEmpty && this.resyncingClients.isEmpty) {
        this.handleInitializationFailure(
          s"$domainFqn/$modelId: The model was initialized, but no clients are connected.",
          None,
          OpenRealtimeModelResponse(Left(UnknownError())))
      } else {
        setState(State.Initialized)
      }
    } catch {
      case cause: Throwable =>
        handleInitializationFailure(
          s"$domainFqn/$modelId: Unable to initialize the model from the database.",
          Some(cause),
          OpenRealtimeModelResponse(Left(UnknownError())))
    }
  }

  /**
   * Asynchronously requests the model data from the connecting client.
   */
  private[this] def requestAutoCreateConfigFromClient(session: DomainUserSessionId, clientActor: ActorRef[ModelClientActor.OutgoingMessage], autoCreateId: Int): Unit = {
    debug(s"$domainFqn/$modelId: Requesting model config data from client.")
    clientActor.ask[ModelClientActor.ClientAutoCreateModelConfigResponse](
      ModelClientActor.ClientAutoCreateModelConfigRequest(modelId, autoCreateId, _))(clientDataResponseTimeout, system.scheduler)
      .map(_.config.fold({ error =>
        val resp = error match {
          case ModelClientActor.ClientAutoCreateModelConfigTimeout() =>
            OpenRealtimeModelResponse(Left(ClientErrorResponse("The client did not respond to a request for model data in time.")))
          case ModelClientActor.ClientAutoCreateModelConfigInvalid() =>
            OpenRealtimeModelResponse(Left(ClientErrorResponse("The client returned invalid model config data.")))
          case ModelClientActor.UnknownError() =>
            OpenRealtimeModelResponse(Left(ClientErrorResponse("An unknown error occurred getting model data from the client.")))
        }

        workQueue.schedule {
          handleQueuedClientOpenFailureFailure(session, resp)
        }
      }, { response =>
        debug(s"$domainFqn/$modelId: Model config data received from client.")
        workQueue.schedule {
          onClientAutoCreateModelConfigResponse(session, response)
        }
      }))
      .recover {
        case _: TimeoutException =>
          debug(s"$domainFqn/$modelId: A timeout occurred waiting for the client to respond with model data.")
          val resp = OpenRealtimeModelResponse(Left(ClientErrorResponse(
            "The client did not respond in time with model data, while initializing a new model.")))
          workQueue.schedule {
            handleQueuedClientOpenFailureFailure(session, resp)
          }
      }
  }

  /**
   * Processes the model data coming back from a client.  This will persist the model and
   * then open the model from the database.
   */
  def onClientAutoCreateModelConfigResponse(session: DomainUserSessionId, config: ModelClientActor.ClientAutoCreateModelConfig): Unit = {
    if (this.state == State.Initializing) {
      this.queuedOpeningClients.get(session) match {
        case Some(_) =>
          debug(s"$domainFqn/$modelId: Processing config data for model from client.")
          val ModelClientActor.ClientAutoCreateModelConfig(_, modelData, overridePermissions, worldPermissions, userPermissions, ephemeral) = config
          val rootObject = modelData.getOrElse(ModelDataGenerator(Map()))
          val collectionId = config.collectionId

          this.ephemeral = ephemeral.getOrElse(false)

          debug(s"$domainFqn/$modelId: Creating model in database.")

          modelCreator.createModel(
            persistenceProvider,
            Some(session.userId),
            collectionId,
            modelId,
            rootObject,
            overridePermissions,
            worldPermissions,
            userPermissions) map { _ =>
            requestModelDataFromDataStore()
          } recover {
            case cause: Exception =>
              handleQueuedClientOpenFailureFailure(session, OpenRealtimeModelResponse(Left(ClientDataRequestError(cause.getMessage))))
          }
        case None =>
          // Here we could not find the opening record, so we don't know who to respond to.
          // all we can really do is log this as an error.
          error(s"$domainFqn/$modelId: Received a model auto config response for a client that was not in our opening clients queue.")
      }
    }
  }

  /**
   * Handles a request to open the model, when the model is already initialized.
   */
  private[this] def onOpenModelWhileInitialized(request: OpenRealtimeModelRequest): Unit = {
    debug(s"$domainFqn/$modelId: Handling a request to open the model while it is initialized.")

    val session = request.session
    if (openClients.contains(session)) {
      request.replyTo ! OpenRealtimeModelResponse(Left(ModelAlreadyOpenError()))
    } else {
      val model = Model(this.metaData, this.model.data.dataValue())
      respondToClientOpenRequest(session, model, OpenRequestRecord(request.clientActor, request.replyTo))
    }
  }

  /**
   * Lets a client know that the open process has completed successfully.
   */
  private[this] def respondToClientOpenRequest(session: DomainUserSessionId, modelData: Model, requestRecord: OpenRequestRecord): Unit = {
    debug(s"$domainFqn/$modelId: Responding to client open request: " + session)
    if (permissions.resolveSessionPermissions(session.userId).read) {
      this.persistenceProvider.modelStore.getAndIncrementNextValuePrefix(this.modelId) map { valuePrefix =>
        // Inform the concurrency control that we have a new client.
        this.onClientOpened(session, requestRecord.clientActor, resyncClient = false)

        // Send a message to the client informing them of the successful model open.
        val metaData = OpenModelMetaData(
          modelData.metaData.id,
          modelData.metaData.collection,
          modelData.metaData.version,
          modelData.metaData.createdTime,
          modelData.metaData.modifiedTime)

        val referencesBySession = this.model.references()
        val permissions = this.permissions.resolveSessionPermissions(session.userId)
        val openModelResponse = OpenModelSuccess(
          valuePrefix,
          metaData,
          openClients.keySet,
          resyncingClients.keySet,
          referencesBySession,
          modelData.data,
          permissions)

        OpenRealtimeModelResponse(Right(openModelResponse))
      } recover {
        case cause: Throwable =>
          val message = "Unable to get the valueIdPrefix for the model"
          this.error(message, cause)
          OpenRealtimeModelResponse(Left(UnknownError()))
      } foreach (requestRecord.replyTo ! _)
    } else {
      requestRecord.replyTo ! OpenRealtimeModelResponse(Left(UnauthorizedError("Must have read privileges to open model.")))
    }
  }

  private[this] def onClientOpened(session: DomainUserSessionId, clientActor: ActorRef[ModelClientActor.OutgoingMessage], resyncClient: Boolean): Unit = {
    if (!resyncClient) {
      // If this client was in the process of resyncing.. they are
      // already added.
      clientToSessionId += (clientActor -> session)
      val contextVersion = this.model.contextVersion()
      this.model.clientConnected(session, contextVersion)
    }

    openClients += (session -> clientActor)

    eventHandler.onClientOpened(clientActor)

    // Let other clients know
    val msg = ModelClientActor.RemoteClientOpened(modelId, session)
    broadcastToAllOthers(msg, session)
  }

  def onModelResyncRequest(request: ModelResyncRequest): Unit = {
    state match {
      case State.Uninitialized =>
        onResyncWhileUninitialized(request)
      case State.Initializing =>
        onResyncWhileInitializing(request)
      case State.Initialized =>
        onResyncWhileInitialized(request)
      case State.Error | State.InitializationError =>
        request.replyTo ! ModelResyncResponse(Left(ModelClosingAfterErrorError()))
    }
  }

  private[this] def onResyncWhileUninitialized(request: ModelResyncRequest): Unit = {
    debug(s"$domainFqn/$modelId: Handling a request to reconnect to the model while it is uninitialized.")

    modelStore.modelExists(modelId) map { exists =>
      if (exists) {
        queuedReconnectingClients += (request.session ->
          ResynchronizationRequestRecord(request.contextVersion, request.clientActor, request.replyTo)
          )
        requestModelDataFromDataStore()
      } else {
        request.replyTo ! ModelResyncResponse(Left(ModelNotFoundError()))
      }
    } recover {
      case cause =>
        handleInitializationFailure(
          s"$domainFqn/$modelId: Unable to determine if a model exists during a reconnect",
          Some(cause),
          OpenRealtimeModelResponse(Left(UnknownError())))
    }
  }

  private[this] def onResyncWhileInitializing(request: ModelResyncRequest): Unit = {
    if (queuedOpeningClients.contains(request.session) ||
      queuedReconnectingClients.contains(request.session)) {
      request.replyTo ! ModelResyncResponse(Left(ModelAlreadyOpeningError()))
    } else {
      debug(s"$domainFqn/$modelId: Handling a request to reconnect to the model while it is already initializing.")
      queuedReconnectingClients += (request.session -> ResynchronizationRequestRecord(request.contextVersion, request.clientActor, request.replyTo))
    }
  }

  private[this] def onResyncWhileInitialized(request: ModelResyncRequest): Unit = {
    debug(s"$domainFqn/$modelId: Handling a request to reconnect to the model while it is initialized.")

    val session = request.session
    if (openClients.contains(session)) {
      request.replyTo ! ModelResyncResponse(Left(ModelAlreadyOpenError()))
    } else {
      respondToModelResyncRequest(session, ResynchronizationRequestRecord(request.contextVersion, request.clientActor, request.replyTo))
    }
  }

  def respondToModelResyncRequest(session: DomainUserSessionId, record: ResynchronizationRequestRecord): Unit = {
    clientToSessionId += (record.clientActor -> session)

    // This looks in accurate. The reason this is correct
    // is because we are about to send all of the operations
    // up to the current version. So by the time the client
    // sends up any new operations, they will be at this
    // version.
    val currentVersion = this.model.contextVersion()
    this.model.clientConnected(session, currentVersion)

    // TODO after we add a model fingerprint, we should be making sure this is
    //  actually the same model.

    this.permissionsResolver
      .getModelUserPermissions(this.modelId, session.userId, this.persistenceProvider).map { permissions =>

      val currentVersion = this.model.contextVersion()
      record.replyTo ! ModelResyncResponse(Right(ModelResyncResponseData(currentVersion, permissions)))

      val message = ModelClientActor.RemoteClientResyncStarted(modelId, session)
      broadcastToAllOthers(message, session)

      val helper = new ModelOperationReplayHelper(
        this.persistenceProvider.modelOperationStore,
        this.modelId,
        record.clientActor,
        this.ec
      )

      val timeout = createResyncTimeout(session, record.clientActor)
      this.resyncingClients += (session -> ResynchronizationRecord(record.clientActor, timeout))

      helper.sendOperationsInRange(record.contextVersion + 1, currentVersion) onComplete {
        case Success(_) =>
          resetResyncTimeout(session)
        case Failure(cause) =>
          error("error resending operations during model resynchronization", cause)
          record.clientActor ! ModelClientActor.ModelForceClose(this.modelId, "Error resending operations", ForceModelCloseReasonCode.Unknown)
          reconnectComplete(session)
      }
    }.recover {
      case cause: Throwable =>
        error("error getting user permissions during model resynchronization", cause)
        record.clientActor ! ModelClientActor.ModelForceClose(this.modelId, "Error resending operations", ForceModelCloseReasonCode.Unknown)
        reconnectComplete(session)
    }
  }

  private[this] def resetResyncTimeout(session: DomainUserSessionId): Unit = {
    this.resyncingClients.get(session).foreach { r =>
      if (!r.timeout.isCancelled) {
        r.timeout.cancel()

        val timeout = createResyncTimeout(session, r.clientActor)
        this.resyncingClients += (session -> ResynchronizationRecord(r.clientActor, timeout))
      }
    }
  }

  private[this] def createResyncTimeout(session: DomainUserSessionId, clientActor: ActorRef[ModelClientActor.OutgoingMessage]): Cancellable = {
    system.scheduler.scheduleOnce(resyncTimeout, ()=> {
      workQueue.schedule {
        warn("A timeout occurred waiting for the client to complete the resynchronization request")
        clientActor ! ModelClientActor.ModelForceClose(
          this.modelId,
          "Client did not initiate reconnect within the required timespan",
          ForceModelCloseReasonCode.Unknown)
        reconnectComplete(session)
      }
    })
  }

  private[this] def reconnectComplete(session: DomainUserSessionId): Unit = {
    this.resyncingClients.get(session).foreach { r =>
      if (!r.timeout.isCancelled) {
        r.timeout.cancel()
      }
      this.resyncingClients -= session
    }
    val message = ModelClientActor.RemoteClientResyncCompleted(modelId, session)
    broadcastToAllOthers(message, session)
    checkForConnectionsAndClose()
  }

  def onModelResyncClientComplete(message: ModelResyncClientComplete): Unit = {
    val ModelResyncClientComplete(_, _, session, open) = message

    this.resyncingClients.get(session) match {
      case Some(ResynchronizationRecord(clientActor, _)) =>
        if (open) {
          val rc = this.resyncingClients.filter(_._1 != session)
          this.onClientOpened(session, clientActor, resyncClient = true)
          val referencesBySession = this.model.references()
          // We clone these because we don't want to serialize what is in the maps.
          val ocClone: Set[DomainUserSessionId] = Set() ++ openClients.keySet
          val rcClone: Set[DomainUserSessionId] = Set() ++ rc.keySet
          val message = ModelClientActor.ModelResyncServerComplete(this.modelId, ocClone, rcClone, referencesBySession)
          clientActor ! message
        } else {
          this.clientToSessionId -= clientActor
          this.model.clientDisconnected(session)
          val message = ModelClientActor.ModelResyncServerComplete(this.modelId, Set(), Set(), Set())
          clientActor ! message
        }

        reconnectComplete(session)
      case None =>
        warn("Received a model resync complete message from a client that was not resyncing")
    }

    checkForConnectionsAndClose()
  }

  /**
   * Handles a request to close the model.
   */
  def onCloseModelRequest(request: CloseRealtimeModelRequest): Unit = {
    clientClosed(request.session, request.replyTo)
  }

  def handleTerminated(actor: ActorRef[ModelClientActor.OutgoingMessage]): Unit = {
    clientToSessionId.get(actor) match {
      case Some(session) =>
        closeModel(session, notifyOthers = true)
      case None =>
        warn(s"$domainFqn/$modelId: An unexpected actor terminated: " + actor.path)
    }
  }

  private[this] def clientClosed(session: DomainUserSessionId, replyTo: ActorRef[CloseRealtimeModelResponse]): Unit = {
    if (!openClients.contains(session)) {
      replyTo ! CloseRealtimeModelResponse(Left(ModelNotOpenError()))
    } else {
      closeModel(session, notifyOthers = true)
      replyTo ! CloseRealtimeModelResponse(Right(Ok()))
      checkForConnectionsAndClose()
    }
  }

  /**
   * Determines if there are no more clients connected and if so request to shutdown.
   */
  private[this] def checkForConnectionsAndClose(): Unit = {
    state match {
      case State.Uninitialized =>
        // We just close immediately.
        executeClose()
      case State.Initializing | State.InitializationError =>
        if (queuedOpeningClients.isEmpty && queuedReconnectingClients.isEmpty) {
          // We will disconnect after all queued clients have been told that we
          // we can't initialize and have been disconnected.
          executeClose()
        }
      case State.Initialized =>
        val committed = Option(this.model).forall(m => m.contextVersion() == this.committedVersion)
        if (openClients.isEmpty && resyncingClients.isEmpty && committed) {
          // We will disconnect after all clients are disconnected and the model is committed.
          executeClose()
        }
      case State.Error =>
        if (openClients.isEmpty && resyncingClients.isEmpty) {
          // We will disconnect after all clients are disconnected.
          executeClose()
        }
    }
  }

  private[this] def executeClose(): Unit = {
    persistence.close()
    if (this.ephemeral) {
      debug(s"$modelId closing and is ephemeral. Deleting.")
      this.modelStore.deleteModel(this.modelId)
    }
    eventHandler.closeModel()
  }

  //
  // Operation Handling
  //

  def onOperationSubmission(request: OperationSubmission): Unit = {
    if (!this.openClients.contains(request.session) && !this.resyncingClients.contains(request.session)) {
      warn(s"$domainFqn/$modelId: Received operation from client for model that is not open or resyncing!")
    } else {
      val session = request.session
      this.resetResyncTimeout(session)
      if (permissions.resolveSessionPermissions(session.userId).write) {
        val unprocessedOpEvent = UnprocessedOperationEvent(
          session.sessionId,
          request.contextVersion,
          request.operation)

        transformAndApplyOperation(session, unprocessedOpEvent) match {
          case Success(outgoingOperation) =>
            broadcastOperation(session, outgoingOperation, request.seqNo)
            this.metaData = this.metaData.copy(
              version = outgoingOperation.contextVersion + 1,
              modifiedTime = outgoingOperation.timestamp)

            if (snapshotRequired()) {
              executeSnapshot()
            }
          case Failure(cause) =>
            error(s"$domainFqn/$modelId: Error applying operation to model, kicking client from model: $request", cause)
            forceCloseModel(
              session,
              ForceModelCloseReasonCode.ErrorApplyingOperation,
              s"Error applying operation seqNo ${request.seqNo} to model, kicking client out of model: " + cause.getMessage)
        }
      } else {
        forceCloseModel(session, ForceModelCloseReasonCode.Unauthorized, "Unauthorized to edit this model")
      }
    }
  }

  /**
   * Attempts to transform the operation and apply it to the data model.
   */
  private[this] def transformAndApplyOperation(session: DomainUserSessionId, unprocessedOpEvent: UnprocessedOperationEvent): Try[ModelClientActor.OutgoingOperation] = {
    val timestamp = Instant.now()
    this.model.processOperationEvent(unprocessedOpEvent).map {
      case (processedOpEvent, appliedOp) =>
        persistence.processOperation(NewModelOperation(
          modelId,
          processedOpEvent.resultingVersion,
          timestamp,
          session.sessionId,
          appliedOp))

        ModelClientActor.OutgoingOperation(
          modelId,
          session,
          processedOpEvent.contextVersion,
          timestamp,
          processedOpEvent.operation)
    }
  }

  /**
   * Sends an ACK back to the originator of the operation and an operation message
   * to all other connected clients.
   */
  private[this] def broadcastOperation(session: DomainUserSessionId,
                                       outgoingOperation: ModelClientActor.OutgoingOperation,
                                       originSeqNo: Int): Unit = {
    // Ack the sender
    getClientForSession(session).foreach { client =>
      client ! ModelClientActor.OperationAcknowledgement(
        modelId, originSeqNo, outgoingOperation.contextVersion, outgoingOperation.timestamp)
    }

    broadcastToAllOthers(outgoingOperation, session)
  }

  private[this] def getClientForSession(session: DomainUserSessionId): Option[ActorRef[ModelClientActor.OutgoingMessage]] = {
    openClients.get(session).orElse(resyncingClients.get(session).map(_.clientActor))
  }

  //
  // References
  //
  def onReferenceEvent(request: ModelReferenceEvent): Unit = {
    val session = request.session
    this.model.processReferenceEvent(request) match {
      case Success(Some(event)) =>
        broadcastToAllOthers(event, session)
      case Success(None) =>
      // Event's no-op'ed
      case Failure(cause) =>
        error(s"$domainFqn/$modelId: Invalid reference event", cause)
        forceCloseModel(
          session,
          ForceModelCloseReasonCode.InvalidReferenceEvent,
          "invalid reference event"
        )
    }
  }

  private[this] def broadcastToAllOthers(message: ModelClientActor.OutgoingMessage, origin: DomainUserSessionId): Unit = {
    openClients.filter(p => p._1 != origin) foreach {
      case (_, clientActor) => clientActor ! message
    }
  }

  private[this] def snapshotRequired(): Boolean = this.snapshotCalculator.snapshotRequired(
    latestSnapshot.version,
    model.contextVersion(),
    latestSnapshot.timestamp,
    Instant.now())

  /**
   * Asynchronously performs a snapshot of the model.
   */
  private[this] def executeSnapshot(): Unit = {
    // This might not be the exact version that gets a snapshot
    // but that is OK, this is approximate. we send a message to
    // send the snapshot back to the actor to refine the exact version.
    latestSnapshot = ModelSnapshotMetaData(modelId, model.contextVersion(), Instant.now())
    this.persistence.executeSnapshot()
  }

  private[this] def getSnapshotConfigForModel(collectionId: String): Try[ModelSnapshotConfig] = {
    persistenceProvider.collectionStore.getOrCreateCollection(collectionId).flatMap { c =>
      if (c.overrideSnapshotConfig) {
        Success(c.snapshotConfig)
      } else {
        persistenceProvider.configStore.getModelSnapshotConfig()
      }
    }
  }

  def commitVersion(version: Long): Unit = {
    if (version != this.committedVersion + 1) {
      forceCloseAllAfterError(s"The committed version ($version) was not what was expected (${this.committedVersion + 1}).", ForceModelCloseReasonCode.UnexpectedCommittedVersion)
    } else {
      this.committedVersion = version
      this.checkForConnectionsAndClose()
    }
  }

  //
  // Error handling
  //

  /**
   * Kicks all clients out of the model.
   */
  def forceCloseAllAfterError(reason: String, reasonCode: ForceModelCloseReasonCode.Value = ForceModelCloseReasonCode.Unknown): Unit = {
    setState(State.Error)
    debug(s"$domainFqn/$modelId: Force closing all clients: $reason ($reasonCode)")
    allSessionIds() foreach (sessionId => forceCloseModel(sessionId, reasonCode, reason, notifyOthers = false))
  }

  def modelDeleted(): Unit = {
    this.forceCloseAllAfterError("The model was deleted", ForceModelCloseReasonCode.Deleted)
  }

  /**
   * Kicks a specific client out of the model.
   */
  private[this] def forceCloseModel(session: DomainUserSessionId, reasonCode: ForceModelCloseReasonCode.Value, reason: String, notifyOthers: Boolean = true): Unit = {
    closeModel(session, notifyOthers).foreach { closedActor =>
      val forceCloseMessage = ModelClientActor.ModelForceClose(modelId, reason, reasonCode)
      closedActor ! forceCloseMessage
    }

    checkForConnectionsAndClose()
  }

  /**
   * Closes a model for a session and return the associated actor
   *
   * @param session      The session of the client to close
   * @param notifyOthers If True notifies other connected clients of close
   * @return The actor associated with the closed session
   */
  private[this] def closeModel(session: DomainUserSessionId, notifyOthers: Boolean): Option[ActorRef[ModelClientActor.OutgoingMessage]] = {
    val closedActor = openClients
      .get(session)
      .orElse(resyncingClients.get(session).map(_.clientActor))

    openClients -= session
    resyncingClients -= session

    this.model.clientDisconnected(session)

    closedActor.foreach { closedActor =>
      clientToSessionId -= closedActor
      eventHandler.onClientClosed(closedActor)
    }

    if (notifyOthers) {
      // There are still other clients with this model open so notify them
      // that this person has left
      val closedMessage = ModelClientActor.RemoteClientClosed(modelId, session)
      openClients.values foreach { client => client ! closedMessage }
    }

    closedActor
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  def handleInitializationFailure(logMessage: String, error: Option[Throwable], response: OpenRealtimeModelResponse): Unit = {
    error match {
      case Some(e) =>
        this.error(logMessage, e)
      case None =>
        this.error(logMessage)
    }

    setState(State.InitializationError)
    queuedOpeningClients.values foreach { openRequest =>
      openRequest.replyTo ! response
    }

    queuedOpeningClients = HashMap[DomainUserSessionId, OpenRequestRecord]()
    checkForConnectionsAndClose()
    eventHandler.onInitializationError()
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  def handleQueuedClientOpenFailureFailure(session: DomainUserSessionId, response: OpenRealtimeModelResponse): Unit = {
    queuedOpeningClients.get(session) foreach (openRequest => openRequest.replyTo ! response)
    queuedOpeningClients -= session
    checkForConnectionsAndClose()
  }

  private[this] def setState(state: State.Value): Unit = {
    this.state = state
  }

  private[this] def allSessionIds(): Set[DomainUserSessionId] = {
    openClients.keySet ++ resyncingClients.keySet
  }
}