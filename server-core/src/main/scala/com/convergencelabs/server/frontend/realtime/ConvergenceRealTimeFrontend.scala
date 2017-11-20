package com.convergencelabs.server.frontend.realtime

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

class ConvergenceRealTimeFrontend(
  private[this] val system: ActorSystem,
  private[this] val interface: String,
  private[this] val websocketPort: Int)
    extends Logging {

  private[this] val protoConfig = ProtocolConfigUtil.loadConfig(system.settings.config)

  implicit val dispatcher = system.dispatcher
  implicit val s = system

  var binding: Option[Http.ServerBinding] = None

  def start(): Unit = {
    logger.info(s"Realtime Front End starting up on port $websocketPort.")
    val wsTimeout = system.settings.config.getDuration("akka.http.server.idle-timeout")
    logger.info(s"Web Socket Timeout set to: $wsTimeout.")
    implicit val materializer = ActorMaterializer()

    val service = new WebSocketService(
      protoConfig,
      materializer,
      system)

    Http().bindAndHandle(service.route, interface, websocketPort).onComplete {
      case Success(b) ⇒
        this.binding = Some(b)
        val localAddress = b.localAddress
        logger.info(s"Realtime Front End started up on port $websocketPort.")
      case Failure(e) ⇒
        logger.info(s"Binding failed with ${e.getMessage}")
        system.terminate()
    }
  }

  def stop(): Unit = {
    this.binding match {
      case Some(binding) => binding.unbind()
      case None =>
    }
  }
}
