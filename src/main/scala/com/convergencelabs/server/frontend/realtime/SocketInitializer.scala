package com.convergencelabs.server.frontend.realtime

import com.convergencelabs.server.frontend.realtime.proto.MessageEnvelope
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization.{ read, write }
import org.json4s.NoTypeHints
import org.json4s.Extraction
import akka.actor.Scheduler
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit
import scala.concurrent.ExecutionContext
import grizzled.slf4j.Logging
import scala.util.Success
import scala.util.Failure
import com.convergencelabs.server.frontend.realtime.proto.HandshakeRequestMessage

class SocketInitializer(
    private[this] val newSocketEvent: NewSocketEvent,
    private[this] val scheduler: Scheduler,
    private[this] val ec: ExecutionContext,
    private[this] val callback: Function2[NewSocketEvent, HandshakeRequestMessage, Unit]) extends Logging {

  val socket = newSocketEvent.socket

  socket.handler = {
    case SocketMessage(message) => onSocketMessage(message)
    case SocketClosed() => onSocketClosed()
    case SocketDropped() => onSocketDropped()
    case SocketError(message) => onSocketError(message)
  }

  val timeoutTask = scheduler.scheduleOnce(Duration.create(5, TimeUnit.SECONDS))(() => {
    socket.abort("Handhsake timeout")
    socket.dispose()
  })(ec)

  private[this] def onSocketMessage(json: String): Unit = {
    // FIXME we need more error handling
    val canceled = timeoutTask.cancel()

    if (!canceled) {
      // we already timed out.
      return
    }

    MessageEnvelope(json) match {
      case Success(envelope) => {
        envelope.extractBody() match {
          case message: HandshakeRequestMessage => callback(newSocketEvent, message)
          case _ =>
            logger.warn("Unexpected message received instead of a handshake request")
            socket.abort("Unexpected message on handshake")
        }
      }
      case Failure(cause) => {
        // FIXME
      }
    }
  }

  private[this] def onSocketClosed(): Unit = {
    timeoutTask.cancel()
    socket.dispose()
  }

  private[this] def onSocketDropped(): Unit = {
    timeoutTask.cancel()
    socket.dispose()
  }

  private[this] def onSocketError(message: String): Unit = {
    timeoutTask.cancel()
    socket.abort(message)
    socket.dispose()
  }
}