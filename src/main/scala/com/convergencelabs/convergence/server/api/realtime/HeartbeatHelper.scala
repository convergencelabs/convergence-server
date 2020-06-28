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

import akka.actor.Cancellable
import akka.actor.typed.Scheduler
import grizzled.slf4j.Logging

import scala.concurrent.ExecutionContext
import scala.concurrent.duration.FiniteDuration

/**
 * A helper class that will generate and consume the ping-pong heartbeat
 * messages that keep the web socket open and detect disconnections.
 *
 * @param pingInterval The timespan after the last received message from
 *                     client to generate a ping message.
 * @param pongTimeout  How long to wait for a response from the client before
 *                     considering the client disconnected.
 * @param scheduler    The scheduler to use to schedule future events.
 * @param ec           The execution context to use for asynchronous events.
 * @param handler      The callback handler to handle events from the heartbeat helper.
 */
private[realtime] class HeartbeatHelper(pingInterval: FiniteDuration,
                                        pongTimeout: FiniteDuration,
                                        scheduler: Scheduler,
                                        ec: ExecutionContext,
                                        handler: PartialFunction[HeartbeatHelper.HeartbeatEvent, Unit])
  extends Logging {

  import HeartbeatHelper._

  private[this] var pingFuture: Option[Cancellable] = None
  private[this] var timeoutFuture: Option[Cancellable] = None
  private[this] var _started: Boolean = false

  /**
   * Starts the heartbeat helper.
   */
  def start(): Unit = {
    if (!_started) {
      logger.debug(s"HeartbeatHelper started with Ping Interval $pingInterval and Pong Timeout $pongTimeout")
      this._started = true
      this.messageReceived()
    } else {
      throw new IllegalStateException("already started")
    }
  }

  /**
   * Determines if the heartbeat helper is started.
   *
   * @return True if the heartbeat helper is started; false otherwise.
   */
  def started(): Boolean = _started

  /**
   * Determines if the heartbeat helper is stopped.
   *
   * @return True if the heartbeat helper is stopped; false otherwise.
   */
  def stopped(): Boolean = !_started

  /**
   * Stops the heartbeat helper, ceasing all scheduled events and timeouts.
   */
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

  /**
   * Signifies that a message has been received from the client.
   */
  def messageReceived(): Unit = {
    if (_started) {
      logger.trace("Message received, resetting heartbeat intervals.")
      cancelPongTimeout()
      restartPingTimeout()
    } else {
      throw new IllegalStateException("not started")
    }
  }

  private[this] def sendPing(): Unit = {
    handler lift PingRequest
    logger.trace("Requesting pint, scheduling the pong timeout.")
    cancelPongTimeout()
    this.timeoutFuture = Some(scheduler.scheduleOnce(pongTimeout, () => onPongTimeout())(ec))
  }

  private[this] def onPongTimeout(): Unit = {
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
    pingFuture = Some(scheduler.scheduleOnce(pingInterval, () => sendPing())(ec))
  }
}

private[realtime] object HeartbeatHelper {
  /**
   * A sealed trait that represents the events that the heartbeat helper
   * can generate.
   */
  sealed trait HeartbeatEvent

  /**
   * Signifies that the a ping should be sent to the client.
   */
  final case object PingRequest extends HeartbeatEvent

  /**
   * Signifies that a message was not received before the pong
   * timeout occurred.
   */
  final case object PongTimeout extends HeartbeatEvent
}
