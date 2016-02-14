package com.convergencelabs.server.frontend.realtime.ws

import io.netty.channel.SimpleChannelInboundHandler
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.http.FullHttpRequest
import io.netty.handler.codec.http.websocketx.WebSocketFrame
import io.netty.handler.codec.http.HttpMethod
import io.netty.handler.codec.http.DefaultFullHttpResponse
import io.netty.handler.codec.http.HttpVersion
import io.netty.handler.codec.http.HttpResponseStatus
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame
import io.netty.handler.codec.http.HttpHeaders
import io.netty.channel.ChannelFutureListener
import io.netty.handler.codec.http.FullHttpResponse
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame
import io.netty.buffer.Unpooled
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker
import io.netty.util.CharsetUtil
import grizzled.slf4j.Logging
import java.net.URI
import com.convergencelabs.server.domain.DomainFqn
import io.netty.handler.codec.http.websocketx.ContinuationWebSocketFrame
import com.convergencelabs.server.frontend.realtime.SocketConnectionHandler

private[ws] class WebSocketServerHandler(maxFrameSize: Int, socketConnectionHandler: SocketConnectionHandler)
    extends SimpleChannelInboundHandler[Object]
    with Logging {

  private[this] val WEBSOCKET_PATH = "/domain/"
  private[this] var handshaker: Option[WebSocketServerHandshaker] = None
  private[this] var convergenceSocket: Option[NettyServerWebSocket] = None
  private[this] var closeFrameReceieve = false
  private[this] val textFrameBuffer = new StringBuilder()

  trace("New Netty connection initiated")

  def channelRead0(ctx: ChannelHandlerContext, msg: Object): Unit = {
    if (msg.isInstanceOf[FullHttpRequest]) {
      handleHttpRequest(ctx, msg.asInstanceOf[FullHttpRequest])
    } else if (msg.isInstanceOf[WebSocketFrame]) {
      handleWebSocketFrame(ctx, msg.asInstanceOf[WebSocketFrame])
    }
  }

  override def channelReadComplete(ctx: ChannelHandlerContext): Unit = {
    ctx.flush()
  }

  override def channelInactive(ctx: ChannelHandlerContext): Unit = {
    super.channelInactive(ctx)

    if (!closeFrameReceieve) {
      trace("Channel closed unexpectedly")
      if (convergenceSocket.isDefined) {
        convergenceSocket.get.handleClosed(CloseCodes.ConvergenceAbnormal, "Unexpectedly closed by peer")
      }
    } else {
      trace("Channel closed after close frame recieved")
    }
  }

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable): Unit = {
    cause.printStackTrace()
    ctx.close()
  }

  private[this] def handleHttpRequest(ctx: ChannelHandlerContext, req: FullHttpRequest): Unit = {
    if (!req.getDecoderResult().isSuccess()) {
      // Handle a bad request.
      sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
    } else if (req.getMethod() != HttpMethod.GET) {
      // Allow only GET methods.
      sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.FORBIDDEN))
    } else {
      // Handshake
      // scalastyle:off null
      val wsFactory = new WebSocketServerHandshakerFactory(getWebSocketLocation(req), null, true, maxFrameSize)
      // scalastyle:on null
      handshaker = Some(wsFactory.newHandshaker(req))
      if (handshaker.isEmpty) {
        WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx.channel())
      } else {
        getDomainFqnForRequest(req) match {
          case None => handshaker.get.close(ctx.channel(), new CloseWebSocketFrame(CloseCodes.Normal, "Invalid domain url."))
          case Some(domainFqn) => {
            val namespace = domainFqn.namespace
            val domainId = domainFqn.domainId
            val channelId = ctx.channel().hashCode()
            debug(s"Incoming web socket connecting to '$namespace/$domainId': $channelId")

            trace(s"Completing handshake: $channelId")
            handshaker.get.handshake(ctx.channel(), req)
            convergenceSocket = Some(new NettyServerWebSocket(ctx.channel(), maxFrameSize))

            socketConnectionHandler.fireOnSocketOpen(domainFqn, convergenceSocket.get)
          }
        }
      }
    }
  }

  private[this] def handleWebSocketFrame(ctx: ChannelHandlerContext, frame: WebSocketFrame): Unit = {
    frame match {
      case close: CloseWebSocketFrame => handleCloseFrame(ctx, close)
      case pong: PongWebSocketFrame => handlePongFrame(ctx, pong)
      case ping: PingWebSocketFrame => handlePingFrame(ctx, ping)
      case text: TextWebSocketFrame => handleTextFrame(ctx, text)
      case continuation: ContinuationWebSocketFrame => handleContinuationFrame(ctx, continuation)
      case _ => {
        throw new UnsupportedOperationException(String.format("%s frame types not supported", frame.getClass()
          .getName()))
      }
    }
  }

  private[this] def handleCloseFrame(ctx: ChannelHandlerContext, frame: CloseWebSocketFrame): Unit = {
    val code = frame.statusCode()
    val reason = frame.reasonText()
    val channelId = ctx.channel().hashCode()

    trace(s"Received close frame [code: $code, reason: '$reason']: $channelId")

    closeFrameReceieve = true
    val closeFrame = frame.retain().asInstanceOf[CloseWebSocketFrame]
    handshaker.get.close(ctx.channel(), closeFrame)
    convergenceSocket.get.handleClosed(closeFrame.statusCode(), closeFrame.reasonText())
  }

  private[this] def handlePingFrame(ctx: ChannelHandlerContext, frame: PingWebSocketFrame): Unit = {
    val channelId = ctx.channel().hashCode()
    trace(s"Received ping, sending pong frame: $channelId")
    ctx.channel().writeAndFlush(new PongWebSocketFrame(frame.content().retain()))
  }

  private[this] def handlePongFrame(ctx: ChannelHandlerContext, frame: PongWebSocketFrame): Unit = {
    val channelId = ctx.channel().hashCode()
    trace(s"Received ping frame: $channelId")
  }

  private[this] def handleTextFrame(ctx: ChannelHandlerContext, frame: TextWebSocketFrame): Unit = {
    if (frame.isFinalFragment()) {
      convergenceSocket.get.onMessageReceived(frame.text())
    } else {
      textFrameBuffer ++= frame.text()
    }
  }

  private[this] def handleContinuationFrame(ctx: ChannelHandlerContext, frame: ContinuationWebSocketFrame): Unit = {
    textFrameBuffer ++= frame.text()
    if (frame.isFinalFragment()) {
      convergenceSocket.get.onMessageReceived(textFrameBuffer.toString())
      textFrameBuffer.clear()
    }
  }

  private[this] def sendHttpResponse(
    ctx: ChannelHandlerContext, req: FullHttpRequest, res: FullHttpResponse): Unit = {
    if (res.getStatus().code() != 200) {
      val buf = Unpooled.copiedBuffer(res.getStatus().toString(), CharsetUtil.UTF_8)
      res.content().writeBytes(buf)
      buf.release()
      HttpHeaders.setContentLength(res, res.content().readableBytes())
    }

    // Send the response and close the connection if necessary.
    val f = ctx.channel().writeAndFlush(res)
    if (!HttpHeaders.isKeepAlive(req) || res.getStatus().code() != 200) {
      f.addListener(ChannelFutureListener.CLOSE)
    }
  }

  private[this] def getWebSocketLocation(req: FullHttpRequest): String = {
    val location = req.headers().get(HttpHeaders.Names.HOST) + WEBSOCKET_PATH
    if (false) {
      "wss://" + location
    } else {
      "ws://" + location
    }
  }

  private[this] def getDomainFqnForRequest(req: FullHttpRequest): Option[DomainFqn] = {
    val uri = URI.create(req.getUri())
    val path = uri.getPath()
    if (path.length <= WEBSOCKET_PATH.length) {
      None
    } else {
      val domainPath = path.substring(WEBSOCKET_PATH.length(), path.length())
      val components = domainPath.split("/")
      if (components.length != 2) {
        None
      } else {
        val namespace = components(0)
        val domainId = components(1)
        Some(DomainFqn(namespace, domainId))
      }
    }
  }
}
