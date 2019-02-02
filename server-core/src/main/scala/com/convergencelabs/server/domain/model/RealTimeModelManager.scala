package com.convergencelabs.server.domain.model

import java.time.Instant

import scala.collection.immutable.HashMap
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.ModelDataGenerator
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainUserSessionId
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.UnauthorizedException
import com.convergencelabs.server.domain.model.RealtimeModelPersistence.PersistenceEventHanlder
import com.convergencelabs.server.domain.model.ot.OperationTransformer
import com.convergencelabs.server.domain.model.ot.ServerConcurrencyControl
import com.convergencelabs.server.domain.model.ot.TransformationFunctionRegistry
import com.convergencelabs.server.domain.model.ot.UnprocessedOperationEvent
import com.convergencelabs.server.domain.model.ot.xform.ReferenceTransformer
import com.convergencelabs.server.util.EventLoop
import com.convergencelabs.server.util.concurrent.UnexpectedErrorException

import akka.actor.ActorContext
import akka.actor.ActorRef
import akka.actor.Status
import akka.actor.Terminated
import akka.pattern.AskTimeoutException
import akka.pattern.Patterns
import akka.util.Timeout
import grizzled.slf4j.Logging

object RealTimeModelManager {
  val DatabaseInitializationFailure = UnknownErrorResponse("Unexpected persistence error initializing the model.")

  trait EventHandler {
    def onInitializationError(): Unit
    def onClientOpened(clientActor: ActorRef): Unit
    def onClientClosed(clientActor: ActorRef): Unit
    def closeModel()
  }

  object State extends Enumeration {
    val Uninitialized, Initializing, InitializationError, Initialized, Error = Value
  }

  trait RealtimeModelClient {
    def send(message: Any): Unit
    def request(message: Any): Future[Any]
  }

  trait Requestor {
    def reply(message: Any): Unit
  }

  case class OpenRequestRecord(clientActor: ActorRef, askingActor: ActorRef)
}

class RealTimeModelManager(
  private[this] val persistenceFactory: RealtimeModelPersistenceFactory,
  private[this] val workQueue: EventLoop,
  private[this] val domainFqn: DomainFqn,
  private[this] val modelId: String,
  private[this] val persistenceProvider: DomainPersistenceProvider,
  private[this] val permissionsResolver: ModelPermissionResolver,
  private[this] val modelCreator: ModelCreator,
  private[this] val clientDataResponseTimeout: Timeout,
  private[this] val context: ActorContext,
  private[this] val eventHandler: RealTimeModelManager.EventHandler) extends Logging {

  import RealTimeModelManager._

  private[this] implicit val ec = context.dispatcher

  val persistence = persistenceFactory.create(new PersistenceEventHanlder() {
    def onError(message: String): Unit = {
      workQueue.schedule {
        forceCloseAllAfterError(message)
      }
    }

    def onClosed(): Unit = {
      // No-Op
    }

    def onOperationCommited(version: Long): Unit = {
      workQueue.schedule {
        commitVersion(version)
      }
    }

    def onOperationError(message: String): Unit = {
      workQueue.schedule {
        state = State.Error
        forceCloseAllAfterError(message)
      }
    }
  });

  private[this] val modelStore = persistenceProvider.modelStore
  private[this] val modelSnapshotStore = persistenceProvider.modelSnapshotStore

  private[this] var connectedClients = HashMap[DomainUserSessionId, ActorRef]()
  private[this] var clientToSessionId = HashMap[ActorRef, DomainUserSessionId]()
  private[this] var queuedOpeningClients = HashMap[DomainUserSessionId, OpenRequestRecord]()

  private[this] var model: RealTimeModel = _
  private[this] var metaData: ModelMetaData = _
  private[this] var valuePrefix: Long = _
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

  def onOpenRealtimeModelRequest(request: OpenRealtimeModelRequest, replyTo: ActorRef) {
    state match {
      case State.Uninitialized =>
        onOpenModelWhileUninitialized(request, replyTo)
      case State.Initializing =>
        onOpenModelWhileInitializing(request, replyTo)
      case State.Initialized =>
        onOpenModelWhileInitialized(request, replyTo)
      case State.Error | State.InitializationError =>
        replyTo ! Status.Failure(new UnexpectedErrorException(
            "The model can't be open, because it is currently shutting down after an error. You may be able to reopen the model."))
    }
  }

  /**
   * Starts the open process from an uninitialized model.  This only happens
   * when the first client it connecting.  Unless there is an error, after this
   * method is called, the actor will be an in initializing state.
   */
  def onOpenModelWhileUninitialized(request: OpenRealtimeModelRequest, replyTo: ActorRef): Unit = {
    debug(s"${domainFqn}/${modelId}: Handling a request to open the model while it is uninitialized.")
    setState(State.Initializing)

    queuedOpeningClients += (request.session -> OpenRequestRecord(request.clientActor, replyTo))
    modelStore.modelExists(modelId) map { exists =>
      if (exists) {
        debug(s"${domainFqn}/${modelId}. Model exists, loading immediately from the database.")
        requestModelDataFromDatastore()
      } else {
        debug(s"${domainFqn}/${modelId}: Model does not exist, will request from clients.")
        request.autoCreateId match {
          case Some(id) =>
            requestAutoCreateConfigFromClient(request.session, request.clientActor, id)
          case None =>
            replyTo ! Status.Failure(ModelNotFoundException(modelId))
            eventHandler.closeModel()
        }
      }
    } recover {
      case cause =>
        error(s"${domainFqn}/${modelId}: Unable to determine if a model exists.", cause)
        handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    }
  }

  /**
   * Handles an additional request for opening the model, while the model is
   * already initializing.
   */
  def onOpenModelWhileInitializing(request: OpenRealtimeModelRequest, replyTo: ActorRef): Unit = {
    if (queuedOpeningClients.contains(request.session)) {
      replyTo ! Status.Failure(new ModelAlreadyOpeningException())
    } else {
      debug(s"${domainFqn}/${modelId}: Handling a request to open the model while it is already initializing.")
      // We know we are already INITIALIZING.  This means we are at least the second client
      // to open the model before it was fully initialized.
      queuedOpeningClients += (request.session -> OpenRequestRecord(request.clientActor, replyTo))

      // If we are persistent, then the data is already loading, so there is nothing to do.
      // However, if we are not persistent, we have already asessioned the previous opening clients
      // for the data, but we will asession this client too, in case the others fail.
      modelStore.modelExists(modelId) map { exists =>
        if (!exists) {
          // If there is an auto create id we can asession this client for data.  If there isn't an auto create
          // id, we can't asession them, but that is ok since we assume the previous client supplied the data
          // else it would have bombed out.
          request.autoCreateId.foreach((id) => requestAutoCreateConfigFromClient(request.session, request.clientActor, id))
        }
        // Else no action required, the model must have been persistent, which means we are in the process of
        // loading it from the database.
      } recover {
        case cause =>
          error(
            s"${domainFqn}/${modelId}: Unable to determine if model exists while handling an open request for an initializing model.",
            cause)
          handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
      }
    }
  }

  /**
   * Asynchronously requests model data from the database.
   */
  def requestModelDataFromDatastore(): Unit = {
    debug(s"${domainFqn}/${modelId}: Requesting model data from the database.")
    (for {
      snapshotMetaData <- modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)
      model <- modelStore.getModel(modelId)
    } yield {
      (model, snapshotMetaData) match {
        case (Some(m), Some(s)) =>
          val collectionId = m.metaData.collectionId
          (for {
            permissions <- this.permissionsResolver.getModelAndCollectionPermissions(modelId, collectionId, persistenceProvider)
            snapshotConfig <- getSnapshotConfigForModel(collectionId)
          } yield {
            this.onDatabaseModelResponse(m, s, snapshotConfig, permissions)
          }) recover {
            case cause: Throwable =>
              val message = s"Error getting model permissions (${this.modelId})"
              error(message, cause)
              this.handleInitializationFailure(DatabaseInitializationFailure)
          }
        case _ =>
          val mMessage = model.map(_ => "found").getOrElse("not found")
          val sMessage = snapshotMetaData.map(_ => "found").getOrElse("not found")
          val message = s"${domainFqn}/${modelId}: Error getting model data: model: ${mMessage}, snapshot: ${sMessage}"
          val cause = new IllegalStateException(message)
          error(message, cause)
          this.handleInitializationFailure(DatabaseInitializationFailure)
      }
    }) recover {
      case cause =>
        error(s"${domainFqn}/${modelId}: Error getting model data.", cause)
        this.handleInitializationFailure(DatabaseInitializationFailure)
    }
  }

  def reloadModelPermissions(): Try[Unit] = {
    // Build a map of all current permissions so we can detect what changes.
    val currentPerms = this.connectedClients.map {
      case (session, client) =>
        val sessionPerms = this.permissions.resolveSessionPermissions(session.userId)
        (session, sessionPerms)
    }

    this.permissionsResolver
      .getModelAndCollectionPermissions(modelId, this.metaData.collectionId, persistenceProvider)
      .map { p =>
        this.permissions = p
        this.metaData = this.metaData.copy(overridePermissions = p.overrideCollection, worldPermissions = p.modelWorld)

        // Fire of an update to any client whose permissions have changed.
        this.connectedClients.foreach {
          case (session, client) =>
            val current = this.permissions.resolveSessionPermissions(session.userId)
            val previous = currentPerms.get(session)
            if (Some(current) != previous) {
              val message = ModelPermissionsChanged(this.modelId, current)
              client ! message
            }
        }
        ()
      }.recover {
        case cause =>
          error(s"${domainFqn}/${modelId}: Error updating permissions", cause)
          this.forceCloseAllAfterError("Error updating permissions")
      }
  }

  /**
   * Handles model initialization data coming back from the database and attempts to
   * complete the initialization process.
   */
  private[this] def onDatabaseModelResponse(
    modelData: Model,
    snapshotMetaData: ModelSnapshotMetaData,
    snapshotConfig: ModelSnapshotConfig,
    permissions: RealTimeModelPermissions): Unit = {

    debug(s"${domainFqn}/${modelId}: Model loaded from database.")

    try {
      this.permissions = permissions
      this.latestSnapshot = snapshotMetaData
      this.metaData = modelData.metaData
      this.valuePrefix = modelData.metaData.valuePrefix
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

      queuedOpeningClients foreach {
        case (sessionKey, queuedClientRecord) =>
          respondToClientOpenRequest(sessionKey, modelData, queuedClientRecord)
      }

      this.queuedOpeningClients = HashMap[DomainUserSessionId, OpenRequestRecord]()
      if (this.connectedClients.isEmpty) {
        error(s"${domainFqn}/${modelId}: The model was initialized, but not clients are connected.")
        this.handleInitializationFailure(UnknownErrorResponse("Model was initialized, but no clients connected"))
      }
      setState(State.Initialized)
    } catch {
      case cause: Throwable =>
        error(
          s"${domainFqn}/${modelId}: Unable to initialize the model from the database.",
          cause)
        handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    }
  }

  /**
   * Asynchronously requests the model data from the connecting client.
   */
  private[this] def requestAutoCreateConfigFromClient(session: DomainUserSessionId, clientActor: ActorRef, autoCreateId: Int): Unit = {
    debug(s"${domainFqn}/${modelId}: Requesting model config data from client.")

    val future = Patterns.ask(clientActor, ClientAutoCreateModelConfigRequest(modelId, autoCreateId), clientDataResponseTimeout)
    future.mapTo[ClientAutoCreateModelConfigResponse] onComplete {
      case Success(response) =>
        debug(s"${domainFqn}/${modelId}: Model config data received from client.")
        workQueue.schedule {
          onClientAutoCreateModelConfigResponse(session, response)
        }
      case Failure(cause) => cause match {
        case e: AskTimeoutException =>
          debug(s"${domainFqn}/${modelId}: A timeout occured waiting for the client to respond with model data.")
          workQueue.schedule {
            handleQueuedClientOpenFailureFailure(
              session,
              ClientDataRequestFailure("The client did not respond in time with model data, while initializing a new model."))
          }
        case e: Throwable =>
          error(s"${domainFqn}/${modelId}: Uknnown exception processing model config data response.", e)
          workQueue.schedule {
            handleQueuedClientOpenFailureFailure(session, UnknownErrorResponse(e.getMessage))
          }
      }
    }
  }

  /**
   * Processes the model data coming back from a client.  This will persist the model and
   * then open the model from the database.
   */
  def onClientAutoCreateModelConfigResponse(session: DomainUserSessionId, config: ClientAutoCreateModelConfigResponse): Unit = {
    if (this.state == State.Initializing) {
      this.queuedOpeningClients.get(session) match {
        case Some(openRecord) =>
          debug(s"${domainFqn}/${modelId}: Processing config data for model from client.")
          val ClientAutoCreateModelConfigResponse(colleciton, modelData, overridePermissions, worldPermissions, userPermissions, ephemeral) = config

          val overrideWorld = overridePermissions.getOrElse(false)
          val worldPerms = worldPermissions.getOrElse(ModelPermissions(false, false, false, false))
          val rootObject = modelData.getOrElse(ModelDataGenerator(Map()))
          val collectionId = config.collectionId

          this.ephemeral = ephemeral.getOrElse(false)

          debug(s"${domainFqn}/${modelId}: Creating model in database.")

          modelCreator.createModel(
            persistenceProvider,
            Some(session.userId),
            collectionId,
            modelId,
            rootObject,
            overridePermissions,
            worldPermissions,
            userPermissions) map { _ =>
              requestModelDataFromDatastore()
            } recover {
              case cause: Exception =>
                handleQueuedClientOpenFailureFailure(session, cause)
            }
        case None =>
          // Here we could not find the opening record, so we don't know who to respond to.
          // all we can really do is log this as an error.
          error(s"${domainFqn}/${modelId}: Received a model auto config response for a client that was not in our opening clients queue.")
      }
    }
  }

  /**
   * Handles a request to open the model, when the model is already initialized.
   */
  private[this] def onOpenModelWhileInitialized(request: OpenRealtimeModelRequest, asker: ActorRef): Unit = {
    debug(s"${domainFqn}/${modelId}: Handling a request to open the model while it is initialized.")

    val session = request.session
    if (connectedClients.contains(session)) {
      asker ! Status.Failure(new ModelAlreadyOpenException())
    } else {
      val model = Model(this.metaData, this.model.data.dataValue())
      respondToClientOpenRequest(session, model, OpenRequestRecord(request.clientActor, asker))
    }
  }

  /**
   * Lets a client know that the open process has completed successfully.
   */
  private[this] def respondToClientOpenRequest(session: DomainUserSessionId, modelData: Model, requestRecord: OpenRequestRecord): Unit = {
    debug(s"${domainFqn}/${modelId}: Responding to client open request: " + session)
    if (permissions.resolveSessionPermissions(session.userId).read) {
      // Inform the concurrency control that we have a new client.
      val contextVersion = modelData.metaData.version
      this.model.clientConnected(session, contextVersion)
      connectedClients += (session -> requestRecord.clientActor)
      clientToSessionId += (requestRecord.clientActor -> session)

      eventHandler.onClientOpened(requestRecord.clientActor)

      // Send a message to the client informing them of the successful model open.
      val metaData = OpenModelMetaData(
        modelData.metaData.modelId,
        modelData.metaData.collectionId,
        modelData.metaData.version,
        modelData.metaData.createdTime,
        modelData.metaData.modifiedTime)

      val referencesBySession = this.model.references()
      val permissions = this.permissions.resolveSessionPermissions(session.userId)
      val openModelResponse = OpenModelSuccess(
        valuePrefix,
        metaData,
        connectedClients.keySet,
        referencesBySession,
        modelData.data,
        permissions)

      valuePrefix = valuePrefix + 1
      modelStore.setNextPrefixValue(modelId, valuePrefix)

      requestRecord.askingActor ! openModelResponse

      // Let other client knows
      val msg = RemoteClientOpened(modelId, session)
      connectedClients filterKeys ({ _ != session }) foreach {
        case (session, clientActor) =>
          clientActor ! msg
      }
    } else {
      requestRecord.askingActor ! Status.Failure(UnauthorizedException("Must have read privileges to open model."))
    }
  }

  /**
   * Handles a request to close the model.
   */
  def onCloseModelRequest(request: CloseRealtimeModelRequest, askingActor: ActorRef): Unit = {
    clientClosed(request.session, askingActor)
  }

  def handleTerminated(terminated: Terminated): Unit = {
    clientToSessionId.get(terminated.actor) match {
      case Some(session) =>
        clientClosed(session, terminated.actor)
      case None =>
        warn(s"${domainFqn}/${modelId}: An unexpected actor terminated: " + terminated.actor.path)
    }
  }

  private[this] def clientClosed(session: DomainUserSessionId, askingActor: ActorRef): Unit = {
    if (!connectedClients.contains(session)) {
      askingActor ! Status.Failure(new ModelNotOpenException())
    } else {
      val closedActor = closeModel(session, true)
      askingActor ! (())
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
        persistence.close()
        eventHandler.closeModel()
      case State.Initializing | State.InitializationError =>
        if (queuedOpeningClients.isEmpty) {
          // We will disconnect after all queued clients have been told that we
          // we can't initialize and have been disconnected.
          persistence.close()
          eventHandler.closeModel()
        }
      case State.Initialized =>
        val committed = Option(this.model).map { m => m.contextVersion() == this.committedVersion }.getOrElse(true)
        if (connectedClients.isEmpty && committed) {
           // We will disconnect after all clients are disconnected and the model is committed.
          persistence.close()
          eventHandler.closeModel()
        }
      case State.Error =>
        if (connectedClients.isEmpty) {
          // We will disconnect after all clients are disconnected.
          persistence.close()
          eventHandler.closeModel()
        }
    }
  }

  /**
   * Handles the notification of a deleted model, while open.
   */
  private[this] def handleModelDeletedWhileOpen(): Unit = {
    forceCloseAllAfterError("Model deleted")
  }

  //
  // Operation Handling
  //

  def onOperationSubmission(request: OperationSubmission, clientActor: ActorRef): Unit = {
    val sessionKey = this.clientToSessionId.get(clientActor)
    sessionKey match {
      case None => warn(s"${domainFqn}/${modelId}: Received operation from client for model that is not open!")
      case Some(session) => {
        if (permissions.resolveSessionPermissions(session.userId).write) {
          val unprocessedOpEvent = UnprocessedOperationEvent(
            session.sessionId,
            request.contextVersion,
            request.operation)

          transformAndApplyOperation(session, unprocessedOpEvent) match {
            case Success(outgoinOperation) =>
              broadcastOperation(session, outgoinOperation, request.seqNo)
              this.metaData = this.metaData.copy(
                version = outgoinOperation.contextVersion + 1,
                modifiedTime = outgoinOperation.timestamp)

              if (snapshotRequired()) {
                executeSnapshot()
              }
            case Failure(cause) =>
              error(s"${domainFqn}/${modelId}: Error applying operation to model, kicking client from model: ${request}", cause);
              forceClosedModel(
                session,
                s"Error applying operation seqNo ${request.seqNo} to model, kicking client out of model: " + cause.getMessage,
                true)
          }
        } else {
          forceClosedModel(
            session,
            s"Unauthorized to edit this model",
            true)
        }
      }
    }

  }

  /**
   * Attempts to transform the operation and apply it to the data model.
   */
  private[this] def transformAndApplyOperation(session: DomainUserSessionId, unprocessedOpEvent: UnprocessedOperationEvent): Try[OutgoingOperation] = {
    val timestamp = Instant.now()
    this.model.processOperationEvent(unprocessedOpEvent).map {
      case (processedOpEvent, appliedOp) =>
        persistence.processOperation(NewModelOperation(
          modelId,
          processedOpEvent.resultingVersion,
          timestamp,
          session.sessionId,
          appliedOp))

        OutgoingOperation(
          modelId,
          session,
          processedOpEvent.contextVersion.toInt, // TODO: Update Int to Long or the other way
          timestamp,
          processedOpEvent.operation)
    }
  }

  /**
   * Sends an ACK back to the originator of the operation and an operation message
   * to all other connected clients.
   */
  private[this] def broadcastOperation(session: DomainUserSessionId, outgoingOperation: OutgoingOperation, originSeqNo: Long): Unit = {
    // Ack the sender
    connectedClients(session) ! OperationAcknowledgement(
      modelId, originSeqNo.toInt, outgoingOperation.contextVersion, outgoingOperation.timestamp)

    broacastToAllOthers(outgoingOperation, session)
  }

  //
  // References
  //
  def onReferenceEvent(request: ModelReferenceEvent, clientActor: ActorRef): Unit = {
    val session = this.clientToSessionId(clientActor)
    this.model.processReferenceEvent(request, session) match {
      case Success(Some(event)) =>
        broacastToAllOthers(event, session)
      case Success(None) =>
      // Event's no-op'ed
      case Failure(cause) =>
        error(s"${domainFqn}/${modelId}: Invalid reference event", cause)
        forceClosedModel(session, "invalid reference event", true)
    }
  }

  private[this] def broacastToAllOthers(message: Any, origin: DomainUserSessionId): Unit = {
    connectedClients.filter(p => p._1 != origin) foreach {
      case (session, clientActor) => clientActor ! message
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
    // This might not be the exact version that gets snapshotted
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
      forceCloseAllAfterError(s"The commited version ($version) was not what was expected (${this.committedVersion + 1}).")
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
  def forceCloseAllAfterError(reason: String): Unit = {
    setState(State.Error)
    debug(s"${domainFqn}/${modelId}: Force closing all clients: $reason")
    connectedClients foreach {
      case (clientId, actor) => forceClosedModel(clientId, reason, false)
    }
  }

  def modelDeleted(): Unit = {
    this.forceCloseAllAfterError("The model was deleted")
  }

  /**
   * Kicks a specific client out of the model.
   */
  private[this] def forceClosedModel(session: DomainUserSessionId, reason: String, notifyOthers: Boolean): Unit = {
    val closedActor = closeModel(session, notifyOthers)

    val forceCloseMessage = ModelForceClose(modelId, reason)
    closedActor ! forceCloseMessage

    checkForConnectionsAndClose()
  }

  /**
   * Closes a model for a session and return the associated actor
   *
   * @param session The session of the client to close
   * @param notifyOthers If True notifies other connected clients of close
   * @return The actor associated with the closed session
   */
  private[this] def closeModel(session: DomainUserSessionId, notifyOthers: Boolean): ActorRef = {
    val closedActor = connectedClients(session)
    connectedClients -= session
    clientToSessionId -= closedActor
    this.model.clientDisconnected(session)
    eventHandler.onClientClosed(closedActor)

    if (notifyOthers) {
      // There are still other clients with this model open so notify them
      // that this person has left
      val closedMessage = RemoteClientClosed(modelId, session)
      connectedClients.values foreach { client => client ! closedMessage }
    }

    closedActor
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  def handleInitializationFailure(response: AnyRef): Unit = {
    setState(State.InitializationError)
    queuedOpeningClients.values foreach { openRequest =>
      openRequest.askingActor ! response
    }
    queuedOpeningClients = HashMap[DomainUserSessionId, OpenRequestRecord]()
    checkForConnectionsAndClose()
    eventHandler.onInitializationError()
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  def handleQueuedClientOpenFailureFailure(session: DomainUserSessionId, response: AnyRef): Unit = {
    queuedOpeningClients.get(session) foreach (openRequest => openRequest.askingActor ! response)
    queuedOpeningClients -= session
    checkForConnectionsAndClose()
  }

  private[this] def setState(state: State.Value): Unit = {
    this.state = state
  }
}