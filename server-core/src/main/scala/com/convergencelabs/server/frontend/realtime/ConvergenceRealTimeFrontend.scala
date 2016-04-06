package com.convergencelabs.server.frontend.realtime

import java.util.concurrent.TimeUnit

import scala.concurrent.duration.DurationInt
import scala.concurrent.duration.FiniteDuration
import scala.language.postfixOps
import scala.util.Failure
import scala.util.Success

import com.convergencelabs.server.HeartbeatConfiguration
import com.convergencelabs.server.ProtocolConfiguration

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

  // FIXME this object is nonsensical.  It's all over the place.  I don't know
  // if this is the right place for this.
  private val protoConfig = ProtocolConfiguration(
    5 seconds,
    HeartbeatConfiguration(
      true,
      5 seconds,
      10 seconds))

  private[this] val inbox = Inbox.create(system)
  private[this] val connectionManager = system.actorOf(RealTimeFrontEndActor.props(inbox.getRef(), protoConfig), "connectionManager")

  import system.dispatcher
  implicit val s = system

  def start(): Unit = {
    logger.info(s"Realtime Front End starting up on port $websocketPort.")
    val timeout = FiniteDuration(5, TimeUnit.SECONDS)
    inbox.receive(timeout) match {
      case StartUpComplete(domainManager) => {
        implicit val materializer = ActorMaterializer()

        val service = new WebSocketService(
          domainManager,
          protoConfig,
          FiniteDuration(5, TimeUnit.SECONDS),
          materializer,
          system)

        val binding = Http().bindAndHandle(service.route, interface, websocketPort)
        binding.onComplete {
          case Success(binding) ⇒
            val localAddress = binding.localAddress
            logger.info(s"Realtime Front End started up on port $websocketPort.")
          case Failure(e) ⇒
            logger.info(s"Binding failed with ${e.getMessage}")
            system.terminate()
        }
      }
    }
  }

  def stop(): Unit = {
    // TODO shutdow
  }
}
