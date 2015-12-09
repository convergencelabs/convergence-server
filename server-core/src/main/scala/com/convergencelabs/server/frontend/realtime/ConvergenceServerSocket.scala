package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage

sealed trait ConvergenceServerSocketEvent
case class SocketMessage(message: String) extends ConvergenceServerSocketEvent
case class SocketError(errorMessage: String) extends ConvergenceServerSocketEvent
case class SocketClosed() extends ConvergenceServerSocketEvent
case class SocketDropped() extends ConvergenceServerSocketEvent

trait ConvergenceServerSocket {

  var handler: PartialFunction[ConvergenceServerSocketEvent, Unit] = { case _ => }

  var sessionId: String = null

  def send(message: String): Unit

  def isOpen(): Boolean

  def close(): Unit = this.close("closed normally")

  def close(reason: String): Unit

  def abort(reason: String): Unit

  def fireOnError(errorMessage: String): Unit =
    handler lift SocketError(errorMessage)

  def fireOnMessage(message: String): Unit =
    handler lift SocketMessage(message)

  def fireOnClosed(): Unit =
    handler lift SocketClosed()

  def fireOnDropped(): Unit =
    handler lift SocketDropped()
}