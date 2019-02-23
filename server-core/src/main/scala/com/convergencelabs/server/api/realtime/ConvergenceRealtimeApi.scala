package com.convergencelabs.server.api.realtime

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.ProtocolConfigUtil

import akka.actor.ActorSystem
import akka.actor.Inbox
import akka.http.scaladsl.Http
import akka.http.scaladsl.server.RouteResult.route2HandlerFlow
import akka.stream.ActorMaterializer
import grizzled.slf4j.Logging
import scala.concurrent.Await

class ConvergenceRealtimeApi(
  private[this] val system: ActorSystem,
  private[this] val interface: String,
  private[this] val websocketPort: Int)
    extends Logging {

  private[this] val protoConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

  implicit val dispatcher = system.dispatcher
  implicit val s = system

  var binding: Option[Http.ServerBinding] = None

  def start(): Unit = {
    val wsTimeout = system.settings.config.getDuration("akka.http.server.idle-timeout")
    implicit val materializer = ActorMaterializer()

    val service = new WebSocketService(
      protoConfig,
      materializer,
      system)

    Http().bindAndHandle(service.route, interface, websocketPort).onComplete {
      case Success(b) ⇒
        this.binding = Some(b)
        val localAddress = b.localAddress
        logger.info(s"Realtime API started at: http://${interface}:${websocketPort}")
      case Failure(e) ⇒
        logger.error("Realtime API startup failed", e)
        system.terminate()
    }
  }

  def stop(): Unit = {
    logger.info("Convergence Realtime API shutting down.")
    this.binding foreach { b =>
       val f = b.unbind()
       Await.result(f, FiniteDuration(10, TimeUnit.SECONDS))
       logger.info("Convergence Realtime API shut down.")
    }
  }
}
