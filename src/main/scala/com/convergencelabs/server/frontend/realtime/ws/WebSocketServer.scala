package com.convergencelabs.server.frontend.realtime.ws

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.handler.logging.LogLevel
import io.netty.handler.logging.LoggingHandler
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import grizzled.slf4j.Logging
import io.netty.channel.socket.nio.NioServerSocketChannel
import com.convergencelabs.server.util.NamedThreadFactory
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.frontend.realtime.ConvergenceServerSocket
import com.convergencelabs.server.frontend.realtime.SocketClosed
import com.convergencelabs.server.frontend.realtime.SocketConnectionHandler
import com.convergencelabs.server.frontend.realtime.SocketDropped
import com.convergencelabs.server.frontend.realtime.SocketError
import com.convergencelabs.server.frontend.realtime.SocketMessage
import io.netty.channel.socket.nio.NioServerSocketChannel

/**
 * Implements a web socket server for the convergence realtime API.
 *
 * @param port The TCP Port the server should bind to.
 * @param maxFrameSize The maximum size, in bytes, that a text message should
 *                     be in order to send it as a single frame.  Messages
 *                     larger than this size will be fragmented.
 * @param connectionHandler The object that will respond to new socket 
 *                          connections.                
 */
private[realtime] class WebSocketServer(
  port: Int,
  maxFrameSize: Int,
  private[this] val connectionHandler: SocketConnectionHandler)
    extends Logging {

  private[this] val bossGroup = new NioEventLoopGroup(1);
  private[this] val workerGroup = new NioEventLoopGroup(0, new NamedThreadFactory("WebSocket"));

  def start() {
    info(s"Web Socket server starting up on port: $port")

    val b = new ServerBootstrap();
    b.group(bossGroup, workerGroup)
      .channel(classOf[NioServerSocketChannel])
      .handler(new LoggingHandler(LogLevel.INFO))
      .childHandler(new WebSocketServerInitializer(maxFrameSize, connectionHandler))

    val ch = b.bind(port).sync().channel()

    info("Web Socket server started")
  }

  def stop() {
    info("Web Socket server stopping")
    bossGroup.shutdownGracefully()
    workerGroup.shutdownGracefully()
    info("Web Socket server stopped")
  }
}