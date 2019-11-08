package com.convergencelabs.server.api.realtime

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.{Materializer, OverflowStrategy}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.util.{ByteString, ByteStringBuilder}
import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainId
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.concurrent.duration.Duration
import scala.language.postfixOps

/**
 * Represents an incoming binary message from the client.
 *
 * @param message The incoming binary web socket message data.
 */
private[realtime] case class IncomingBinaryMessage(message: Array[Byte])

/**
 * Represents an outgoing binary message from the client.
 *
 * @param message The outgoing binary web socket message data.
 */
private[realtime] case class OutgoingBinaryMessage(message: Array[Byte])

/**
 * The [[WebSocketService]] class handles incoming web socket connections and
 * creates the server side representation of the client connection. It creates
 * Akka Actors to represent the client, the connection, and the web socket.
 *
 * @param protocolConfig The configuration options for the Convergence Web
 *                       Socket protocol.
 * @param materializer   The materializer to use to materialize the web socket
 *                       flow.
 * @param system         The actor system in which to create the acttors.
 */
private[realtime] class WebSocketService(private[this] val protocolConfig: ProtocolConfiguration,
                                         private[this] implicit val materializer: Materializer,
                                         private[this] implicit val system: ActorSystem)
  extends Directives
    with Logging {

  private[this] val config = system.settings.config
  private[this] val maxFrames = config.getInt("convergence.realtime.websocket.max-frames")
  private[this] val maxStreamDuration = Duration.fromNanos(
    config.getDuration("convergence.realtime.websocket.max-stream-duration").toNanos)

  private[this] implicit val ec: ExecutionContextExecutor = system.dispatcher

  val route: Route = {
    path(Segment / Segment) { (namespace, domain) =>
      extractClientIP { remoteAddress =>
        optionalHeaderValueByName("User-Agent") { ua =>
          handleWebSocketMessages(realTimeDomainFlow(namespace, domain, remoteAddress, ua.getOrElse("")))
        }
      }
    }
  }

  private[this] def realTimeDomainFlow(namespace: String, domain: String, remoteAddress: RemoteAddress, ua: String): Flow[Message, Message, Any] = {
    logger.debug(s"New web socket connection for $namespace/$domain")
    Flow[Message]
      .collect {
        case BinaryMessage.Strict(msg) =>
          Future.successful(IncomingBinaryMessage(msg.toArray))
        case BinaryMessage.Streamed(stream) ⇒
          stream
            .limit(maxFrames)
            .completionTimeout(maxStreamDuration)
            .runFold(new ByteStringBuilder())((b, e) => b.append(e))
            .map(b ⇒ b.result)
            .flatMap(msg => Future.successful(IncomingBinaryMessage(msg.toArray)))
      }
      .mapAsync(parallelism = 3)(identity)
      .via(createFlowForConnection(namespace, domain, remoteAddress, ua))
      .map {
        case OutgoingBinaryMessage(msg) ⇒ BinaryMessage.Strict(ByteString.fromArray(msg))
      }
  }

  private[this] def createFlowForConnection(namespace: String, domain: String, remoteAddress: RemoteAddress, ua: String): Flow[IncomingBinaryMessage, OutgoingBinaryMessage, Any] = {
    val clientActor = system.actorOf(ClientActor.props(
      DomainId(namespace, domain),
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
