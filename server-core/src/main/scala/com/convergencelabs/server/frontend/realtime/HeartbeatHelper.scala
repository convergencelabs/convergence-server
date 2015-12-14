package com.convergencelabs.server.frontend.realtime

import grizzled.slf4j.Logging
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.Scheduler
import akka.actor.Cancellable
import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

sealed trait HeartbeatEvent
case object PingRequest extends HeartbeatEvent
case object PongTimeout extends HeartbeatEvent

class HeartbeatHelper(
  private[this] val pingInterval: FiniteDuration,
  private[this] val pongTimeout: FiniteDuration,
  private[this] val scheduler: Scheduler,
  private[this] val ec: ExecutionContext,
  private[this] val handler: PartialFunction[HeartbeatEvent, Unit])
    extends Logging {

  private[this] var pingFuture: Option[Cancellable] = None
  private[this] var timeoutFuture: Option[Cancellable] = None
  private[this] var _started: Boolean = false

  def messageReceived(): Unit = {
    if (_started) {
      logger.trace("Message recieved, resetting timeouts.")
      cancelPongTimeout()
      restartPingTimeout()
    } else {
      throw new IllegalStateException("not started")
    }
  }

  def start(): Unit = {
    if (!_started) {
      logger.debug(s"HeartbeatHelper started with Ping Interval $pingInterval and Pong Timeout $pongTimeout")
      this._started = true
      this.messageReceived()
    } else {
      throw new IllegalStateException("already started")
    }
  }

  def started(): Boolean = _started

  def stopped(): Boolean = !_started

  def stop(): Unit = {
    if (_started) {
      logger.debug("HeartbeatHelper stopped.")
      this._started = false
      stopPingTimer()
      cancelPongTimeout()
    } else {
      throw new IllegalStateException("not started")
    }
  }

  private[this] def sendPing(): Unit = {
    handler lift PingRequest
    logger.trace("Requesting pint, scheduling the pong timeout.")
    this.timeoutFuture = Some(scheduler.scheduleOnce(pongTimeout)(onTimeout())(ec))
  }

  private[this] def onTimeout(): Unit = {
    logger.debug("PONG Timeout Exceeded")
    if (_started) {
      handler lift PongTimeout
    }
  }

  private[this] def cancelPongTimeout(): Unit = {
    if (timeoutFuture.isDefined && !timeoutFuture.get.isCancelled) {
      timeoutFuture.get.cancel()
      timeoutFuture = None
    }
  }

  private[this] def stopPingTimer(): Unit = {
    if (pingFuture.isDefined && !pingFuture.get.isCancelled) {
      pingFuture.get.cancel()
      pingFuture = None
    }
  }

  private[this] def restartPingTimeout(): Unit = {
    stopPingTimer()
    pingFuture = Some(scheduler.scheduleOnce(pingInterval)(sendPing())(ec))
  }
}
