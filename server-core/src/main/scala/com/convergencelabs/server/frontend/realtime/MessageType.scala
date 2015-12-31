package com.convergencelabs.server.frontend.realtime

object MessageType extends Enumeration {
  val Error = "error"
  val Handshake = "handshake"

  val AuthPassword = "authPassword"
  val AuthToken = "authToken"

  val OpenRealtimeModel = "openRealtimeModel"
  val CloseRealtimeModel = "closeRealtimeModel"

  val ModelDataRequest = "modelData"
  val OperationSubmission = "opSubmit"
  val OperationAck = "opAck"
}
