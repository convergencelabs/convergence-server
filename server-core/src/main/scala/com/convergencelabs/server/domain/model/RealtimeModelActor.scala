package com.convergencelabs.server.domain.model

import akka.actor.{ Props, ActorRef, ActorLogging, Actor }
import akka.pattern.{ AskTimeoutException, Patterns }
import java.time.Instant
import java.time.Duration
import org.json4s.JsonAST.JValue
import scala.collection.immutable.HashMap
import scala.concurrent.{ ExecutionContext, Future }
import com.convergencelabs.server.domain.DomainFqn
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import scala.concurrent.duration.FiniteDuration
import scala.compat.Platform
import scala.util.Success
import scala.language.implicitConversions
import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.model.ot.TransformationFunctionRegistry
import com.convergencelabs.server.domain.model.ot.{ UnprocessedOperationEvent, ServerConcurrencyControl }
import com.convergencelabs.server.domain.model.ot.Operation
import com.convergencelabs.server.domain.model.ot.OperationTransformer
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.util.concurrent.AskFuture
import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.datastore.domain.CollectionStore
import akka.actor.Terminated
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.domain.model.ot.xform.ReferenceTransformer
import scala.collection.mutable.ListBuffer

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
    private[this] val modelOperationProcessor: ModelOperationProcessor,
    private[this] val modelSnapshotStore: ModelSnapshotStore,
    private[this] val clientDataResponseTimeout: Long,
    private[this] val snapshotConfig: ModelSnapshotConfig) extends Actor with ActorLogging {

  // This sets the actor dispatcher as an implicit execution context.  This way we
  // don't have to pass this argument to futures.
  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private[this] var connectedClients = HashMap[SessionKey, ActorRef]()
  private[this] var clientToSessionId = HashMap[ActorRef, SessionKey]()
  private[this] var queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()

  private[this] var model: RealTimeModel = _
  private[this] var latestSnapshot: ModelSnapshotMetaData = _

  private[this] val operationTransformer = new OperationTransformer(new TransformationFunctionRegistry())
  private[this] val referenceTransformer = new ReferenceTransformer(new TransformationFunctionRegistry())

  private[this] val snapshotCalculator = new ModelSnapshotCalculator(snapshotConfig)

  //
  // Receive methods
  //

  def receive: Receive = receiveUninitialized

  /**
   * Handles messages when the realtime model has not been initialized yet.
   */
  private[this] def receiveUninitialized: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileUninitialized(request)
    case unknown: Any => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from one or more clients.
   */
  private[this] def receiveInitializingFromClients: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileInitializing(request)
    case dataResponse: DatabaseModelResponse => onDatabaseModelResponse(dataResponse)
    case dataResponse: ClientModelDataResponse => onClientModelDataResponse(dataResponse)
    case ModelDeleted => handleInitializationFailure(ModelDeletedWhileOpening)
    case unknown: Any => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from the database.
   */
  private[this] def receiveInitializingFromDatabase: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileInitializing(request)
    case dataResponse: DatabaseModelResponse => onDatabaseModelResponse(dataResponse)
    case DatabaseModelFailure(cause) => handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    case ModelDeleted => handleInitializationFailure(ModelDeletedWhileOpening)
    case dataResponse: ClientModelDataResponse =>
    case unknown: Any => unhandled(unknown)
  }

  /**
   * Handles messages once the model has been completely initialized.
   */
  private[this] def receiveInitialized: Receive = {
    case openRequest: OpenRealtimeModelRequest => onOpenModelWhileInitialized(openRequest)
    case closeRequest: CloseRealtimeModelRequest => onCloseModelRequest(closeRequest)
    case operationSubmission: OperationSubmission => onOperationSubmission(operationSubmission)
    case referenceEvent: ModelReferenceEvent => onReferenceEvent(referenceEvent)
    case dataResponse: ClientModelDataResponse =>
    // This can happen if we asked several clients for the data.  The first
    // one will be handled, but the rest will come in an simply be ignored.
    case snapshotMetaData: ModelSnapshotMetaData => this.latestSnapshot = snapshotMetaData
    case ModelDeleted => handleModelDeletedWhileOpen()
    case terminated: Terminated => handleTerminated(terminated)
    case unknown: Any => unhandled(unknown)
  }

  //
  // Opening and Closing
  //

  /**
   * Starts the open process from an uninitialized model.  This only happens
   * when the first client it connecting.  Unless there is an error, after this
   * method is called, the actor will be an in initializing state.
   */
  private[this] def onOpenModelWhileUninitialized(request: OpenRealtimeModelRequest): Unit = {
    queuedOpeningClients += (SessionKey(request.userId, request.sessionId) -> OpenRequestRecord(request.clientActor, sender()))
    modelStore.modelExists(modelFqn) match {
      case Success(true) =>
        requestModelDataFromDatastore()
      case Success(false) =>
        if (request.initializerProvided) {
          requestModelDataFromClient(request.clientActor)
        } else {
          sender ! NoSuchModel
        }
      case Failure(cause) =>
        log.error(cause, "Unable to determine if a model exists.")
        handleInitializationFailure(UnknownErrorResponse(
          "Unexpected error initializing the model."))
    }
  }

  /**
   * Handles an additional request for opening the model, while the model is
   * already initializing.
   */
  private[this] def onOpenModelWhileInitializing(request: OpenRealtimeModelRequest): Unit = {
    // We know we are already INITIALIZING.  This means we are at least the second client
    // to open the model before it was fully initialized.
    queuedOpeningClients += (SessionKey(request.userId, request.sessionId) -> OpenRequestRecord(request.clientActor, sender()))

    // If we are persistent, then the data is already loading, so there is nothing to do.
    // However, if we are not persistent, we have already asked the previous opening client
    // for the data, but we will ask this client too, in case the others fail.
    modelStore.modelExists(modelFqn) match {
      case Success(false) =>
        if (request.initializerProvided) {
          // Otherwise this client has nothing for us, but there is at least one
          // other client in the mix.
          requestModelDataFromClient(request.clientActor)
        }
      case Success(true) => // No action required
      case Failure(cause) =>
        log.error(cause,
          s"Unable to determine if model exists while handling an open request for an initializing model: $domainFqn/$modelFqn")
        sender ! UnknownErrorResponse("Could not open model")
    }
  }

  /**
   * Asynchronously requests model data from the database.
   */
  private[this] def requestModelDataFromDatastore(): Unit = {
    context.become(receiveInitializingFromDatabase)

    val f = Try {
      val snapshotMetaData = modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelFqn)
      val model = modelStore.getModel(modelFqn)
      (model, snapshotMetaData) match {
        case (Success(Some(m)), Success(Some(s))) => DatabaseModelResponse(m, s)
        case _ => throw new IllegalStateException("Could not get both the model snapshot and model data")
      }
    }

    f match {
      case Success(modelDataResponse) => {
        self ! modelDataResponse
      }
      case Failure(cause) => {
        log.error(cause, "Could not initialize model from the database")
        self ! DatabaseModelFailure(cause)
      }
    }
  }

  /**
   * Handles model initialization data coming back from the database and attempts to
   * complete the initialization process.
   */
  private[this] def onDatabaseModelResponse(response: DatabaseModelResponse): Unit = {
    try {
      latestSnapshot = response.snapshotMetaData
      val modelData = response.modelData

      val startTime = Platform.currentTime
      val concurrencyControl = new ServerConcurrencyControl(
        operationTransformer,
        referenceTransformer,
        modelData.metaData.version)

      this.model = new RealTimeModel(
        modelFqn,
        modelResourceId,
        concurrencyControl,
        modelData.data)

      queuedOpeningClients foreach {
        case (sessionKey, queuedClientRecord) =>
          respondToClientOpenRequest(sessionKey, modelData, queuedClientRecord)
      }

      this.queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()
      context.become(receiveInitialized)
    } catch {
      case e: Exception =>
        log.error(e, "Unable to initialize realtime model from database response")
        handleInitializationFailure(UnknownErrorResponse("unknown error opening model"))
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
          askingActor ! ClientDataRequestFailure("The client responded with an unexpected value.")
        case e: AskTimeoutException =>
          log.debug("A timeout occured waiting for the client to respond with model data.")
          askingActor ! ClientDataRequestFailure(
            "The client did not correctly respond with data, while initializing a new model.")
        case e: Exception =>
          log.error(e, "Uknnown exception processing model data response.")
          askingActor ! UnknownErrorResponse(e.getMessage)
      }
    }

    context.become(receiveInitializingFromClients)
  }

  /**
   * Processes the model data coming back from a client.  This will persist the model and
   * then open the model from the database.
   */
  private[this] def onClientModelDataResponse(response: ClientModelDataResponse): Unit = {
    val createTime = Instant.now()
    val model = Model(
      ModelMetaData(
        modelFqn,
        0,
        createTime,
        createTime),
      response.modelData)

    modelStore.createModel(model)
    modelSnapshotStore.createSnapshot(
      ModelSnapshot(ModelSnapshotMetaData(modelFqn, 0L, createTime), response.modelData))

    requestModelDataFromDatastore()
  }

  /**
   * Handles a request to open the model, when the model is already initialized.
   */
  private[this] def onOpenModelWhileInitialized(request: OpenRealtimeModelRequest): Unit = {
    val sk = SessionKey(request.userId, request.sessionId)
    if (connectedClients.contains(sk)) {
      sender ! ModelAlreadyOpen
    } else {
      modelStore.getModel(modelFqn) match {
        case Success(Some(modelData)) => respondToClientOpenRequest(sk, modelData, OpenRequestRecord(request.clientActor, sender()))
        case Success(None) =>
          log.error(s"Could not find model data in the database for an open model: ${modelFqn}")
          sender ! UnknownErrorResponse("could not open model")
        case Failure(cause) =>
          log.error(cause, s"Could not get model data from the database for model: ${modelFqn}")
          sender ! UnknownErrorResponse("could not open model")
      }
    }
  }

  /**
   * Lets a client know that the open process has completed successfully.
   */
  private[this] def respondToClientOpenRequest(sk: SessionKey, modelData: Model, requestRecord: OpenRequestRecord): Unit = {
    // Inform the concurrency control that we have a new client.
    val contextVersion = modelData.metaData.version
    this.model.clientConnected(sk, contextVersion)
    connectedClients += (sk -> requestRecord.clientActor)
    clientToSessionId += (requestRecord.clientActor -> sk)

    // this is how we are being notified that our client is gone.
    // TODO is the the best way.
    context.watch(requestRecord.clientActor)

    // Send a message to the client informing them of the successful model open.
    val metaData = OpenModelMetaData(
      modelData.metaData.version,
      modelData.metaData.createdTime,
      modelData.metaData.modifiedTime)

    val referencesBySession = this.model.references()

    val openModelResponse = OpenModelSuccess(
      self,
      modelResourceId,
      sk.serialize(), // TODO eventually we want to use some other smaller value.
      metaData,
      connectedClients.keySet,
      referencesBySession,
      modelData.data)

    requestRecord.askingActor ! openModelResponse

    // Let other client knows
    val msg = RemoteClientOpened(modelResourceId, sk)
    connectedClients.filterKeys({ _ != sk }).foreach({
      case (session, clientActor) =>
        clientActor ! msg
    })
  }

  /**
   * Handles a request to close the model.
   */
  private[this] def onCloseModelRequest(request: CloseRealtimeModelRequest): Unit = {
    val sk = SessionKey(request.userId, request.sessionId)
    clientClosed(sk)
  }

  private[this] def handleTerminated(terminated: Terminated): Unit = {
    val sk = clientToSessionId(terminated.actor)
    clientClosed(sk)
  }

  private[this] def clientClosed(sk: SessionKey): Unit = {
    if (!connectedClients.contains(sk)) {
      sender ! ModelNotOpened
    } else {
      val clientActor = connectedClients(sk)
      clientToSessionId -= clientActor
      connectedClients -= sk
      this.model.clientDisconnected(sk)
      context.unwatch(clientActor)

      // TODO handle reference leaving

      // Acknowledge the close back to the requester
      sender ! CloseRealtimeModelSuccess()

      val closedMessage = RemoteClientClosed(modelResourceId, sk)

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
  private[this] def handleModelDeletedWhileOpen(): Unit = {
    connectedClients.keys foreach {
      sk => forceClosedModel(sk, "Model deleted", false)
    }

    context.stop(self)
  }

  //
  // Operation Handling
  //

  private[this] def onOperationSubmission(request: OperationSubmission): Unit = {
    val sessionKey = this.clientToSessionId(sender)

    val unprocessedOpEvent = UnprocessedOperationEvent(
      sessionKey,
      request.contextVersion,
      request.operation)

    transformAndApplyOperation(sessionKey, unprocessedOpEvent) match {
      case Success(outgoinOperation) => {
        broadcastOperation(sessionKey, outgoinOperation, request.seqNo)
        if (snapshotRequired()) { executeSnapshot() }
      }
      case Failure(error) => {
        log.error(error, "Error applying operation to model, closing client");
        forceClosedModel(
          sessionKey,
          "Error applying operation to model, closing as a precautionary step: " + error.getMessage,
          true)
      }
    }
  }

  /**
   * Attempts to transform the operation and apply it to the data model.
   */
  private[this] def transformAndApplyOperation(sk: SessionKey, unprocessedOpEvent: UnprocessedOperationEvent): Try[OutgoingOperation] = {
    val timestamp = Instant.now()
    this.model.processOperationEvent(unprocessedOpEvent).map { processedOpEvent =>
      modelOperationProcessor.processModelOperation(ModelOperation(
        modelFqn,
        processedOpEvent.resultingVersion,
        timestamp,
        sk.uid,
        sk.sid,
        processedOpEvent.operation))
      processedOpEvent
    }.map { processedOpEvent =>
      OutgoingOperation(
        modelResourceId,
        sk.uid,
        sk.sid,
        processedOpEvent.contextVersion,
        timestamp.toEpochMilli(),
        processedOpEvent.operation)
    }
  }

  /**
   * Sends an ACK back to the originator of the operation and an operation message
   * to all other connected clients.
   */
  private[this] def broadcastOperation(sk: SessionKey, outgoingOperation: OutgoingOperation, originSeqNo: Long): Unit = {

    // Ack the sender
    connectedClients(sk) !
      OperationAcknowledgement(modelResourceId, originSeqNo, outgoingOperation.contextVersion)

    broacastToAllOthers(outgoingOperation, sk)
  }

  //
  // References
  //
  private[this] def onReferenceEvent(request: ModelReferenceEvent): Unit = {
    val sk = this.clientToSessionId(sender)
    this.model.processReferenceEvent(request, sk.serialize()) match {
      case Success(Some(event)) =>
        broacastToAllOthers(event, sk)
      case Success(None) =>
      // Event's no-op'ed
      case _ =>
        // FIXME
        ???
    }
  }

  private[this] def broacastToAllOthers(message: Any, origin: SessionKey): Unit = {
    connectedClients.filter(p => p._1 != origin).foreach {
      case (sk, clientActor) => clientActor ! message
    }
  }

  private[this] def snapshotRequired(): Boolean = snapshotCalculator.snapshotRequired(
    latestSnapshot.version,
    model.contextVersion(),
    latestSnapshot.timestamp,
    Instant.now())

  /**
   * Asynchronously performs a snapshot of the model.
   */
  private[this] def executeSnapshot(): Unit = {
    // This might not be the exact version that gets snapshotted
    // but that is OK, this is approximate.
    latestSnapshot = ModelSnapshotMetaData(modelFqn, model.contextVersion(), Instant.now())

    val f = Future[ModelSnapshotMetaData] {
      // FIXME: Handle Failure from try and None from option.
      val modelData = modelStore.getModel(this.modelFqn).get.get
      val snapshot = new ModelSnapshot(
        ModelSnapshotMetaData(
          modelData.metaData.fqn,
          modelData.metaData.version,
          modelData.metaData.modifiedTime),
        modelData.data)

      modelSnapshotStore.createSnapshot(snapshot)

      new ModelSnapshotMetaData(
        modelFqn,
        modelData.metaData.version,
        modelData.metaData.modifiedTime)
    }

    f onSuccess {
      case snapshotMetaData: ModelSnapshotMetaData =>
        // Send the snapshot back to the model so it knows when the snapshot was actually taken.
        self ! snapshotMetaData
        log.debug(s"Snapshot successfully taken for '${modelFqn.collectionId}/${modelFqn.modelId}' " +
          s"at version: ${snapshotMetaData.version}, timestamp: ${snapshotMetaData.timestamp}")
    }

    f onFailure {
      case cause: Throwable => log.error(cause, s"Error taking snapshot of model (${modelFqn.collectionId}/${modelFqn.modelId})")
    }
  }

  //
  // Error handling
  //

  /**
   * Kicks all clients out of the model.
   */
  private[this] def forceCloseAllAfterError(reason: String): Unit = {
    connectedClients foreach {
      case (clientId, actor) => forceClosedModel(clientId, reason, false)
    }
  }

  /**
   * Kicks a specific client out of the model.
   */
  private[this] def forceClosedModel(sk: SessionKey, reason: String, notifyOthers: Boolean): Unit = {
    val closedActor = connectedClients(sk)
    connectedClients -= sk
    this.model.clientDisconnected(sk)

    // TODO handle reference node leaving

    val closedMessage = RemoteClientClosed(modelResourceId, sk)

    if (notifyOthers) {
      // There are still other clients with this model open so notify them
      // that this person has left
      connectedClients.values foreach { client => client ! closedMessage }
    }

    val forceCloseMessage = ModelForceClose(modelResourceId, reason)
    closedActor ! forceCloseMessage

    checkForConnectionsAndClose()
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  private[this] def handleInitializationFailure(response: AnyRef): Unit = {
    queuedOpeningClients.values foreach {
      openRequest => openRequest.askingActor ! response
    }

    queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()
    checkForConnectionsAndClose()
  }

  override def postStop(): Unit = {
    log.debug("Unloading Realtime Model({}/{})", domainFqn, modelFqn)
    connectedClients = HashMap()
  }

  private[this] implicit def toClientId(sk: SessionKey): String = s"${sk.uid}:${sk.sid}"
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
    modelOperationProcessor: ModelOperationProcessor,
    modelSnapshotStore: ModelSnapshotStore,
    clientDataResponseTimeout: Long,
    snapshotConfig: ModelSnapshotConfig): Props =
    Props(new RealtimeModelActor(
      modelManagerActor,
      domainFqn,
      modelFqn,
      resourceId,
      modelStore,
      modelOperationProcessor,
      modelSnapshotStore,
      clientDataResponseTimeout,
      snapshotConfig))

  def sessionKeyToClientId(sk: SessionKey): String = sk.serialize()
}

private object ErrorCodes extends Enumeration {
  val Unknown = "unknown"
  val ModelDeleted = "model_deleted"
}

case class SessionKey(uid: String, sid: String) {
  def serialize(): String = s"${uid}:${sid}"
}
