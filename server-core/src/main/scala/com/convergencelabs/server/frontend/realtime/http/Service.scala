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
  val domainFlowFactory = DomainFlowFactory(system)

  def route =
    get {
      path("domain" / Segment / Segment) { (namespace, domain) =>
        handleWebSocketMessages(realTimeDomainFlow(namespace, domain))
      }
    }

  def realTimeDomainFlow(namespace: String, domain: String): Flow[Message, Message, Any] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ IncomingMessage(msg)
        // This will lose (ignore) messages not received in one chunk (which is
        // unlikely because chat messages are small) but absolutely possible
        // FIXME: We need to handle TextMessage.Streamed as well.
      }
      .via(domainFlowFactory.createFlowForConnection(namespace, domain)) // Route to our echo flow
      .map {
        case OutgoingMessage(msg) ⇒
          // We get back strings from our echo flow.  Wrap them in a web socket
          // text message.  This will be the output of the flow.
          TextMessage.Strict(msg)
      }
}