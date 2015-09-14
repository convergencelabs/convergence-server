package com.convergencelabs.server.frontend.realtime.ws

import io.netty.channel.ChannelInitializer
import io.netty.channel.socket.SocketChannel
import io.netty.handler.codec.http.HttpServerCodec
import io.netty.handler.codec.http.HttpObjectAggregator
import io.netty.handler.codec.http.cors.CorsHandler
import io.netty.handler.codec.http.cors.CorsConfig
import com.convergencelabs.server.frontend.realtime.SocketConnectionHandler

private[ws] class WebSocketServerInitializer(
  maxFrameSize: Int,
  connectionHandler: SocketConnectionHandler)
    extends ChannelInitializer[SocketChannel] {

  def initChannel(ch: SocketChannel): Unit = {
    val corsConfig = CorsConfig.withAnyOrigin().build();
    val pipeline = ch.pipeline();
    pipeline.addLast(new HttpServerCodec());
    pipeline.addLast(new HttpObjectAggregator(65536));
    pipeline.addLast(new CorsHandler(corsConfig));
    pipeline.addLast(new WebSocketServerHandler(maxFrameSize, connectionHandler));
  }
}