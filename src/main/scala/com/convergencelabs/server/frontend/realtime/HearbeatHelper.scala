package com.convergencelabs.server.frontend.realtime

import grizzled.slf4j.Logging
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import akka.actor.Scheduler
import akka.actor.Cancellable
import scala.concurrent.duration.Duration
import java.util.concurrent.TimeUnit

sealed trait HeartbeatEvent
case object PingRequest extends HeartbeatEvent
case object PongTimeout extends HeartbeatEvent

class HearbeatHelper(
  private[this] val pingInterval: Int,
  private[this] val pongTimeout: Int,
  private[this] val scheduler: Scheduler,
  private[this] val ec: ExecutionContext,
  private[this] val handler: PartialFunction[HeartbeatEvent, Unit])
    extends Logging {

  // VALIDATES

  private[this] var pingFuture: Cancellable = null
  private[this] var timeoutFuture: Cancellable = null
  private[this] var _started: Boolean = false

  def messageReceived(): Unit = {
    if (_started) {
      logger.trace("Message recieved, resetting timeouts.")
      cancelPongTimeout()
      restartPingTimeout()
    }
  }

  def start(): Unit = {
    if (!_started) {
      logger.debug(s"HeartbeatHelper started with Ping Interval $pingInterval and Pong Timeout $pongTimeout")
      this._started = true
      this.messageReceived()
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
    }
  }

  def sendPing(): Unit = {
    handler lift PingRequest
    schedulePongTimeout()
  }

  def onTimeout(): Unit = {
    logger.debug("PONG Timeout Exceeded")
    if (_started) {
      handler lift PongTimeout
    }
  }

  def schedulePongTimeout(): Unit = {
    if (this.timeoutFuture != null) {
      throw new IllegalStateException("Pong timeout already started.")
    }

    logger.trace("Scheduling the pong timeout.")
    this.timeoutFuture = scheduler.scheduleOnce(
      Duration.create(pongTimeout, TimeUnit.SECONDS))(onTimeout())(ec)
  }

  def cancelPongTimeout(): Unit = {
    if (timeoutFuture != null && !timeoutFuture.isCancelled) {
      timeoutFuture.cancel()
      timeoutFuture = null
    }
  }

  def stopPingTimer(): Unit = {
    if (pingFuture != null && !pingFuture.isCancelled) {
      pingFuture.cancel()
      pingFuture = null
    }
  }

  def restartPingTimeout(): Unit = {
    stopPingTimer()
    if (pingInterval > 0) {
      pingFuture = scheduler.scheduleOnce(
        Duration.create(pingInterval, TimeUnit.SECONDS))(sendPing())(ec)
    }
  }
}