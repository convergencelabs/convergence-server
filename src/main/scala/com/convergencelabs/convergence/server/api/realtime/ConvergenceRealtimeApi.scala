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

import akka.Done
import akka.actor.typed.scaladsl.adapter._
import akka.actor.typed.{ActorRef, ActorSystem}
import akka.actor.{CoordinatedShutdown, ActorSystem => ClassicActorSystem}
import akka.http.scaladsl.Http
import grizzled.slf4j.Logging

import scala.concurrent.{ExecutionContextExecutor, Future}
import scala.language.postfixOps

/**
 * The [[ConvergenceRealtimeApi]] is the main entry point that bootstraps
 * the Convergence Server Realtime API. It will start any required actors
 * and create an HTTP Binding to listen for web socket connections.
 *
 * @param system        The Akka ActorSystem .
 * @param interface     The network interface to bind to.
 * @param websocketPort The network port to listen to web socket connections on.
 */
private[server] final class ConvergenceRealtimeApi(system: ActorSystem[_],
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
   *
   * @return A future that will be resolved when the Realtime API startup
   *         is complete.
   */
  def start(): Future[Unit] = {
    CoordinatedShutdown(system).addTask(CoordinatedShutdown.PhaseServiceUnbind, "Unbind Realtime API") { () =>
      this.stop().map(_ => Done)
    }

    val service = new WebSocketService(system, clientCreator)
    Http()
      .newServerAt(interface, websocketPort)
      .bind(service.route)
      .map { binding =>
        this.binding = Some(binding)
        logger.info(s"Realtime API started at: http://$interface:$websocketPort")
        ()
      }
      .recoverWith { cause =>
        logger.error("Realtime API startup failed", cause)
        Future.failed(cause)
      }


  }

  /**
   * Stops the Realtime API, cleaning up any actors created and stopping
   * the HTTP server that listens for incoming web socket connections.
   */
  def stop(): Future[Unit] = {
    logger.info("Convergence Realtime API shutting down...")

    this.binding
      .map (b =>b.unbind())
      .getOrElse(Future.successful(()))
      .map(_ => logger.info("Convergence Realtime API shut down"))
  }
}
