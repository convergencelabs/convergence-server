package com.convergencelabs.server.frontend.realtime

import scala.collection.mutable.Queue

sealed trait ConvergenceServerSocketEvent
case class SocketMessage(message: String) extends ConvergenceServerSocketEvent
case class SocketError(errorMessage: String) extends ConvergenceServerSocketEvent
case class SocketClosed() extends ConvergenceServerSocketEvent
case class SocketDropped() extends ConvergenceServerSocketEvent

object ConvergenceServerSocket {
  private val NoOpHandler: PartialFunction[ConvergenceServerSocketEvent, Unit] = { case _ => }
}

trait ConvergenceServerSocket {

  private[this] object Lock
  private[this] val eventQueue = Queue[ConvergenceServerSocketEvent]()
  private[this] var ready: Boolean = false;
  private[this] var handler = ConvergenceServerSocket.NoOpHandler;

  var sessionId: String = null

  def ready(): Unit = {
    Lock.synchronized({
      if (this.handler == ConvergenceServerSocket.NoOpHandler) {
        throw new IllegalStateException("Can not call ready until the handler has been set")
      }
      this.ready = true;
      eventQueue.toList.foreach { x => fire(x) }
      eventQueue.clear()
    })
  }

  def setHandler(handler: PartialFunction[ConvergenceServerSocketEvent, Unit]): Unit = {
    if (this.handler != ConvergenceServerSocket.NoOpHandler) {
      throw new IllegalStateException("Handler already set.")
    }
    this.handler = handler
  }

  def send(message: String): Unit

  def isOpen(): Boolean

  def close(): Unit = this.close("closed normally")

  def close(reason: String): Unit

  def abort(reason: String): Unit

  def fireOnError(errorMessage: String): Unit =
    fire(SocketError(errorMessage))

  def fireOnMessage(message: String): Unit =
    fire(SocketMessage(message))

  def fireOnClosed(): Unit =
    fire(SocketClosed())

  def fireOnDropped(): Unit =
    fire(SocketDropped())

  private[this] def fire(event: ConvergenceServerSocketEvent): Unit = {
    Lock.synchronized({
      if (ready) {
        handler lift event
      } else {
        this.eventQueue.enqueue(event)
      }
    })
  }
}
