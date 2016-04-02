package com.convergencelabs.server.frontend.realtime.http

import java.util.Date

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.{ Message, TextMessage }
import akka.stream.stage._

import scala.concurrent.duration._

import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.scaladsl.Flow

class Service(implicit fm: Materializer, system: ActorSystem) extends Directives {
  val echoFlow = Echo.create(system)

  def route =
    get {
      path("echo") {
        handleWebSocketMessages(websocketEchoFlow())
      }
    }

  def websocketEchoFlow(): Flow[Message, Message, Any] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ msg
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(echoFlow.echoFlow()) // Route to our echo flow
      .map {
        case msg: String ⇒
          // We get back strings from our echo flow.  Wrap them in a web socket
          // text message.  This will be the output of the flow.
          TextMessage.Strict(msg)
      }
}