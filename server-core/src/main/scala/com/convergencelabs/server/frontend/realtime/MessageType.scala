package com.convergencelabs.server.frontend.realtime

object MessageType extends Enumeration {
  val Error = "error"
  val Handshake = "handshake"

  val Authentication = "authenticate"

  val CreateRealtimeModel = "createRealTimeModel"
  val DeleteRealtimeModel = "deleteRealTimeModel"
  val OpenRealtimeModel = "openRealTimeModel"
  val CloseRealtimeModel = "closeRealTimeModel"
  val ModelForceClose = "forceCloseRealTimeModel"

  val ModelDataRequest = "modelData"
  val OperationSubmission = "opSubmit"
  val OperationAck = "opAck"
  val RemoteOperation = "remoteOp"
}
