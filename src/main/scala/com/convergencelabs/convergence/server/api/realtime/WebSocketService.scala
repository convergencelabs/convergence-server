/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.api.realtime

import akka.actor.{ActorSystem, Status}
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.{ByteString, ByteStringBuilder}
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.api.realtime.ConnectionActor.{IncomingBinaryMessage, OutgoingBinaryMessage, WebSocketClosed, WebSocketOpened}
import com.convergencelabs.convergence.server.api.rest.InfoService.InfoRestResponse
import com.convergencelabs.convergence.server.api.rest.{JsonSupport, OkResponse}
import com.convergencelabs.convergence.server.domain.DomainId
import grizzled.slf4j.Logging

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

/**
 * The [[WebSocketService]] class handles incoming web socket connections and
 * creates the server side representation of the client connection. It creates
 * Akka Actors to represent the client, the connection, and the web socket.
 *
 * @param protocolConfig The configuration options for the Convergence Web
 *                       Socket protocol.
 * @param system         The actor system in which to create the actors.
 */
private[realtime] class WebSocketService(private[this] val protocolConfig: ProtocolConfiguration,
                                         private[this] implicit val system: ActorSystem)
  extends Directives
    with Logging
    with JsonSupport {

  private[this] val config = system.settings.config
  private[this] val maxFrames = config.getInt("convergence.realtime.websocket.max-frames")
  private[this] val maxStreamDuration = Duration.fromNanos(
    config.getDuration("convergence.realtime.websocket.max-stream-duration").toNanos)

  private[this] val modelSyncInterval = Duration.fromNanos(
    config.getDuration("convergence.offline.model-sync-interval").toNanos)

  private[this] implicit val ec: ExecutionContextExecutor = system.dispatcher

  val route: Route = {
    path("") {
      complete(Future.successful(InfoRestResponse))
    } ~ path("health") {
      complete(Future.successful(OkResponse))
    } ~
      path(Segment / Segment) { (namespace, domain) =>
        extractClientIP { remoteAddress =>
          optionalHeaderValueByName("User-Agent") { ua =>
            handleWebSocketMessages(realTimeDomainFlow(namespace, domain, remoteAddress, ua.getOrElse("")))
          }
        }
      }
  }

  private[this] def realTimeDomainFlow(namespace: String,
                                       domain: String,
                                       remoteAddress:
                                       RemoteAddress,
                                       ua: String): Flow[Message, Message, Any] = {
    logger.debug(s"New web socket connection for $namespace/$domain")
    Flow[Message]
      .collect {
        case BinaryMessage.Strict(msg) =>
          Future.successful(IncomingBinaryMessage(msg.toArray))
        case BinaryMessage.Streamed(stream) =>
          stream
            .limit(maxFrames)
            .completionTimeout(maxStreamDuration)
            .runFold(new ByteStringBuilder())((b, e) => b.append(e))
            .map(b => b.result)
            .flatMap(msg => Future.successful(IncomingBinaryMessage(msg.toArray)))
      }
      .mapAsync(parallelism = 3)(identity)
      .via(createFlowForConnection(namespace, domain, remoteAddress, ua))
      .map {
        case OutgoingBinaryMessage(msg) => BinaryMessage.Strict(ByteString.fromArray(msg))
      }
  }

  private[this] def createFlowForConnection(namespace: String, domain: String, remoteAddress: RemoteAddress, ua: String): Flow[IncomingBinaryMessage, OutgoingBinaryMessage, Any] = {
    val clientActor = system.actorOf(ClientActor.props(
      DomainId(namespace, domain),
      protocolConfig,
      remoteAddress,
      ua,
      modelSyncInterval))

    val connection = system.actorOf(ConnectionActor.props(clientActor))

    // This is how we route messages that are coming in.  Basically we route them
    // to the connection actor and, when the flow is completed (e.g. the web socket is
    // closed) we send a WebSocketClosed case object, which the connection can listen for.
    val in = Flow[IncomingBinaryMessage].to(Sink.actorRef[IncomingBinaryMessage](connection, WebSocketClosed, t => Status.Failure(t)))

    // This is where outgoing messages will go.  Basically we create an actor based
    // source for messages.  This creates an ActorRef that you can send messages to
    // and then will be spit out the flow.  However to get access to this you must
    // materialize the source.  By materializing it we get a reference to the underlying
    // actor.  We can send an actor ref (in a message) to the connection actor.  This is
    // how the connection actor will get a reference to the actor that it needs to sent
    // messages to.
    val out = Source.actorRef[OutgoingBinaryMessage](
      {
        case akka.actor.Status.Success(s: CompletionStrategy) => s
        case akka.actor.Status.Success(_) => CompletionStrategy.draining
        case akka.actor.Status.Success => CompletionStrategy.draining
      }: PartialFunction[Any, CompletionStrategy], {
        case akka.actor.Status.Failure(cause) => cause
      }: PartialFunction[Any, Throwable],
      500,
      OverflowStrategy.fail)
      .mapMaterializedValue(ref => connection ! WebSocketOpened(ref))

    Flow.fromSinkAndSource(in, out)
  }
}
