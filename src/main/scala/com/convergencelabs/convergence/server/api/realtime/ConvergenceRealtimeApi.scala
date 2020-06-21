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

import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.{ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import grizzled.slf4j.Logging

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[ConvergenceRealtimeApi]] is the main entry point that bootstraps
 * the Convergence Server Realtime API. It will start any required actors
 * and create an HTTP Binding to listen for web socket connections.
 *
 * @param system       The Akka ActorSystem .
 * @param interface     The network interface to bind to.
 * @param websocketPort The network port to listen to web socket connections on.
 */
class ConvergenceRealtimeApi(system: ActorSystem[_],
                             clientCreator: ActorRef[ClientActorCreator.CreateClientRequest],
                             interface: String,
                             websocketPort: Int)
  extends Logging {

  private[this] implicit val ec: ExecutionContextExecutor = system.executionContext
  private[this] implicit val classicSystem: ClassicActorSystem = system.toClassic

  private[this] var binding: Option[Http.ServerBinding] = None

  /**
   * Starts the Realtime API, which will listen on the specified
   * interface and port for incoming web socket connections.
   */
  def start(): Unit = {
    val service = new WebSocketService(system, clientCreator)

    Http().bindAndHandle(service.route, interface, websocketPort).onComplete {
      case Success(b) =>
        this.binding = Some(b)
        logger.info(s"Realtime API started at: http://$interface:$websocketPort")
      case Failure(e) =>
        logger.error("Realtime API startup failed", e)
        system.terminate()
    }
  }

  /**
   * Stops the Realtime API, cleaning up any actors created and stopping
   * the HTTP server that listens for incoming web socket connections.
   */
  def stop(): Unit = {
    logger.info("Convergence Realtime API shutting down...")

    this.binding foreach { b =>
      val f = b.unbind()
      Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info("Convergence Realtime API shut down")
    }
  }
}
