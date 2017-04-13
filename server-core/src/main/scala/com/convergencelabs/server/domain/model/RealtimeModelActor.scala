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
import com.convergencelabs.server.domain.model.ot.ProcessedOperationEvent
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.ActorMaterializer
import akka.actor.PoisonPill
import akka.actor.Status
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.UnauthorizedException

/**
 * An instance of the RealtimeModelActor manages the lifecycle of a single
 * realtime model.
 */
class RealtimeModelActor(
  private[this] val modelManagerActor: ActorRef,
  private[this] val domainFqn: DomainFqn,
  private[this] val modelFqn: ModelFqn,
  private[this] val modelResourceId: String,
  private[this] val modelStore: ModelStore,
  private[this] val modelOperationProcessor: ModelOperationProcessor,
  private[this] val modelSnapshotStore: ModelSnapshotStore,
  private[this] val clientDataResponseTimeout: Long,
  private[this] val snapshotConfig: ModelSnapshotConfig,
  private[this] val permissions: RealTimeModelPermissions)
    extends Actor
    with ActorLogging {

  // This sets the actor dispatcher as an implicit execution context.  This way we
  // don't have to pass this argument to futures.
  private[this] implicit val ec: ExecutionContext = context.dispatcher

  private[this] var overrideCollection = permissions.modelOverridesCollection
  private[this] var collectionWorld = permissions.collectionWorld
  private[this] var collectionUsers = permissions.collectionUsers
  private[this] var modelWorld = permissions.modelWorld
  private[this] var modelUsers = permissions.modelUsers

  private[this] var connectedClients = HashMap[SessionKey, ActorRef]()
  private[this] var clientToSessionId = HashMap[ActorRef, SessionKey]()
  private[this] var queuedOpeningClients = HashMap[SessionKey, OpenRequestRecord]()

  private[this] var model: RealTimeModel = _
  private[this] var latestSnapshot: ModelSnapshotMetaData = _

  private[this] val operationTransformer = new OperationTransformer(new TransformationFunctionRegistry())
  private[this] val referenceTransformer = new ReferenceTransformer(new TransformationFunctionRegistry())

  private[this] val snapshotCalculator = new ModelSnapshotCalculator(snapshotConfig)

  implicit val materializer = ActorMaterializer()
  val persistenceStream = Flow[NewModelOperation]
    .map { modelOperation =>
      modelOperationProcessor.processModelOperation(modelOperation) match {
        case Failure(f) =>
          // FIXME this is probably altering state outside of the thread.
          // probably need to send a message.
          this.log.error(f, "Error applying operation: " + modelOperation)
          this.forceCloseAllAfterError("persistence error")
        case _ =>
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
        this.forceCloseAllAfterError("persitence error")
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
    case request: OpenRealtimeModelRequest   => onOpenModelWhileUninitialized(request)
    case request: SetModelPermissionsRequest => onSetPermissionsRequest(request)
    case unknown: Any                        => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from one or more clients.
   */
  private[this] def receiveInitializingFromClients: Receive = {
    case request: OpenRealtimeModelRequest     => onOpenModelWhileInitializing(request)
    case dataResponse: ClientModelDataResponse => onClientModelDataResponse(dataResponse)
    case ModelDeleted                          => handleInitializationFailure(ModelDeletedWhileOpening)
    case unknown: Any                          => unhandled(unknown)
  }

  /**
   * Handles messages while the model is being initialized from the database.
   */
  private[this] def receiveInitializingFromDatabase: Receive = {
    case request: OpenRealtimeModelRequest     => onOpenModelWhileInitializing(request)
    case dataResponse: DatabaseModelResponse   => onDatabaseModelResponse(dataResponse)
    case DatabaseModelFailure(cause)           => handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
    case ModelDeleted                          => handleInitializationFailure(ModelDeletedWhileOpening)
    case dataResponse: ClientModelDataResponse =>
    case unknown: Any                          => unhandled(unknown)
  }

  /**
   * Handles messages once the model has been completely initialized.
   */
  private[this] def receiveInitialized: Receive = {
    case openRequest: OpenRealtimeModelRequest    => onOpenModelWhileInitialized(openRequest)
    case closeRequest: CloseRealtimeModelRequest  => onCloseModelRequest(closeRequest)
    case operationSubmission: OperationSubmission => onOperationSubmission(operationSubmission)
    case referenceEvent: ModelReferenceEvent      => onReferenceEvent(referenceEvent)
    case request: SetModelPermissionsRequest      => onSetPermissionsRequest(request)
    case snapshotMetaData: ModelSnapshotMetaData  => this.latestSnapshot = snapshotMetaData
    case ModelDeleted                             => handleModelDeletedWhileOpen()
    case terminated: Terminated                   => handleTerminated(terminated)
    case ModelShutdown                            => shutdown()

    // This can happen if we asked several clients for the data.  The first
    // one will be handled, but the rest will come in an simply be ignored.
    case dataResponse: ClientModelDataResponse    =>

    case unknown: Any                             => unhandled(unknown)
  }

  private[this] def onSetPermissionsRequest(request: SetModelPermissionsRequest): Unit = {
    val SetModelPermissionsRequest(sk, collectionId, modelId, overridePerms, world, setAllUsers, users) = request

    // TODO: Handle else case
    if (getPermissionsForSession(sk).manage) {
      val oldOverrideCollection = overrideCollection
      val oldModelWorldPermissions = modelWorld
      val oldUserModelPermissions = modelUsers

      overridePerms.foreach { overrideCollection = _ }
      world.foreach { modelWorld = _ }

      if (setAllUsers) {
        val newUsers = scala.collection.mutable.Map[String, ModelPermissions]()
        users.foreach {
          case (username, permissions) =>
            if (permissions.isDefined) {
              newUsers.put(username, permissions.get)
            }
        }
        modelUsers = newUsers.toMap
      } else {
        val newUsers = scala.collection.mutable.Map[String, ModelPermissions](modelUsers.toSeq: _*)
        users.foreach {
          case (username, permissions) =>
            if (permissions.isDefined) {
              newUsers.put(username, permissions.get)
            } else {
              newUsers.remove(username)
            }
        }
        modelUsers = newUsers.toMap
      }

      this.connectedClients foreach {
        case (sk, actor) =>
          val oldPerms = getPermissionsForSession(sk, collectionWorld, collectionUsers, oldOverrideCollection, oldModelWorldPermissions, oldUserModelPermissions)
          val newPerms = getPermissionsForSession(sk)
          if (oldPerms != newPerms) {
            actor ! ModelPermissionsChanged(modelResourceId, newPerms)
          }
      }
    }
  }

  private[this] def getPermissionsForSession(sk: SessionKey): ModelPermissions = {
    getPermissionsForSession(sk, collectionWorld, collectionUsers, overrideCollection, modelWorld, modelUsers)
  }

  private[this] def getPermissionsForSession(
    sk: SessionKey, cWorld: CollectionPermissions, cUsers: Map[String, CollectionPermissions],
    overridePerm: Boolean, mWorld: ModelPermissions, mUsers: Map[String, ModelPermissions]): ModelPermissions = {
    if (sk.admin) {
      ModelPermissions(true, true, true, true)
    } else {
      if (overridePerm) {
        mUsers.getOrElse(sk.uid, mWorld)
      } else {
        val CollectionPermissions(create, read, write, remove, manage) = cUsers.getOrElse(sk.uid, cWorld)
        ModelPermissions(read, write, remove, manage)
      }
    }

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
    if (getPermissionsForSession(request.sk).read) {
      queuedOpeningClients += (request.sk -> OpenRequestRecord(request.clientActor, sender()))
      modelStore.modelExists(modelFqn.modelId) match {
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
          handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
      }
    } else {
      // TODO: Need to think about what to do in this case. This can only happen if a users permissions change
      //       after calling open on the model manager but before we get here
      sender ! Status.Failure(UnauthorizedException("Insufficient privileges to open model"))
    }
  }

  /**
   * Handles an additional request for opening the model, while the model is
   * already initializing.
   */
  private[this] def onOpenModelWhileInitializing(request: OpenRealtimeModelRequest): Unit = {
    if (getPermissionsForSession(request.sk).read) {
      // We know we are already INITIALIZING.  This means we are at least the second client
      // to open the model before it was fully initialized.
      queuedOpeningClients += (request.sk -> OpenRequestRecord(request.clientActor, sender()))

      // If we are persistent, then the data is already loading, so there is nothing to do.
      // However, if we are not persistent, we have already asked the previous opening client
      // for the data, but we will ask this client too, in case the others fail.
      modelStore.modelExists(modelFqn.modelId) match {
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
          handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
      }
    } else {
      sender ! Status.Failure(UnauthorizedException("Insufficient privileges to open model"))
    }
  }

  /**
   * Asynchronously requests model data from the database.
   */
  private[this] def requestModelDataFromDatastore(): Unit = {
    context.become(receiveInitializingFromDatabase)
    log.debug(s"Opening model from database: ${this.modelFqn}")
    //    Future {
    (for {
      snapshotMetaData <- modelSnapshotStore.getLatestSnapshotMetaDataForModel(modelFqn.modelId)
      model <- modelStore.getModel(modelFqn.modelId)
    } yield {
      (model, snapshotMetaData) match {
        case (Some(m), Some(s)) =>
          self ! DatabaseModelResponse(m, s)
        case _ =>
          val mMessage = model.map(_ => "found").getOrElse("not found")
          val sMessage = snapshotMetaData.map(_ => "found").getOrElse("not found")
          val message = s"Error getting model data (${this.modelFqn}): model: ${mMessage}, snapshot: ${sMessage}"
          val cause = new IllegalStateException(message)
          log.error(cause, message)
          self ! DatabaseModelFailure(cause)
      }
    }) recover {
      case cause: Exception =>
        val message = s"Error getting model data (${this.modelFqn})"
        log.error(cause, message)
        self ! DatabaseModelFailure(cause)
    }
    //    }
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
      case cause: Exception =>
        log.error(cause,
          s"Unable to initialize the model from a the database: $domainFqn/$modelFqn")
        handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
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
      case Success(response) =>
        self ! response
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
    // The next step will to get the data from the database. So we will set this now,
    // this ensures that we don't take more data from another clinet.
    context.become(receiveInitializingFromDatabase)

    log.debug(s"Received data for model ${this.modelFqn} from client")
    val ClientModelDataResponse(modelData, overridePermissions, worldPermissions) = response

    log.debug(s"Creating model in database: ${this.modelFqn}")
    val overrideWorld = overridePermissions.getOrElse(false)
    val worldPerms = worldPermissions.getOrElse(ModelPermissions(false, false, false, false))
    modelStore.createModel(
      modelFqn.collectionId, Some(modelFqn.modelId), modelData, overrideWorld, worldPerms) flatMap { model =>
        log.debug(s"Model created in database: ${this.modelFqn}")

        val Model(
          ModelMetaData(fqn, version, createdTime, modifiedTime, overridePermissions, worldPermissions),
          data) = model

        log.debug(s"Creating initial snapshot database: ${this.modelFqn}")
        modelSnapshotStore.createSnapshot(
          ModelSnapshot(ModelSnapshotMetaData(modelFqn, version, createdTime), response.modelData))
      } map { _ =>
        log.debug(s"Initial snapshot created in database: ${this.modelFqn}")
        requestModelDataFromDatastore()
      } recover {
        case cause: Exception =>
          log.error(cause,
            s"Unable to initialize the model from a client initializer: $domainFqn/$modelFqn")
          handleInitializationFailure(UnknownErrorResponse("Unexpected error initializing the model."))
      }
  }

  /**
   * Handles a request to open the model, when the model is already initialized.
   */
  private[this] def onOpenModelWhileInitialized(request: OpenRealtimeModelRequest): Unit = {
    if (getPermissionsForSession(request.sk).read) {
      val sk = request.sk
      if (connectedClients.contains(sk)) {
        sender ! ModelAlreadyOpen
      } else {
        modelStore.getModel(modelFqn.modelId) match {
          case Success(Some(modelData)) => respondToClientOpenRequest(sk, modelData, OpenRequestRecord(request.clientActor, sender()))
          case Success(None) =>
            log.error(s"Could not find model data in the database for an open model: ${modelFqn}")
            sender ! UnknownErrorResponse("could not open model")
          case Failure(cause) =>
            log.error(cause, s"Could not get model data from the database for model: ${modelFqn}")
            sender ! UnknownErrorResponse("could not open model")
        }
      }
    } else {
      sender ! Status.Failure(UnauthorizedException("Insufficient privileges to open model"))
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

    context.watch(requestRecord.clientActor)

    // Send a message to the client informing them of the successful model open.
    val metaData = OpenModelMetaData(
      modelData.metaData.version,
      modelData.metaData.createdTime,
      modelData.metaData.modifiedTime)

    val referencesBySession = this.model.references()

    val permissions = this.getPermissionsForSession(sk)

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
    if (connectedClients.isEmpty) {
      log.debug("All clients closed the model, requesting shutdown")
      modelManagerActor ! new ModelShutdownRequest(this.modelFqn)
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
        if (getPermissionsForSession(session).write) {
          val unprocessedOpEvent = UnprocessedOperationEvent(
            session,
            request.contextVersion,
            request.operation)

          transformAndApplyOperation(session, unprocessedOpEvent) match {
            case Success(outgoinOperation) =>
              broadcastOperation(session, outgoinOperation, request.seqNo)
              if (snapshotRequired()) { executeSnapshot() }
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
          modelFqn,
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
    latestSnapshot = ModelSnapshotMetaData(modelFqn, model.contextVersion(), Instant.now())

    val f = Future[ModelSnapshotMetaData] {
      // FIXME: Handle Failure from try and None from option.
      val modelData = modelStore.getModel(this.modelFqn.modelId).get.get
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
      case cause: Throwable =>
        log.error(cause, s"Error taking snapshot of model (${modelFqn.collectionId}/${modelFqn.modelId})")
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

  private def shutdown(): Unit = {
    this.persistenceStream ! Status.Success("stream complete")
  }

  override def postStop(): Unit = {
    log.debug("Realtime Model({}/{}) stopped", domainFqn, modelFqn)
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
    snapshotConfig: ModelSnapshotConfig,
    permissions: RealTimeModelPermissions): Props =
    Props(new RealtimeModelActor(
      modelManagerActor,
      domainFqn,
      modelFqn,
      resourceId,
      modelStore,
      modelOperationProcessor,
      modelSnapshotStore,
      clientDataResponseTimeout,
      snapshotConfig,
      permissions))

  def sessionKeyToClientId(sk: SessionKey): String = sk.serialize()
}

case object ModelShutdown

private object ErrorCodes extends Enumeration {
  val Unknown = "unknown"
  val ModelDeleted = "model_deleted"
}

case class SessionKey(uid: String, sid: String, admin: Boolean = false) {
  def serialize(): String = s"${uid}:${sid}"
}
