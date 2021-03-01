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

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem, Scheduler}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.model.{RemoteAddress, StatusCodes}
import akka.http.scaladsl.server.Directive._
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{CompletionStrategy, Materializer, OverflowStrategy, SystemMaterializer}
import akka.util.{ByteString, ByteStringBuilder, Timeout}
import com.convergencelabs.convergence.server.api.realtime.ClientActorCreator.CreateClientResponse
import com.convergencelabs.convergence.server.api.rest.InfoService.InfoRestResponse
import com.convergencelabs.convergence.server.api.rest.{ErrorResponseEntity, JsonSupport, OkResponse}
import com.convergencelabs.convergence.server.model.DomainId
import grizzled.slf4j.Logging

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[WebSocketService]] class handles incoming web socket connections and
 * creates the server side representation of the client connection. It creates
 * Akka Actors to represent the client, the connection, and the web socket.
 *
 * @param system The actor system this service is running in.
 */
private[realtime] final class WebSocketService(system: ActorSystem[_],
                                               clientCreator: ActorRef[ClientActorCreator.CreateClientRequest])
  extends Directives with Logging with JsonSupport {

  private[this] val config = system.settings.config

  private[this] val maxFrames = config.getInt("convergence.realtime.websocket.max-frames")

  private[this] val maxStreamDuration = Duration.fromNanos(
    config.getDuration("convergence.realtime.websocket.max-stream-duration").toNanos)

  private[this] implicit val clientCreationTimeout: Timeout = Timeout(Duration.fromNanos(
    config.getDuration("convergence.realtime.client.client-creation-timeout").toNanos))

  private[this] implicit val ec: ExecutionContextExecutor = system.executionContext
  private[this] implicit val scheduler: Scheduler = system.scheduler
  private[this] implicit val materializer: Materializer = SystemMaterializer.get(system).materializer

  val route: Route = {
    path("") {
      complete(Future.successful(InfoRestResponse))
    } ~ path("health") {
      complete(Future.successful(OkResponse))
    } ~
      path(Segment / Segment) { (namespace, domain) =>
        extractClientIP { remoteAddress =>
          optionalHeaderValueByName("User-Agent") { ua =>
            handleWebSocketConnection(namespace, domain, remoteAddress, ua)
          }
        }
      }
  }


  /**
   * Provides a route that will handle incoming web socket connections.
   *
   * @param namespace     The namespace of the domain the connection is for.
   * @param domain        The domain id of the domain the connection is for.
   * @param remoteAddress The address of the remote host that is connecting
   * @param ua            The user agent of the connecting client.
   * @return A route which will handle the incoming web socket connection.
   */
  private[this] def handleWebSocketConnection(namespace: String,
                                              domain: String,
                                              remoteAddress: RemoteAddress,
                                              ua: Option[String]): Route = {
    extractWebSocketUpgrade { upgrade =>
      logger.debug(s"A web socket connection request received for domain: $namespace/$domain")
      onComplete(createClientActor(namespace, domain, remoteAddress, ua)) {
        case Failure(cause) =>
          logger.error("Could not create client actor for incoming web socket connection.", cause)
          complete((StatusCodes.InternalServerError, ErrorResponseEntity("internal_server_error")))
        case Success(client) =>
          complete(upgrade.handleMessages(createBinaryMessageFlow(client), None))
      }
    }
  }

  /**
   * A helper method that will request a client actor be crated for this
   * incoming connection.
   *
   * @param namespace     The namespace of the domain the connection is for.
   * @param domain        The domain id of the domain the connection is for.
   * @param remoteAddress The address of the remote host that is connecting
   * @param ua            The user agent of the connecting client.
   * @return An ActorRef that the web socket can use to send incoming messages
   *         to.
   */
  private[this] def createClientActor(namespace: String,
                                      domain: String,
                                      remoteAddress: RemoteAddress,
                                      ua: Option[String]): Future[ActorRef[ClientActor.WebSocketMessage]] = {
    val domainId = DomainId(namespace, domain)
    val userAgent = ua.getOrElse("")
    clientCreator
      .ask[CreateClientResponse](ClientActorCreator.CreateClientRequest(domainId, remoteAddress, userAgent, _))
      .map(_.client)
  }

  /**
   * Creates a flow to handle binary web socket connections.
   *
   * @param clientActor The client actor that messages should be sent to.
   * @return A flow to handle incoming and outgoing message flow.
   */
  private[this] def createBinaryMessageFlow(clientActor: ActorRef[ClientActor.WebSocketMessage]): Flow[Message, Message, Any] = {
    Flow[Message]
      .collect {
        case BinaryMessage.Strict(msg) =>
          Future.successful(ClientActor.IncomingBinaryMessage(msg.toArray))
        case BinaryMessage.Streamed(stream) =>
          stream
            .limit(maxFrames)
            .completionTimeout(maxStreamDuration)
            .runFold(new ByteStringBuilder())((b, e) => b.append(e))
            .map(b => b.result())
            .flatMap(msg => Future.successful(ClientActor.IncomingBinaryMessage(msg.toArray)))
      }
      .mapAsync(parallelism = 3)(identity)
      .via(createClientActorFlow(clientActor))
      .map {
        case WebSocketService.OutgoingBinaryMessage(msg) => BinaryMessage.Strict(ByteString.fromArray(msg))
      }
  }

  /**
   * A helper method to create the actor based message exchange for the
   * we socket messages. This method will establish bi-directional wiring
   * for the sink and source of the flow using the client actor.
   *
   * @param clientActor The actor to send / recieve messages from.
   * @return The portion of the flow that wires up the client actor.
   */
  private[this] def createClientActorFlow(clientActor: ActorRef[ClientActor.WebSocketMessage]):
  Flow[ClientActor.IncomingBinaryMessage, WebSocketService.OutgoingBinaryMessage, Any] = {
    // This is how we route messages that are coming in.  Basically we route them
    // to the ClientActor  and, when the flow is completed (e.g. the web socket is
    // closed) we send a WebSocketClosed message so the client knows the socket
    // was closed. Similarly, we send a WebSocketError when there is an error.
    val in = Flow[ClientActor.IncomingBinaryMessage].to(Sink.actorRef[ClientActor.IncomingBinaryMessage](
      clientActor.toClassic, // note Akka HTTP is still using the classic actors
      ClientActor.WebSocketClosed, // The message sent when the stream closes nicely
      t => ClientActor.WebSocketError(t))) // The message sent on an error

    // This is where outgoing messages will go.  We create an actor-based source
    // for messages.  This creates an ActorRef that you can send messages to get
    // access to source as an ActorRef you must materialize the source. Once we
    // get this reference we can send it to the ClientActor to give it the
    // ActorRef to send outgoing messages to.
    val out = Source.actorRef[WebSocketService.OutgoingBinaryMessage](
      {
        case WebSocketService.CloseSocket => CompletionStrategy.draining
      }: PartialFunction[Any, CompletionStrategy], {
        case akka.actor.Status.Failure(cause) => cause
      }: PartialFunction[Any, Throwable],
      500,
      OverflowStrategy.fail)
      .mapMaterializedValue(ref => clientActor ! ClientActor.WebSocketOpened(ref))

    Flow.fromSinkAndSource(in, out)
  }
}

private[realtime] object WebSocketService {

  /**
   * The messages that can be sent to the Source actor ref that is materialized
   * and sent to the ClientActor.
   */
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
