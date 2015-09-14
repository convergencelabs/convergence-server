package com.convergencelabs.server.frontend

package object realtime {
  case class ClientInterrupted(sessionId: String)
  case class ClientDisconnected(sessionId: String)
  case class ClientReconnected(socket: ConvergenceServerSocket)
}