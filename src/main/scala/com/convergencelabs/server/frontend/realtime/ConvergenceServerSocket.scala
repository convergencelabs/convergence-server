package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage

sealed trait ConvergenceServerSocketEvent
case class SocketMessage(message: String) extends ConvergenceServerSocketEvent
case class SocketError(errorMessage: String) extends ConvergenceServerSocketEvent
case class SocketClosed() extends ConvergenceServerSocketEvent
case class SocketDropped() extends ConvergenceServerSocketEvent

abstract class ConvergenceServerSocket {

  var handler: PartialFunction[ConvergenceServerSocketEvent, Unit] = null

  var sessionId: String = null

  def send(message: String): Unit

  def isOpen(): Boolean

  def close(): Unit

  def abort(reason: String): Unit

  def dispose(): Unit

  def fireOnError(errorMessage: String): Unit =
    if (handler != null) handler lift SocketError(errorMessage)

  def fireOnMessage(message: String): Unit =
    if (handler != null) handler lift SocketMessage(message)

  def fireOnClosed(): Unit =
    if (handler != null) handler lift SocketClosed()

  def fireOnDropped(): Unit =
    if (handler != null) handler lift SocketDropped()
}