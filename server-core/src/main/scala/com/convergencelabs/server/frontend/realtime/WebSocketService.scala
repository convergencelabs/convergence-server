package com.convergencelabs.server.frontend.realtime

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps

import com.convergencelabs.server.ProtocolConfiguration
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.Message
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.server.Directive.addByNameNullaryApply
import akka.http.scaladsl.server.Directive.addDirectiveApply
import akka.http.scaladsl.server.Directives
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.http.scaladsl.server.Route
import scala.concurrent.Future
import scala.concurrent.duration.Duration

case class IncomingTextMessage(message: String)
case class OutgoingTextMessage(message: String)

class WebSocketService(
  private[this] val domainManager: ActorRef,
  private[this] val protocolConfig: ProtocolConfiguration,
  private[this] implicit val fm: Materializer,
  private[this] implicit val system: ActorSystem)
    extends Directives {

  private[this] val config = system.settings.config
  private[this] val maxFrames = config.getInt("convergence.websocket.max-frames")
  private[this] val maxStreamDuration = Duration.fromNanos(
      config.getDuration("convergence.websocket.max-stream-duration").toNanos)

  private[this] implicit val ec = system.dispatcher

  val route: Route =
    get {
      path("domain" / Segment / Segment) { (namespace, domain) =>
        handleWebSocketMessages(realTimeDomainFlow(namespace, domain))
      }
    }

  private[this] def realTimeDomainFlow(namespace: String, domain: String): Flow[Message, Message, Any] =
    Flow[Message]
      .collect {
        case TextMessage.Strict(msg) ⇒ Future.successful(IncomingTextMessage(msg))
        case TextMessage.Streamed(stream) ⇒ stream
          .limit(maxFrames)
          .completionTimeout(maxStreamDuration)
          .runFold("")(_ + _)
          .flatMap(msg => Future.successful(IncomingTextMessage(msg)))
      }
      .mapAsync(parallelism = 3)(identity)
      .via(createFlowForConnection(namespace, domain))
      .map {
        case OutgoingTextMessage(msg) ⇒ TextMessage.Strict(msg)
      }

  private[this] def createFlowForConnection(namespace: String, domain: String): Flow[IncomingTextMessage, OutgoingTextMessage, Any] = {
    val clientActor = system.actorOf(ClientActor.props(
      domainManager,
      DomainFqn(namespace, domain),
      protocolConfig))

    val connection = system.actorOf(ConnectionActor.props(clientActor))

    // This is how we route messages that are coming in.  Basically we route them
    // to the echo actor and, when the flow is completed (e.g. the web socket is
    // disconnected) we send a Disconnected case class.
    val in = Flow[IncomingTextMessage].to(Sink.actorRef[IncomingTextMessage](connection, WebSocketClosed))

    // This is where outgoing messages will go.  Basically we create an actor based
    // source for messages.  This creates an ActorRef that you can send messages to
    // and then will be spit out the flow.  However to get access to this you must
    // materialize the source.  By materializing it we get a reference to the underlying
    // actor and we can send another actor this reference, or use the reference however
    // we want (e.g. create a connection)
    val out = Source.actorRef[OutgoingTextMessage](1, OverflowStrategy.fail).mapMaterializedValue({ ref =>
      connection ! WebSocketOpened(ref)
    })

    Flow.fromSinkAndSource(in, out)
  }
}
