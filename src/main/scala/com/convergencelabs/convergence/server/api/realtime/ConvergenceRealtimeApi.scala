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

import akka.actor.{ActorRef, ActorSystem, PoisonPill}
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import com.convergencelabs.convergence.server.ProtocolConfigUtil
import com.convergencelabs.convergence.server.datastore.convergence.ConfigStoreActor
import com.convergencelabs.convergence.server.util.AkkaRouterUtils.createBackendRouter
import grizzled.slf4j.Logging

import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContextExecutor}
import scala.language.postfixOps
import scala.util.{Failure, Success}

/**
 * The [[ConvergenceRealtimeApi]] is the main entry point that bootstraps
 * the Convergence Server Realtime API. It will start any required actors
 * and create an HTTP Binding to listen for web socket connections.
 *
 * @param system        The Akka [[ActorSystem]] to deploy Actors into.
 * @param interface     The network interface to bind to.
 * @param websocketPort The network port to listen to web socket connections on.
 */
class ConvergenceRealtimeApi(private[this] val system: ActorSystem,
                             private[this] val interface: String,
                             private[this] val websocketPort: Int)
  extends Logging {

  private[this] val protoConfig = ProtocolConfigUtil.loadConfig(system.settings.config)
  private[this] implicit val dispatcher: ExecutionContextExecutor = system.dispatcher
  private[this] implicit val s: ActorSystem = system

  private[this] val routers = ListBuffer[ActorRef]()
  private[this] var binding: Option[Http.ServerBinding] = None

  /**
   * Starts the Realtime API, which will listen on the specified
   * interface and port for incoming web socket connections.
   */
  def start(): Unit = {
    implicit val materializer: ActorMaterializer = ActorMaterializer()

    val configActor = createBackendRouter(system, ConfigStoreActor.RelativePath, "realtimeConfigActor")
    routers += configActor

    val service = new WebSocketService(protoConfig, materializer, system)

    Http().bindAndHandle(service.route, interface, websocketPort).onComplete {
      case Success(b) ⇒
        this.binding = Some(b)
        logger.info(s"Realtime API started at: http://$interface:$websocketPort")
      case Failure(e) ⇒
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
    routers.foreach(router => router ! PoisonPill)

    this.binding foreach { b =>
      val f = b.unbind()
      Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
      logger.info("Convergence Realtime API shut down")
    }
  }
}
