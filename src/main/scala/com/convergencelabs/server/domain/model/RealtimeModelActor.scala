package com.convergencelabs.server.domain.model

import akka.actor.{ Props, ActorRef, ActorLogging, Actor }
import akka.pattern.{ AskTimeoutException, Patterns }
import com.convergencelabs.server.datastore.domain._
import com.convergencelabs.server.domain.model.ot.cc.{ UnprocessedOperationEvent, ServerConcurrencyControl }
import com.convergencelabs.server.domain.model.ot.ops.Operation
import com.convergencelabs.server.domain.model.ot.xform.OperationTransformer
import org.json4s.JsonAST.JValue
import scala.collection.immutable.HashMap
import scala.concurrent.{ ExecutionContext, Future }
import com.convergencelabs.server.domain.DomainFqn
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import com.convergencelabs.server.domain.model.ot.cc.ProcessedOperationEvent
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration._
import scala.compat.Platform
import com.convergencelabs.server.ErrorResponse
import java.time.Instant
import com.convergencelabs.server.domain.model.ot.xform.TransformationFunctionRegistry

/**
 * An instance of the RealtimeModelActor manages the lifecycle of a single
 * realtime model.
 */
// FIXME right now we don't handle when a client disconnects.
class RealtimeModelActor(
    private[this] val modelManagerActor: ActorRef,
    private[this] val domainFqn: DomainFqn,
    private[this] val modelFqn: ModelFqn,
    private[this] val modelResourceId: String,
    private[this] val modelStore: ModelStore,
    private[this] val modelSnapshotStore: ModelSnapshotStore,
    private[this] val clientDataResponseTimeout: Long,
    private[this] val snapshotConfig: SnapshotConfig) extends Actor with ActorLogging {

  // This sets the actor dispatcher as an implicit execution context.  This way we
  // don't have to pass this argument to futures.
  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private[this] var connectedClients = HashMap[String, ActorRef]()
  private[this] var clientToSessionId = HashMap[ActorRef, String]()
  private[this] var queuedOpeningClients = HashMap[String, OpenRequestRecord]()
  private[this] var concurrencyControl: ServerConcurrencyControl = null
  private[this] var latestSnapshot: SnapshotMetaData = null

  //
  // Receive methods
  //

  def receive = receiveUninitialized

  /**
   * Handles messages when the realtime model has not been initialized yet.
   */
  private[this] def receiveUninitialized: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileUninitialized(request)
    case unknown => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from one or more clients.
   */
  private[this] def receiveInitializingFromClients: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileInitializing(request)
    case dataResponse: DatabaseModelResponse => onDatabaseModelResponse(dataResponse)
    case dataResponse: ClientModelDataResponse => onClientModelDataResponse(dataResponse)
    case ModelDeleted => handleInitializationFailure("model_deleted", "The model was deleted while opening.")
    case unknown => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from the database.
   */
  private[this] def receiveInitializingFromDatabase: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileInitializing(request)
    case dataResponse: DatabaseModelResponse => onDatabaseModelResponse(dataResponse)
    case DatabaseModelFailure(cause) => handleInitializationFailure("unknown", "Unexpected error initializing the model.")
    case ModelDeleted => handleInitializationFailure("model_deleted", "The model was deleted while opening.")
    case dataResponse: ClientModelDataResponse =>
    case unknown => unhandled(unknown)
  }

  /**
   * Handles messages once the model has been completely initialized.
   */
  private[this] def receiveInitialized: Receive = {
    case openRequest: OpenRealtimeModelRequest => onOpenModelWhileInitialized(openRequest)
    case closeRequest: CloseRealtimeModelRequest => onCloseModelRequest(closeRequest)
    case operationSubmission: OperationSubmission => onOperationSubmission(operationSubmission)
    case dataResponse: ClientModelDataResponse =>
    case snapshotMetaData: SnapshotMetaData => this.latestSnapshot = snapshotMetaData
    case ModelDeleted => handleModelDeletedWhileOpen()
    case unknown => unhandled(unknown)
  }

  //
  // Opening and Closing
  //

  /**
   * Starts the open process from an uninitialized model.
   */
  private[this] def onOpenModelWhileUninitialized(request: OpenRealtimeModelRequest): Unit = {
    queuedOpeningClients += (request.sessionId -> OpenRequestRecord(request.clientActor, sender()))

    if (modelStore.modelExists(modelFqn)) {
      // The model is persistent, load from the store.
      requestModelDataFromDatastore()
    } else {
      // The model is not persistent, ask the client for the data.
      requestModelDataFromClient(request.clientActor)
    }
  }

  /**
   * Handles an additional request for opening the model, while the model is
   * already initializing.
   */
  private[this] def onOpenModelWhileInitializing(request: OpenRealtimeModelRequest): Unit = {
    // We know we are already INITIALIZING.  This means we are at least the second client
    // to open the model before it was fully initialized.
    queuedOpeningClients += (request.sessionId -> OpenRequestRecord(request.clientActor, sender()))

    // If we are persistent, then the data is already loading, so there is nothing to do.
    // However, if we are not persistent, we have already asked the previous opening client
    // for the data, but we will ask this client too, in case the others fail.
    if (!modelStore.modelExists(modelFqn)) {
      requestModelDataFromClient(request.clientActor)
    }
  }

  /**
   * Asynchronously requests model data from the database.
   */
  private[this] def requestModelDataFromDatastore(): Unit = {
    // FIXME maybe the databaes should just be async?
    val f = Future[DatabaseModelResponse] {
      val snapshotMetaData = modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelFqn)
      //FIXME: Handle None, handle when snapshot doesn't exist.

      modelStore.getModelData(modelFqn) match {
        case Some(modelData) => DatabaseModelResponse(modelData, snapshotMetaData.get)
        case None => ??? // FIXME there is no mode, need to throw an exception.
      }
    }

    f onComplete {
      case Success(modelDataResponse) => self ! modelDataResponse
      case Failure(cause) => {
        log.error(cause, "Could not initialize model from the database")
        self ! DatabaseModelFailure(cause)
      }
    }

    context.become(receiveInitializingFromDatabase)
  }

  /**
   * Handles model initialization data coming back from the database and attempts to
   * complete the initialization process.
   */
  private[this] def onDatabaseModelResponse(response: DatabaseModelResponse): Unit = {
    try {
      latestSnapshot = response.snapshotMetaData
      val modelData = response.modelData

      concurrencyControl = new ServerConcurrencyControl(
        new OperationTransformer(new TransformationFunctionRegistry()),
        modelData.metaData.version)

      // TODO Initialize tree reference model

      queuedOpeningClients foreach {
        case (clientId, queuedClientRecord) =>
          respondToClientOpenRequest(clientId, modelData, queuedClientRecord)
      }

      this.queuedOpeningClients = HashMap[String, OpenRequestRecord]()
      context.become(receiveInitialized)
    } catch {
      case e: Exception =>
        log.error(e, "Unable to initialize realtime model from database response")
        handleInitializationFailure("unknown", e.getMessage)
    }
  }

  /**
   * Asynchronously requests the model data from the connecting client.
   */
  private[this] def requestModelDataFromClient(clientActor: ActorRef): Unit = {
    val future = Patterns.ask(
      clientActor,
      ClientModelDataRequest(modelFqn),
      clientDataResponseTimeout)

    val askingActor = sender

    future.mapTo[ClientModelDataResponse] onComplete {
      case Success(response) => {
        self ! response
      }
      case Failure(cause) => cause match {
        case e: ClassCastException =>
          log.warning("The client responded with an unexpected value:" + e.getMessage)
          askingActor ! ErrorResponse("invalid_response", "The client responded with an unexpected value.")
        case e: AskTimeoutException =>
          log.debug("A timeout occured waiting for the client to respond with model data.")
          askingActor ! ErrorResponse(
            "data_request_timeout",
            "The client did not correctly respond with data, while initializing a new model.")
        case e: Exception =>
          log.error(e, "Uknnown exception processing model data response.")
          askingActor ! ErrorResponse("unknown", e.getMessage)
      }
    }

    context.become(receiveInitializingFromClients)
  }

  /**
   * Processes the model data coming back from a client.  This will persist the model and
   * then open the model from the database.
   */
  private[this] def onClientModelDataResponse(response: ClientModelDataResponse): Unit = {
    val data = response.modelData
    val createTime = Instant.now()

    modelStore.createModel(modelFqn, data, createTime)
    modelSnapshotStore.createSnapshot(
      SnapshotData(SnapshotMetaData(modelFqn, 0L, createTime), data))

    requestModelDataFromDatastore()
  }

  /**
   * Handles a request to open the model, when the model is already initialized.
   */
  private[this] def onOpenModelWhileInitialized(request: OpenRealtimeModelRequest): Unit = {
    if (connectedClients.contains(request.sessionId)) {
      sender ! ModelAlreadyOpen
    } else {
      //TODO: Handle None
      modelStore.getModelData(modelFqn) match {
        case Some(modelData) => respondToClientOpenRequest(request.sessionId, modelData, OpenRequestRecord(request.clientActor, sender()))
        case None => ??? // The model is open but we can't find data.  This is a major issue.
      }
    }
  }

  /**
   * Lets a client know that the open process has completed successfully.
   */
  private[this] def respondToClientOpenRequest(clientId: String, modelData: ModelData, requestRecord: OpenRequestRecord): Unit = {
    // Inform the concurrency control that we have a new client.
    val contextVersion = modelData.metaData.version
    concurrencyControl.trackClient(clientId, contextVersion)
    connectedClients += (clientId -> requestRecord.clientActor)
    clientToSessionId += (requestRecord.clientActor -> clientId)

    // Send a message to the client informing them of the successful model open.
    val metaData = OpenModelMetaData(
      modelData.metaData.version,
      modelData.metaData.createdTime,
      modelData.metaData.modifiedTime)

    val openModelResponse = OpenModelSuccess(
      self,
      modelResourceId,
      metaData,
      modelData.data)

    requestRecord.askingActor ! openModelResponse
  }

  /**
   * Handles a request to close the model.
   */
  private[this] def onCloseModelRequest(request: CloseRealtimeModelRequest): Unit = {
    if (!connectedClients.contains(request.sessionId)) {
      sender ! ModelNotOpened
    } else {
      val clientActor = connectedClients(request.sessionId)
      clientToSessionId -= clientActor
      connectedClients -= request.sessionId
      concurrencyControl.untrackClient(request.sessionId)

      // TODO handle reference leaving

      // Acknowledge the close back to the requester
      sender ! CloseRealtimeModelSuccess()

      val closedMessage = RemoteClientClosed(modelResourceId, request.sessionId)

      // If there are other clients, inform them.
      connectedClients.values foreach { client => client ! closedMessage }

      checkForConnectionsAndClose()
    }
  }

  /**
   * Determines if there are no more clients connected and if so request to shutdown.
   */
  private[this] def checkForConnectionsAndClose(): Unit = {
    if (connectedClients.isEmpty) {
      modelManagerActor ! new ModelShutdownRequest(this.modelFqn)
    }
  }

  /**
   * Handles the notification of a deleted model, while open.
   */
  private[this] def handleModelDeletedWhileOpen() {
    connectedClients.keys foreach {
      sessionId => forceClosedModel(sessionId, "Model deleted", false)
    }

    context.stop(self)
  }

  //
  // Operation Handling
  //

  private[this] def onOperationSubmission(request: OperationSubmission): Unit = {
    val clientId = this.clientToSessionId(sender)
    val unprocessedOpEvent = UnprocessedOperationEvent(
      clientId,
      request.contextVersion,
      request.operation)

    transformAndApplyOperation(unprocessedOpEvent) match {
      case Success(outgoinOperation) => {
        concurrencyControl.commit()
        broadcastOperation(outgoinOperation, request.seqNo)
        if (snapshotRequired()) { executeSnapshot() }
      }
      case Failure(error) => {
        log.debug("Error applying operation to model, closing client: " + error)
        concurrencyControl.rollback()
        forceClosedModel(
          clientId,
          "Error applying operation to model, closing as a precautionary step: " + error.getMessage,
          true)

      }
    }
  }

  /**
   * Attempts to transform the operation and apply it to the data model.
   */
  private[this] def transformAndApplyOperation(unprocessedOpEvent: UnprocessedOperationEvent): Try[OutgoingOperation] = Try {
    val processedOpEvent = concurrencyControl.processRemoteOperation(unprocessedOpEvent)

    val timestamp = Platform.currentTime

    // TODO get username.
    modelStore.applyOperationToModel(
      modelFqn,
      processedOpEvent.operation,
      processedOpEvent.resultingVersion,
      timestamp,
      "")

    OutgoingOperation(
      modelResourceId,
      processedOpEvent.clientId,
      processedOpEvent.contextVersion,
      timestamp,
      processedOpEvent.operation)
  }

  /**
   * Sends an ACK back to the originator of the operation and an operation message
   * to all other connected clients.
   */
  private[this] def broadcastOperation(outgoingOperation: OutgoingOperation, originSeqNo: Long) {
    val originModelSessiond = outgoingOperation.clientId

    // Ack the sender
    connectedClients(originModelSessiond) ! OperationAcknowledgement(
      modelResourceId, originSeqNo, outgoingOperation.contextVersion)

    // Send the message to all others
    connectedClients.filter(p => p._1 != originModelSessiond) foreach {
      case (sessionId, clientActor) => clientActor ! outgoingOperation
    }
  }

  /**
   * Determines if a snapshot is required.
   */
  private[this] def snapshotRequired(): Boolean = snapshotConfig.snapshotRequired(
    latestSnapshot.version,
    concurrencyControl.contextVersion,
    latestSnapshot.timestamp,
    Instant.now())

  /**
   * Asynchronously performs a snapshot of the model.
   */
  private[this] def executeSnapshot(): Unit = {
    // This might not be the exact version that gets snapshotted
    // but that is OK, this is approximate.
    latestSnapshot = SnapshotMetaData(modelFqn, concurrencyControl.contextVersion, Instant.now())

    val f = Future[SnapshotMetaData] {
      //TODO: Handle None
      val modelData = modelStore.getModelData(this.modelFqn).getOrElse(null)
      val snapshot = new SnapshotData(
        SnapshotMetaData(
          modelData.metaData.fqn,
          modelData.metaData.version,
          modelData.metaData.modifiedTime),
        modelData.data)

      modelSnapshotStore.createSnapshot(snapshot)

      new SnapshotMetaData(
        modelFqn,
        modelData.metaData.version,
        modelData.metaData.modifiedTime)
    }

    f onSuccess {
      case snapshotMetaData =>
        // Send the snapshot back to the model so it knows when the snapshot was actually taken.
        self ! snapshotMetaData
        log.debug(s"Snapshot successfully taken for '${modelFqn.collectionId}/${modelFqn.modelId}' " +
          s"at version: ${snapshotMetaData.version}, timestamp: ${snapshotMetaData.timestamp}")
    }

    f onFailure {
      case cause => log.error(cause, s"Error taking snapshot of model (${modelFqn.collectionId}/${modelFqn.modelId})")
    }
  }

  //
  // Error handling
  //

  /**
   * Kicks all clients out of the model.
   */
  private[this] def forceCloseAllAfterError(reason: String) {
    connectedClients foreach {
      case (clientId, actor) => forceClosedModel(clientId, reason, false)
    }
  }

  /**
   * Kicks a specific clent out of the model.
   */
  private[this] def forceClosedModel(clientId: String, reason: String, notifyOthers: Boolean) {
    val closedActor = connectedClients(clientId)
    connectedClients -= clientId
    concurrencyControl.untrackClient(clientId)

    // TODO handle reference node leaving

    val closedMessage = RemoteClientClosed(modelResourceId, clientId)

    if (notifyOthers) {
      // There are still other clients with this model open so notify them
      // that this person has left
      connectedClients.values foreach { client => client ! closedMessage }
    }

    val forceCloseMessage = ModelForceClose(modelResourceId, clientId, reason)
    closedActor ! forceCloseMessage
    checkForConnectionsAndClose()
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  private[this] def handleInitializationFailure(errorCode: String, errorMessage: String): Unit = {
    queuedOpeningClients.values foreach {
      openRequest => openRequest.askingActor ! ErrorResponse(errorCode, errorMessage)
    }

    queuedOpeningClients = HashMap[String, OpenRequestRecord]()
    checkForConnectionsAndClose()
  }

  override def postStop() {
    log.debug("Unloading Realtime Model({}/{})", domainFqn, modelFqn)
    connectedClients = HashMap()
  }
}

/**
 * Provides a factory method for creating the RealtimeModelActor
 */
object RealtimeModelActor {
  def props(
    modelManagerActor: ActorRef,
    domainFqn: DomainFqn,
    modelFqn: ModelFqn,
    resourceId: String,
    modelStore: ModelStore,
    modelSnapshotStore: ModelSnapshotStore,
    clientDataResponseTimeout: Long,
    snapshotConfig: SnapshotConfig): Props =
    Props(new RealtimeModelActor(
      modelManagerActor,
      domainFqn,
      modelFqn,
      resourceId,
      modelStore,
      modelSnapshotStore,
      clientDataResponseTimeout,
      snapshotConfig))
}