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

import akka.actor
import akka.actor.typed.ActorRef
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model.RemoteAddress
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, OverflowStrategy}
import akka.util.{ByteString, ByteStringBuilder}
import com.convergencelabs.convergence.server.ProtocolConfiguration
import com.convergencelabs.convergence.server.api.rest.InfoService.InfoRestResponse
import com.convergencelabs.convergence.server.api.rest.{JsonSupport, OkResponse}
import com.convergencelabs.convergence.server.db.provision.DomainLifecycleTopic
import com.convergencelabs.convergence.server.domain.activity.ActivityActor
import com.convergencelabs.convergence.server.domain.chat.{ChatActor, ChatDeliveryActor}
import com.convergencelabs.convergence.server.domain.model.RealtimeModelActor
import com.convergencelabs.convergence.server.domain.{DomainActor, DomainId}
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
 * @param context        The actor context in which to create the actors.
 */
private[realtime] class WebSocketService(protocolConfig: ProtocolConfiguration,
                                         context: ActorContext[_],
                                         domainRegion: ActorRef[DomainActor.Message],
                                         activityShardRegion: ActorRef[ActivityActor.Message],
                                         modelShardRegion: ActorRef[RealtimeModelActor.Message],
                                         chatShardRegion: ActorRef[ChatActor.Message],
                                         chatDeliveryShardRegion: ActorRef[ChatDeliveryActor.Message],
                                         domainLifecycleTopic: ActorRef[DomainLifecycleTopic.TopicMessage])
  extends Directives with Logging with JsonSupport {

  private[this] val config = context.system.settings.config
  private[this] val maxFrames = config.getInt("convergence.realtime.websocket.max-frames")
  private[this] val maxStreamDuration = Duration.fromNanos(
    config.getDuration("convergence.realtime.websocket.max-stream-duration").toNanos)

  private[this] val modelSyncInterval = Duration.fromNanos(
    config.getDuration("convergence.offline.model-sync-interval").toNanos)

  private[this] implicit val ec: ExecutionContextExecutor = context.system.executionContext

  // Needed by akka http that still uses Classic actors
  private[this] implicit val system: actor.ActorSystem = context.system.toClassic

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
          Future.successful(ConnectionActor.IncomingBinaryMessage(msg.toArray))
        case BinaryMessage.Streamed(stream) =>
          stream
            .limit(maxFrames)
            .completionTimeout(maxStreamDuration)
            .runFold(new ByteStringBuilder())((b, e) => b.append(e))
            .map(b => b.result)
            .flatMap(msg => Future.successful(ConnectionActor.IncomingBinaryMessage(msg.toArray)))
      }
      .mapAsync(parallelism = 3)(identity)
      .via(createFlowForConnection(namespace, domain, remoteAddress, ua))
      .map {
        case WebSocketService.OutgoingBinaryMessage(msg) => BinaryMessage.Strict(ByteString.fromArray(msg))
      }
  }

  private[this] def createFlowForConnection(namespace: String,
                                            domain: String,
                                            remoteAddress: RemoteAddress,
                                            ua: String): Flow[ConnectionActor.IncomingBinaryMessage, WebSocketService.OutgoingBinaryMessage, Any] = {
    val clientActor = context.spawnAnonymous(ClientActor(
      DomainId(namespace, domain),
      protocolConfig,
      remoteAddress,
      ua,
      domainRegion,
      activityShardRegion,
      modelShardRegion,
      chatShardRegion,
      chatDeliveryShardRegion,
      domainLifecycleTopic,
      modelSyncInterval)
    )

    val connection = context.spawnAnonymous(ConnectionActor(clientActor, None))


    // This is how we route messages that are coming in.  Basically we route them
    // to the connection actor and, when the flow is completed (e.g. the web socket is
    // closed) we send a WebSocketClosed case object, which the connection can listen for.
    val in = Flow[ConnectionActor.IncomingBinaryMessage].to(Sink.actorRef[ConnectionActor.IncomingBinaryMessage](
      connection.toClassic, // note Akka HTTP is still using the cla
      ConnectionActor.WebSocketClosed, // The message sent when the stream closes nicely
      t => ConnectionActor.WebSocketError(t))) // The message sent on an error

    // This is where outgoing messages will go.  Basically we create an actor based
    // source for messages.  This creates an ActorRef that you can send messages to
    // and then will be spit out the flow.  However to get access to this you must
    // materialize the source.  By materializing it we get a reference to the underlying
    // actor.  We can send an actor ref (in a message) to the connection actor.  This is
    // how the connection actor will get a reference to the actor that it needs to sent
    // messages to.
    val out = Source.actorRef[WebSocketService.OutgoingBinaryMessage](
      {
        case WebSocketService.CloseSocket =>
          CompletionStrategy.draining
        //        case akka.actor.Status.Success(s: CompletionStrategy) => s
        //        case akka.actor.Status.Success(_) => CompletionStrategy.draining
        //        case akka.actor.Status.Success => CompletionStrategy.draining
      }: PartialFunction[Any, CompletionStrategy], {
        case akka.actor.Status.Failure(cause) => cause
      }: PartialFunction[Any, Throwable],
      500,
      OverflowStrategy.fail)
      .mapMaterializedValue(ref => connection ! ConnectionActor.WebSocketOpened(ref))

    Flow.fromSinkAndSource(in, out)
  }
}

private[realtime] object WebSocketService {

  sealed trait WebSocketMessage

  /**
   * Indicates that this connection should be closed. This message does not
   * come from the web socket, but rather from within Convergence.
   */
  private[realtime] case object CloseSocket extends WebSocketMessage

  /**
   * Represents an outgoing binary message from the client.
   *
   * @param data The outgoing binary web socket message data.
   */
  private[realtime] case class OutgoingBinaryMessage(data: Array[Byte]) extends WebSocketMessage

}