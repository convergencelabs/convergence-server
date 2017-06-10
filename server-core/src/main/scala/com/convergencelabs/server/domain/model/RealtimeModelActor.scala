package com.convergencelabs.server.domain.model

import java.time.Instant

import scala.collection.immutable.HashMap
import scala.compat.Platform
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.UnknownErrorResponse
import com.convergencelabs.server.datastore.domain.CollectionPermissions
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
import akka.stream.ActorMaterializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

case class ModelConfigResponse(sk: SessionKey, config: ClientAutoCreateModelConfigResponse)
case object PermissionsUpdated

/**
 * Provides a factory method for creating the RealtimeModelActor
 */
object RealtimeModelActor {
  def props(
    modelManagerActor: ActorRef,
    domainFqn: DomainFqn,
    modelId: String,
    resourceId: String,
    persistenceProvider: DomainPersistenceProvider,
    modelPemrissionResolver: ModelPermissionResolver,
    modelCreator: ModelCreator,
    clientDataResponseTimeout: Long): Props =
    Props(new RealtimeModelActor(
      modelManagerActor,
      domainFqn,
      modelId,
      resourceId,
      persistenceProvider,
      modelPemrissionResolver,
      modelCreator,
      clientDataResponseTimeout))

  def sessionKeyToClientId(sk: SessionKey): String = sk.serialize()
}

/**
 * An instance of the RealtimeModelActor manages the lifecycle of a single
 * realtime model.
 */
class RealtimeModelActor(
  private[this] val modelManagerActor: ActorRef,
  private[this] val domainFqn: DomainFqn,
  private[this] val modelId: String,
  private[this] val modelResourceId: String,
  private[this] val persistenceProvider: DomainPersistenceProvider,
  private[this] val permissionsResolver: ModelPermissionResolver,
  private[this] val modelCreator: ModelCreator,
  private[this] val clientDataResponseTimeout: Long)
    extends Actor
    with ActorLogging {

  // This sets the actor dispatcher as an implicit execution context.  This way we
  // don't have to pass this argument to futures.
  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private[this] val modelStore = persistenceProvider.modelStore
  private[this] val modelOperationProcessor = persistenceProvider.modelOperationProcessor
  private[this] val modelSnapshotStore = persistenceProvider.modelSnapshotStore

  private[this] var collectionId: String = _

  private[this] var permissions: RealTimeModelPermissions = _

  private[this] var connectedClients = HashMap[SessionKey, ActorRef]()
  private[this] var clientToSessionId = HashMap[ActorRef, SessionKey]()
  private[this] var queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()

  private[this] var model: RealTimeModel = _
  private[this] var metaData: ModelMetaData = _

  private[this] var snapshotConfig: ModelSnapshotConfig = _
  private[this] var latestSnapshot: ModelSnapshotMetaData = _
  private[this] var snapshotCalculator: ModelSnapshotCalculator = _
  private[this] var ephemeral: Boolean = false

  private[this] val operationTransformer = new OperationTransformer(new TransformationFunctionRegistry())
  private[this] val referenceTransformer = new ReferenceTransformer(new TransformationFunctionRegistry())

  implicit val materializer = ActorMaterializer()
  val persistenceStream = Flow[NewModelOperation]
    .map { modelOperation =>
      modelOperationProcessor.processModelOperation(modelOperation).recover {
        case cause: Exception =>
          // FIXME this is probably altering state outside of the thread.
          // probably need to send a message.
          this.log.error(cause, "Error applying operation: " + modelOperation)
          this.forceCloseAllAfterError("There was an unexpected persistence error applying an operation.")
          ()
      }
    }.to(Sink.onComplete {
      case Success(_) =>
        // Note when we shut down we complete the persistence stream.
        // So after that is done, we kill ourselves.
        this.context.stop(self)
      case Failure(f) =>
        // FIXME this is probably altering state outside of the thread.
        // probably need to send a message.
        log.error(f, "Persistence stream completed with an error")
        this.forceCloseAllAfterError("There was an unexpected persitence error.")
        this.context.stop(self)
    }).runWith(Source
      .actorRef[NewModelOperation](bufferSize = 1000, OverflowStrategy.fail))

  //
  // Receive methods
  //

  def receive: Receive = receiveUninitialized

  /**
   * Handles messages when the realtime model has not been initialized yet.
   */
  private[this] def receiveUninitialized: Receive = {
    case request: OpenRealtimeModelRequest => onOpenModelWhileUninitialized(request)
    case PermissionsUpdated => reloadModelPermissions()
    case unknown: Any => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from one or more clients.
   */
  private[this] def receiveInitializingFromClients: Receive = {
    case request: OpenRealtimeModelRequest =>
      onOpenModelWhileInitializing(request)
    case dataResponse: ModelConfigResponse =>
      onClientAutoCreateModelConfigResponse(dataResponse)
    case ModelDeleted() =>
      handleInitializationFailure(ModelDeletedWhileOpening)
    case unknown: Any =>
      unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from the database.
   */
  private[this] def receiveInitializingFromDatabase: Receive = {
    case request: OpenRealtimeModelRequest =>
      onOpenModelWhileInitializing(request)
    case dataResponse: DatabaseModelResponse =>
      onDatabaseModelResponse(dataResponse)
    case DatabaseModelFailure(cause) =>
      handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    case ModelDeleted =>
      handleInitializationFailure(ModelDeletedWhileOpening)
    case dataResponse: ClientAutoCreateModelConfigResponse =>
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
    case PermissionsUpdated => reloadModelPermissions()
    case snapshotMetaData: ModelSnapshotMetaData => this.latestSnapshot = snapshotMetaData
    case ModelDeleted => handleModelDeletedWhileOpen()
    case terminated: Terminated => handleTerminated(terminated)
    case ModelShutdown => shutdown()

    // This can happen if we asked several clients for the data.  The first
    // one will be handled, but the rest will come in an simply be ignored.
    case dataResponse: ClientAutoCreateModelConfigResponse =>

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
    queuedOpeningClients += (request.sk -> OpenRequestRecord(request.clientActor, sender()))
    modelStore.modelExists(modelId) match {
      case Success(true) =>
        requestModelDataFromDatastore()
      case Success(false) =>
        request.autoCreateId match {
          case Some(id) =>
            requestAutoCreateConfigFromClient(request.sk, request.clientActor, id)
          case None =>
            sender ! Status.Failure(ModelNotFoundException(modelId))
        }
      case Failure(cause) =>
        log.error(cause, "Unable to determine if a model exists.")
        handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    }
  }

  /**
   * Handles an additional request for opening the model, while the model is
   * already initializing.
   */
  private[this] def onOpenModelWhileInitializing(request: OpenRealtimeModelRequest): Unit = {
    // We know we are already INITIALIZING.  This means we are at least the second client
    // to open the model before it was fully initialized.
    queuedOpeningClients += (request.sk -> OpenRequestRecord(request.clientActor, sender()))

    // If we are persistent, then the data is already loading, so there is nothing to do.
    // However, if we are not persistent, we have already asked the previous opening client
    // for the data, but we will ask this client too, in case the others fail.
    modelStore.modelExists(modelId) match {
      case Success(false) =>
        // Otherwise this client has nothing for us, but there is at least one
        // other client in the mix.
        request.autoCreateId.foreach((id) => requestAutoCreateConfigFromClient(request.sk, request.clientActor, id))
      case Success(true) => // No action required
      case Failure(cause) =>
        log.error(cause,
          s"Unable to determine if model exists while handling an open request for an initializing model: $domainFqn/$modelId")
        handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    }
  }

  /**
   * Asynchronously requests model data from the database.
   */
  private[this] def requestModelDataFromDatastore(): Unit = {
    context.become(receiveInitializingFromDatabase)
    log.debug(s"Opening model from database: ${this.modelId}")
    //    Future {
    (for {
      snapshotMetaData <- modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelId)
      model <- modelStore.getModel(modelId)
    } yield {
      (model, snapshotMetaData) match {
        case (Some(m), Some(s)) =>
          collectionId = m.metaData.collectionId
          (for {
            _ <- reloadModelPermissions()
            snapshotConfig <- getSnapshotConfigForModel(collectionId)
          } yield {
            this.snapshotConfig = snapshotConfig
            this.snapshotCalculator = new ModelSnapshotCalculator(snapshotConfig)
            self ! DatabaseModelResponse(m, s)
          }) recover {
            case cause: Exception =>
              val message = s"Error getting model permissions (${this.modelId})"
              log.error(cause, message)
              self ! DatabaseModelFailure(cause)
          }
        case _ =>
          val mMessage = model.map(_ => "found").getOrElse("not found")
          val sMessage = snapshotMetaData.map(_ => "found").getOrElse("not found")
          val message = s"Error getting model data (${this.modelId}): model: ${mMessage}, snapshot: ${sMessage}"
          val cause = new IllegalStateException(message)
          log.error(cause, message)
          self ! DatabaseModelFailure(cause)
      }
    }) recover {
      case cause: Exception =>
        val message = s"Error getting model data (${this.modelId})"
        log.error(cause, message)
        self ! DatabaseModelFailure(cause)
    }
  }

  private[this] def reloadModelPermissions(): Try[Unit] = {
    // Build a map of all current permissions so we can detect what changes.
    val currentPerms = this.connectedClients.map {
      case (sk, client) =>
        val sessionPerms = this.permissions.resolveSessionPermissions(sk)
        (sk, sessionPerms)
    }

    this.permissionsResolver
      .getModelAndCollectionPermissions(modelId, collectionId, persistenceProvider)
      .map { p =>
        this.permissions = p
        
        this.metaData = this.metaData.copy(overridePermissions = p.overrideCollection, worldPermissions = p.modelWorld)

        // Fire of an update to any client whose permissions have changed.
        this.connectedClients.foreach {
          case (sk, client) =>
            val current = this.permissions.resolveSessionPermissions(sk)
            val previous = currentPerms.get(sk)
            if (current != previous) {
              val message = ModelPermissionsChanged(this.modelResourceId, current)
              client ! message
            }
        }
        ()
      }.recover {
        case cause: Exception =>
          log.error(cause, "Error updating permissions")
          this.forceCloseAllAfterError("Error updating permissions")
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
      this.metaData = response.modelData.metaData

      val startTime = Platform.currentTime
      val concurrencyControl = new ServerConcurrencyControl(
        operationTransformer,
        referenceTransformer,
        modelData.metaData.version)

      this.model = new RealTimeModel(
        modelId,
        modelResourceId,
        concurrencyControl,
        modelData.data)

      queuedOpeningClients foreach {
        case (sessionKey, queuedClientRecord) =>
          respondToClientOpenRequest(sessionKey, modelData, queuedClientRecord)
      }

      //TODO: verify that at least one client was actually added

      this.queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()
      context.become(receiveInitialized)
    } catch {
      case cause: Exception =>
        log.error(cause,
          s"Unable to initialize the model from a the database: $domainFqn/$modelId")
        handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    }
  }

  /**
   * Asynchronously requests the model data from the connecting client.
   */
  private[this] def requestAutoCreateConfigFromClient(sk: SessionKey, clientActor: ActorRef, autoCreateId: Int): Unit = {
    val future = Patterns.ask(clientActor, ClientAutoCreateModelConfigRequest(autoCreateId), clientDataResponseTimeout)
    val askingActor = sender

    future.mapTo[ClientAutoCreateModelConfigResponse] onComplete {
      case Success(response) =>
        self ! ModelConfigResponse(sk, response)
      case Failure(cause) => cause match {
        case e: AskTimeoutException =>
          log.debug("A timeout occured waiting for the client to respond with model data.")
          this.handleQuedClientOpenFailureFailure(sk, ClientDataRequestFailure("The client did not correctly respond with data, while initializing a new model."))
        case e: Exception =>
          log.error(e, "Uknnown exception processing model data response.")
          this.handleQuedClientOpenFailureFailure(sk, UnknownErrorResponse(e.getMessage))
      }
    }

    context.become(receiveInitializingFromClients)
  }

  /**
   * Processes the model data coming back from a client.  This will persist the model and
   * then open the model from the database.
   */
  private[this] def onClientAutoCreateModelConfigResponse(response: ModelConfigResponse): Unit = {
    val ModelConfigResponse(sk, config) = response

    this.queuedOpeningClients.get(sk) match {
      case Some(openRecord) =>
        log.debug(s"Received data for model ${this.modelId} from client")
        val ClientAutoCreateModelConfigResponse(colleciton, modelData, overridePermissions, worldPermissions, userPermissions, ephemeral) = config

        val overrideWorld = overridePermissions.getOrElse(false)
        val worldPerms = worldPermissions.getOrElse(ModelPermissions(false, false, false, false))
        // FIXME see if this is correct? Specifically with the id.
        val rootObject = modelData.getOrElse(ObjectValue("0:0", Map()))
        val collectionId = config.collectionId

        this.ephemeral = ephemeral.getOrElse(false)

        log.debug(s"Creating model in database: ${this.modelId}")
        modelCreator.createModel(
          persistenceProvider,
          Some(sk.uid),
          collectionId,
          Some(modelId),
          rootObject,
          overridePermissions,
          worldPermissions,
          userPermissions) map { _ =>
            requestModelDataFromDatastore()
          } recover {
            case cause: Exception =>
              handleQuedClientOpenFailureFailure(sk, cause)
          }
      case None =>
        // Hehre we could not find the opening record, so we don't know who to respond to.
        // all we can really do is log this as an error.
        log.error("Received a model auto config response for a client that was not in our opening clients queue")
    }
  }

  /**
   * Handles a request to open the model, when the model is already initialized.
   */
  private[this] def onOpenModelWhileInitialized(request: OpenRealtimeModelRequest): Unit = {
    // FIXME see below fixme, but also it seems like we check this
    // in the respondToClientOpenRequest?
    if (permissions.resolveSessionPermissions(request.sk).read) {
      val sk = request.sk
      if (connectedClients.contains(sk)) {
        sender ! ModelAlreadyOpen
      } else {
        val model = Model(this.metaData, this.model.data.dataValue())
        respondToClientOpenRequest(sk, model, OpenRequestRecord(request.clientActor, sender()))
      }
    } else {
      sender ! Status.Failure(UnauthorizedException("Insufficient privileges to open model"))
    }
  }

  /**
   * Lets a client know that the open process has completed successfully.
   */
  private[this] def respondToClientOpenRequest(sk: SessionKey, modelData: Model, requestRecord: OpenRequestRecord): Unit = {
    if (permissions.resolveSessionPermissions(sk).read) {
      // Inform the concurrency control that we have a new client.
      val contextVersion = modelData.metaData.version
      this.model.clientConnected(sk, contextVersion)
      connectedClients += (sk -> requestRecord.clientActor)
      clientToSessionId += (requestRecord.clientActor -> sk)

      context.watch(requestRecord.clientActor)

      // Send a message to the client informing them of the successful model open.
      val metaData = OpenModelMetaData(
        modelData.metaData.modelId,
        modelData.metaData.collectionId,
        modelData.metaData.version,
        modelData.metaData.createdTime,
        modelData.metaData.modifiedTime)

      val referencesBySession = this.model.references()

      val permissions = this.permissions.resolveSessionPermissions(sk)

      val openModelResponse = OpenModelSuccess(
        self,
        modelResourceId,
        sk.serialize(), // TODO eventually we want to use some other smaller value.
        metaData,
        connectedClients.keySet,
        referencesBySession,
        modelData.data,
        permissions)

      requestRecord.askingActor ! openModelResponse

      // Let other client knows
      val msg = RemoteClientOpened(modelResourceId, sk)
      connectedClients filterKeys ({ _ != sk }) foreach {
        case (session, clientActor) =>
          clientActor ! msg
      }
    } else {
      requestRecord.askingActor ! Status.Failure(UnauthorizedException("User is not authorized to access this model"))
    }
  }

  /**
   * Handles a request to close the model.
   */
  private[this] def onCloseModelRequest(request: CloseRealtimeModelRequest): Unit = {
    clientClosed(request.sk)
  }

  private[this] def handleTerminated(terminated: Terminated): Unit = {
    clientToSessionId.get(terminated.actor) match {
      case Some(sk) =>
        clientClosed(sk)
      case None =>
        this.log.warning("An unexpected actor terminated: " + terminated.actor.path)
    }
  }

  private[this] def clientClosed(sk: SessionKey): Unit = {
    if (!connectedClients.contains(sk)) {
      sender ! ModelNotOpened
    } else {
      val closedActor = closeModel(sk, true)

      // Acknowledge the close back to the requester
      sender ! CloseRealtimeModelSuccess()

      checkForConnectionsAndClose()
    }
  }

  /**
   * Determines if there are no more clients connected and if so request to shutdown.
   */
  private[this] def checkForConnectionsAndClose(): Unit = {
    if (connectedClients.isEmpty && queuedOpeningClients.isEmpty) {
      log.debug("All clients closed the model, requesting shutdown")
      modelManagerActor ! new ModelShutdownRequest(this.modelId)
    }
  }

  /**
   * Handles the notification of a deleted model, while open.
   */
  private[this] def handleModelDeletedWhileOpen(): Unit = {
    connectedClients.keys foreach (sk => forceClosedModel(sk, "Model deleted", false))
    context.stop(self)
  }

  //
  // Operation Handling
  //

  private[this] def onOperationSubmission(request: OperationSubmission): Unit = {
    val sessionKey = this.clientToSessionId.get(sender)
    sessionKey match {
      case None => log.warning("Received operation from client for model that is not open!")
      case Some(session) => {
        if (permissions.resolveSessionPermissions(session).write) {
          val unprocessedOpEvent = UnprocessedOperationEvent(
            session,
            request.contextVersion,
            request.operation)

          transformAndApplyOperation(session, unprocessedOpEvent) match {
            case Success(outgoinOperation) =>
              broadcastOperation(session, outgoinOperation, request.seqNo)
              this.metaData = this.metaData.copy(
                version = outgoinOperation.contextVersion,
                modifiedTime = Instant.ofEpochMilli(outgoinOperation.timestamp))
                
              if (snapshotRequired()) {
                executeSnapshot()
              }
            case Failure(error) =>
              log.error(error, s"Error applying operation to model, kicking client from model: ${request}");
              forceClosedModel(
                session,
                s"Error applying operation seqNo ${request.seqNo} to model, kicking client out of model: " + error.getMessage,
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
  private[this] def transformAndApplyOperation(sk: SessionKey, unprocessedOpEvent: UnprocessedOperationEvent): Try[OutgoingOperation] = {
    val timestamp = Instant.now()
    this.model.processOperationEvent(unprocessedOpEvent).map {
      case (processedOpEvent, appliedOp) =>
        persistenceStream ! NewModelOperation(
          modelId,
          processedOpEvent.resultingVersion,
          timestamp,
          sk.sid,
          appliedOp)

        OutgoingOperation(
          modelResourceId,
          sk,
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
    connectedClients(sk) ! OperationAcknowledgement(
      modelResourceId, originSeqNo, outgoingOperation.contextVersion, outgoingOperation.timestamp)

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
      case Failure(cause) =>
        log.error(cause, "Invalid reference event")
        forceClosedModel(sk, "invalid reference event", true)
    }
  }

  private[this] def broacastToAllOthers(message: Any, origin: SessionKey): Unit = {
    connectedClients.filter(p => p._1 != origin) foreach {
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
    // but that is OK, this is approximate. we send a message to
    // send the snapshot back to the actor to refine the exact version.
    latestSnapshot = ModelSnapshotMetaData(modelId, model.contextVersion(), Instant.now())

    val f = Future[ModelSnapshotMetaData] {
      // FIXME: Handle Failure from try and None from option.
      val modelData = modelStore.getModel(this.modelId).get.get
      val snapshotMetaData = new ModelSnapshotMetaData(
        modelId,
        modelData.metaData.version,
        modelData.metaData.modifiedTime)

      val snapshot = new ModelSnapshot(snapshotMetaData, modelData.data)

      modelSnapshotStore.createSnapshot(snapshot)

      snapshotMetaData
    }

    f onSuccess {
      case snapshotMetaData: ModelSnapshotMetaData =>
        // Send the snapshot back to the model so it knows when the snapshot was actually taken.
        self ! snapshotMetaData
        log.debug(s"Snapshot successfully taken for '${modelId}' " +
          s"at version: ${snapshotMetaData.version}, timestamp: ${snapshotMetaData.timestamp}")
    }

    f onFailure {
      case cause: Throwable =>
        log.error(cause, s"Error taking snapshot of model (${modelId})")
    }
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

  //
  // Error handling
  //

  /**
   * Kicks all clients out of the model.
   */
  private[this] def forceCloseAllAfterError(reason: String): Unit = {
    log.debug("Force closing all clients after an internal error")
    connectedClients foreach {
      case (clientId, actor) => forceClosedModel(clientId, reason, false)
    }
  }

  /**
   * Kicks a specific client out of the model.
   */
  private[this] def forceClosedModel(sk: SessionKey, reason: String, notifyOthers: Boolean): Unit = {
    val closedActor = closeModel(sk, notifyOthers)

    val forceCloseMessage = ModelForceClose(modelResourceId, reason)
    closedActor ! forceCloseMessage

    checkForConnectionsAndClose()
  }

  /**
   * Closes a model for a session and return the associated actor
   *
   * @param sk The session of the client to close
   * @param notifyOthers If True notifies other connected clients of close
   * @return The actor associated with the closed session
   */
  private[this] def closeModel(sk: SessionKey, notifyOthers: Boolean): ActorRef = {
    val closedActor = connectedClients(sk)
    connectedClients -= sk
    clientToSessionId -= closedActor
    this.model.clientDisconnected(sk)
    context.unwatch(closedActor)

    if (notifyOthers) {
      // There are still other clients with this model open so notify them
      // that this person has left
      val closedMessage = RemoteClientClosed(modelResourceId, sk)
      connectedClients.values foreach { client => client ! closedMessage }
    }

    return closedActor
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  private[this] def handleInitializationFailure(response: AnyRef): Unit = {
    queuedOpeningClients.values foreach (openRequest => openRequest.askingActor ! response)
    queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()
    checkForConnectionsAndClose()
  }

  /**
   * Informs all clients that the model could not be initialized.
   */
  private[this] def handleQuedClientOpenFailureFailure(sk: SessionKey, response: AnyRef): Unit = {
    queuedOpeningClients.get(sk) foreach (openRequest => openRequest.askingActor ! response)
    queuedOpeningClients -= sk
    checkForConnectionsAndClose()
  }

  private def shutdown(): Unit = {
    log.debug(s"Model ${modelId} is shutting down.")
    this.persistenceStream ! Status.Success("stream complete")
    if (this.ephemeral) {
      log.debug(s"Model ${modelId} is ephemeral, so deleting it.")
      this.modelStore.deleteModel(this.modelId) recover {
        case cause: Exception =>
          log.error(cause, "Could not delete ephemeral model")
      }
    }
  }

  override def postStop(): Unit = {
    log.debug("Realtime Model({}/{}) stopped", domainFqn, modelId)
    connectedClients = HashMap()
  }

  private[this] implicit def toClientId(sk: SessionKey): String = s"${sk.uid}:${sk.sid}"
}

case object ModelShutdown

private object ErrorCodes extends Enumeration {
  val Unknown = "unknown"
  val ModelDeleted = "model_deleted"
}

case class SessionKey(uid: String, sid: String, admin: Boolean = false) {
  def serialize(): String = s"${uid}:${sid}"
}
