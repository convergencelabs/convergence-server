package com.convergencelabs.server.domain.model.ot.cc

import com.convergencelabs.server.domain.model.ot.xform.OperationTransformer
import grizzled.slf4j.Logging
import com.convergencelabs.server.domain.model.ot.ops.Operation
import org.apache.commons.lang3.Validate
import scala.collection.mutable

/**
 * The ServerConcurrencyControl class implements the server side Operational Transformation Control Algorithm.  It is
 * responsible for determining which operations are concurrent and ensuring the concurrent operations are correctly
 * transformed against one another.
 *
 * @param operationTransformer The OperationTransformer instance that this concurrency control will us to transform
 *                             operations with.
 *
 * @param initialContextVersion The initial context version this object will be set to.
 */
class ServerConcurrencyControl(
    private[this] val operationTransformer: OperationTransformer,
    private[this] var initialContextVersion: Long) extends Logging {

  Validate.notNull(operationTransformer, "operationTransformer must not be null")
  Validate.isTrue(initialContextVersion >= 0, "initialContextVersion must be >= 0: ", initialContextVersion)

  private val clientStates = mutable.HashMap[String, ClientConcurrencyState]()
  private var operationHistoryCache = List[ProcessedOperationEvent]()
  private var pendingEvent: ProcessedOperationEvent = null
  private var pendingClientState: ClientConcurrencyState = null

  private var _contextVersion = initialContextVersion

  /**
   * Processes an operation event from a client.  The client must be tracked before operations can be processed from
   * it.  After this method is called, there will be a pending event.  The caller must either call commit or
   * rollback in order to complete the operation.  This method may not be called again until the pending event is
   * committed or rolled back.
   *
   * @param remoteOpEvent The remote event to process.
   * @return A ProcessedOperationEvent which contains a transformed version of the operation.
   */
  def processRemoteOperation(remoteOpEvent: UnprocessedOperationEvent): ProcessedOperationEvent = {
    if (pendingEvent != null) {
      throw new IllegalStateException("The previous operation must be committed or rolled back " +
        "before a new operation can before the next operation can be processed.")
    }
    validateOperationEvent(remoteOpEvent)

    val remoteClientId = remoteOpEvent.clientId
    val remoteOperation = remoteOpEvent.operation
    val clientContextVersion = remoteOpEvent.contextVersion

    val clientState = clientStates(remoteClientId)

    var newStatePath = clientState.branchedStatePath.filter(event => {
      event.contextVersion >= clientContextVersion
    })

    val firstVersionFromGlobalHistory = {
      if (newStatePath.isEmpty) {
        // There are no operations in the client's state path.  This means the client's context version has
        // moved beyond the last operation that was in the client's previous state path.  Therefore, the
        // first operation we need from the global ordering is the operation that has the same context
        // version as the incoming operation, since that would be the first operation the incoming
        // operation was concurrent with
        remoteOpEvent.contextVersion
      } else {
        // We still have a prior transformed version of one of the operations in the global ordering of
        // operations.  Therefore, the first one we need from th history is the one with the context
        // version right after the last one in the clients new state path
        newStatePath.last.contextVersion + 1
      }
    }

    // We now get any operations from the operationHistoryCache that are concurrent with the incoming
    // operation, and concatenate that we anything left over from this client branched state.  We filter
    // out any operations that are from the same client.
    newStatePath = newStatePath ++ operationHistoryCache.filter(event => {
      event.contextVersion >= firstVersionFromGlobalHistory && event.clientId != remoteClientId
    })

    // Now we transform the incoming operation across all previous operations.  Those from the
    // branched state path, that connects to the servers state path, and then along the
    // servers state path to the current state.
    val (xFormedOp, xFormedStatePath) = transform(remoteClientId, newStatePath, remoteOperation)

    // The results are stored as a pending event, waiting for a commit.
    pendingEvent = new ProcessedOperationEvent(
      remoteClientId,
      _contextVersion,
      xFormedOp)

    // If committed this will become the new state for the client.
    pendingClientState = clientState.copy(
      contextVersion = clientContextVersion,
      branchedStatePath = xFormedStatePath)

    pendingEvent
  }

  /**
   * Gets the current context version of the server.
   * @return The current context version.
   */
  def contextVersion = _contextVersion

  /**
   * Makes the concurrency control aware of a new client.  The client must have a unique identifier within the system.
   * Once added the concurrency control will track the state path of this client to ensure it can transform incoming
   * operations on to the global state path.  A client MUST be tracked before an operation can be processed from it.
   *
   * @param clientId The unique identifier for this client.
   * @param contextVersion The clients initial context version when it was added.
   */
  def trackClient(clientId: String, contextVersion: Long) {
    if (clientStates.contains(clientId)) {
      throw new IllegalArgumentException(s"A client with id '$clientId' has already been added.")
    }

    if (contextVersion > this._contextVersion) {
      throw new IllegalArgumentException(s"A client can not be added with a later context version than the server")
    }

    clientStates(clientId) = ClientConcurrencyState(clientId, contextVersion, List())
  }

  /**
   * Removes a remote client from this concurrency control.  Once removed, the concurrency control will no longer track
   * the state of the client.  The client must currently be tracked before calling this method.  After calling
   * untrackClient, no further operations can be processed from that client.
   *
   * @param clientId The client to untrack.
   */
  def untrackClient(clientId: String) {
    if (!clientStates.contains(clientId)) {
      throw new IllegalArgumentException(s"A client with id '$clientId' is not being tracked.")
    }

    clientStates.remove(clientId)
  }

  /**
   * Determines is a specific client is being tracked, and ready to process operations.
   * @param clientId The identifier of the client.
   * @return True fi the client is being tracked, false otherwise.
   */
  def isClientTracked(clientId: String) = this.clientStates.contains(clientId)

  /**
   * Determines if an operation has been processed that has yet to be committed or rolled back. If an event is
   * pending, it must be committed or rolled back before another operation can be submitted.
   *
   * @return True if there is an outstanding operation to commit, false otherwise.
   */
  def hasPendingEvent: Boolean = pendingEvent != null

  /**
   * Commits the pending event.  May only be called when hasPendingEvent returns true.
   */
  def commit() {
    if (pendingEvent == null) {
      throw new IllegalStateException("Can't call commit when there is no pending operation event.")
    }

    this._contextVersion += 1
    clientStates(this.pendingClientState.clientId) = pendingClientState
    val minContextVersion = minimumContextVersion()

    operationHistoryCache.filter(event => {
      event.contextVersion >= minContextVersion
    })
    operationHistoryCache = operationHistoryCache :+ pendingEvent

    pendingEvent = null
    pendingClientState = null
  }

  /**
   * Rolls back the pending operation, removing its effects.  May only be called when hasPendingEvent returns true.
   */
  def rollback() {
    if (pendingEvent == null) {
      throw new IllegalStateException("Can't call rollback when there is no pending operation event.")
    }

    pendingEvent = null
    pendingClientState = null
  }

  private def transform(opClientId: String, historyOperationEvents: List[ProcessedOperationEvent], incomingOp: Operation): (Operation, List[ProcessedOperationEvent]) = {
    var xFormedOp = incomingOp
    val xFormedList = historyOperationEvents.map(event => {
      val pair = operationTransformer.transform(xFormedOp, event.operation)
      xFormedOp = pair._1
      event.copy(operation = pair._2)
    })

    (xFormedOp, xFormedList)
  }

  private def minimumContextVersion(): Long = {
    var version = Long.MaxValue
    clientStates.values.foreach(state => {
      version = Math.min(state.contextVersion, version)
    })

    version
  }

  private def validateOperationEvent(incomingOperation: UnprocessedOperationEvent) {
    val clientId = incomingOperation.clientId

    if (!clientStates.contains(clientId)) {
      throw new IllegalArgumentException(s"The server received an operation from an unknown client: $clientId")
    }

    val clientState = clientStates(incomingOperation.clientId)
    val currentContextVersion = clientState.contextVersion

    if (incomingOperation.contextVersion > _contextVersion) {
      throw new IllegalArgumentException(
        s"The context version of an incoming remote operation (${incomingOperation.contextVersion}}) " +
          s"can not be greater than the server's context version (${_contextVersion})")
    }

    if (incomingOperation.contextVersion < currentContextVersion) {
      throw new IllegalArgumentException(
        s"The server received an operation for client ($clientId) with an earlier context version (${incomingOperation.contextVersion}) " +
          s"than the last known context version for that client ($currentContextVersion).")
    }
  }
}

/**
 * The ClientConcurrencyState holds the concurrency state for a specific client.
 *
 * @param clientId The identifier of the client.
 * @param contextVersion The currently known context version of that client.
 * @param branchedStatePath The set of operations (other than those from the client it self) that move it from
 *                          the contextVersion back to the servers state path.
 */
private case class ClientConcurrencyState(clientId: String, contextVersion: Long, branchedStatePath: List[ProcessedOperationEvent])

/**
 * The UnprocessedOperationEvent represents a incoming operation from the a client.  The operation may not be
 * contextualized to the head of the servers state path, and may therefore require transformation.
 *
 * @param clientId  The string id of the client that submitted the operation.
 * @param contextVersion The context version of the operation when it was generated by the client.  This is the point at
 *                       which the client branched of the servers state path.
 * @param operation The operation that was performed.
 */
case class UnprocessedOperationEvent(clientId: String, contextVersion: Long, operation: Operation)

/**
 * The ProcessedOperationEvent represents an operation that has been contextualized on to the servers state path.  This
 * is the final state the operation will be in as it enters the globally ordered operation history.
 *
 * @param clientId The string id of the client that submitted the operation.
 * @param contextVersion The context version of the operation after any required transformation.
 * @param operation The (potentially) transformed operation.
 */
case class ProcessedOperationEvent(clientId: String, contextVersion: Long, operation: Operation) {
  val resultingVersion = contextVersion + 1
}