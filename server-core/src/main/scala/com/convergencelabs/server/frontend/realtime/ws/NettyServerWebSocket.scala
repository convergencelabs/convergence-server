package com.convergencelabs.server.frontend.realtime.ws

import java.io.IOException
import io.netty.channel.Channel
import org.apache.commons.lang3.Validate
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import grizzled.slf4j.Logging
import java.util.concurrent.atomic.AtomicLong
import scala.util.Success
import scala.util.Failure
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import com.convergencelabs.server.frontend.realtime.proto.ProtocolMessage
import com.convergencelabs.server.frontend.realtime.ConvergenceServerSocket
import io.netty.channel.ChannelFuture

private[ws] object NettyServerWebSocket {
  private var socketId = new AtomicLong(0)
}

private[ws] class NettyServerWebSocket(
    private[this] val channel: Channel, 
    private[this] val maxFrameSize: Int) 
    extends ConvergenceServerSocket with Logging {

  Validate.notNull(channel, "channel must not be null.")

  private[this] val socketId = NettyServerWebSocket.socketId.getAndIncrement();

  debug(s"(ws-$socketId) New socket created")

  private[ws] def onMessageReceived(message: String) {
    try {
        fireOnMessage(message)
    } catch {
      case e: Exception => {
        val errorMessage = s"Error receiving web socket message for session($sessionId): $message"
        if (isDebugEnabled) {
          error(errorMessage, e)
        } else {
          error(errorMessage + "\n" + e.getMessage())
        }
      }
    }
  }

  def send(message: String): Unit = {
    if (!channel.isOpen()) {
      trace(s"Can't send message because channel is closed: textMessage")
    }

    val frameCount = Math.ceil(message.length / this.maxFrameSize).toInt

    try {
      var f = if (frameCount == 1) {
        channel.writeAndFlush(new TextWebSocketFrame(message))
      } else {
        var future: ChannelFuture = null
        
        val lastFrame = frameCount - 1
        for (i <- 0 to frameCount) {
          val start = i * maxFrameSize
          val end = Math.min(start + maxFrameSize, message.length())
          val frameText = message.substring(start, end)
          i match {
            case 0 => channel.writeAndFlush(new TextWebSocketFrame(frameText))
            case _ => future = channel.writeAndFlush(
                new ContinuationWebSocketFrame(i == lastFrame, 0, frameText))
          }
          future
        }
      }
      
    } catch {
      case e: IOException =>
        val message = s"Error sending web socket text message for session($sessionId): textMessage"
        if (isTraceEnabled) {
          trace(message, e)
        } else {
          debug(message + ": " + e.getMessage())
        }
        onError(message)
    }
  }

  private[ws] def handleClosed(code: Int, reason: String): Unit = {
    debug(s"(ws-$socketId) WebSocket connection closed: [code: $code: reason: '$reason'']")

    if (code == 1000 || code == 1001) {
      fireOnClosed()
    } else {
      fireOnDropped()
    }
  }

  def isOpen(): Boolean = {
    return channel.isOpen()
  }

  def close(reason: String): Unit = {
    try {
      if (this.isOpen()) {
        val closeFrame = new CloseWebSocketFrame(1000, reason)
        channel.writeAndFlush(closeFrame)
      }
    } catch {
      case e: Exception => {
        debug(s"Error closing session: $sessionId", e)
      }
    }
  }

  def abort(reason: String): Unit = {
    try {
      if (this.isOpen()) {
        val closeFrame = new CloseWebSocketFrame(4006, reason)
        channel.writeAndFlush(closeFrame)
      }
    } catch {
      case e: Exception => debug(s"Error closing session: $sessionId", e)
    }
  }

  private[this] def onError(message: String): Unit = {
    fireOnError(message)
    try {
      if (channel.isOpen()) {
        val closeFrame = new CloseWebSocketFrame(4006, message)
        channel.writeAndFlush(closeFrame)
      }
    } catch {
      case e1: Exception => logger.error("Error closing misbehaving WebSocket", e1)
    }
  }
}