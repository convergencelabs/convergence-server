package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives
import akka.http.scaladsl.server.Directives._
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.Route
import scala.concurrent.Future
import scala.concurrent.duration.Duration
import grizzled.slf4j.Logging
import akka.http.scaladsl.model.RemoteAddress
import akka.util.ByteString

case class IncomingBinaryMessage(message: Array[Byte])
case class OutgoingBinaryMessage(message: Array[Byte])

class WebSocketService(
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] implicit val fm: Materializer,
  private[this] implicit val system: ActorSystem)
    extends Directives
    with Logging {

  private[this] val config = system.settings.config
  private[this] val maxFrames = config.getInt("convergence.realtime.websocket.max-frames")
  private[this] val maxStreamDuration = Duration.fromNanos(
    config.getDuration("convergence.realtime.websocket.max-stream-duration").toNanos)

  private[this] implicit val ec = system.dispatcher

  val route: Route =
    get {
      path("domain" / Segment / Segment) { (namespace, domain) =>
        extractClientIP { ip =>
          headerValueByName("User-Agent") { ua =>
            handleWebSocketMessages(realTimeDomainFlow(namespace, domain, ip, ua))
          }
        }
      }
    }

  private[this] def realTimeDomainFlow(namespace: String, domain: String, remoteAddress: RemoteAddress, ua: String): Flow[Message, Message, Any] = {
    logger.info(s"New web socket connection for $namespace/$domain")
    Flow[Message]
      .collect {
        case BinaryMessage.Strict(msg) ⇒ Future.successful(IncomingBinaryMessage(msg.toArray))
        case BinaryMessage.Streamed(stream) ⇒ stream
          .limit(maxFrames)
          .completionTimeout(maxStreamDuration)
          .runFold("")(_ + _)
          .flatMap(msg => Future.successful(IncomingBinaryMessage(ByteString(msg).toArray)))
      }
      .mapAsync(parallelism = 3)(identity)
      .via(createFlowForConnection(namespace, domain, remoteAddress, ua))
      .map {
        case OutgoingBinaryMessage(msg) ⇒ BinaryMessage.Strict(ByteString.fromArray(msg))
      }
  }

  private[this] def createFlowForConnection(namespace: String, domain: String, remoteAddress: RemoteAddress, ua: String): Flow[IncomingBinaryMessage, OutgoingBinaryMessage, Any] = {
    val clientActor = system.actorOf(ClientActor.props(
      DomainFqn(namespace, domain),
      protocolConfig,
      remoteAddress,
      ua))

    val connection = system.actorOf(ConnectionActor.props(clientActor))

    // This is how we route messages that are coming in.  Basically we route them
    // to the connection actor and, when the flow is completed (e.g. the web socket is
    // closed) we send a WebSocketClosed case object, which the connection can listen for.
    val in = Flow[IncomingBinaryMessage].to(Sink.actorRef[IncomingBinaryMessage](connection, WebSocketClosed))

    // This is where outgoing messages will go.  Basically we create an actor based
    // source for messages.  This creates an ActorRef that you can send messages to
    // and then will be spit out the flow.  However to get access to this you must
    // materialize the source.  By materializing it we get a reference to the underlying
    // actor.  We can send an actor ref (in a message) to the connection actor.  This is
    // how the connection actor will get a reference to the actor that it needs to sent 
    // messages to.
    val out = Source.actorRef[OutgoingBinaryMessage](500, OverflowStrategy.fail).mapMaterializedValue({ ref =>
      connection ! WebSocketOpened(ref)
    })

    Flow.fromSinkAndSource(in, out)
  }
}
