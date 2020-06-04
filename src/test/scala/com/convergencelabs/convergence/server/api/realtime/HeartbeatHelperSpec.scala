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

import akka.actor.testkit.typed.scaladsl.{ManualTime, ScalaTestWithActorTestKit}
import com.miguno.akka.testing.VirtualTime
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Promise}

// scalastyle:off magic.number
class HeartbeatHelperSpec
  extends ScalaTestWithActorTestKit(ManualTime.config)
    with AnyWordSpecLike
    with Matchers
    with ScalaFutures {

  private[this] implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private[this] val pingInterval = FiniteDuration(5, TimeUnit.SECONDS)
  private[this] val pongTimeout = FiniteDuration(10, TimeUnit.SECONDS)

  private[this] val resolutionTimeout = FiniteDuration(250, TimeUnit.MILLISECONDS)

  private[this] val manualTime: ManualTime = ManualTime()

  "A HeartbeatHelper" when {
    "started" must {
      "emit a ping request event once the ping interval has been reached" in {
        val p = Promise[Unit]

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest => p.success(())
          case PongTimeout =>
        })

        hbh.start()

        manualTime.timePasses(pingInterval)

        Await.ready(p.future, resolutionTimeout)

        hbh.stop()
      }

      "emit a pong timeout event once the ping interval plus pong timeout has been reached" in {
        val time = new VirtualTime

        val p = Promise[Unit]

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout => p.success(())
        })

        hbh.start()

        time.advance(pingInterval)
        time.advance(pongTimeout)

        Await.ready(p.future, resolutionTimeout)

        hbh.stop()
      }

      "not emit a pong timeout event if a message is received in time" in {
        val time = new VirtualTime

        val p = Promise[Unit]

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout => p.success(())
        })

        hbh.start()

        time.advance(pingInterval)

        hbh.messageReceived()

        time.advance(pongTimeout)

        // Not sure how else to do this.
        Thread.sleep(250)

        p.future.isCompleted shouldBe false
        hbh.stop()
      }

      "indicate that it is started" in {
        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        hbh.start()

        hbh.started() shouldBe true
        hbh.stopped() shouldBe false

        hbh.stop()
      }

      "throw an exception if start is called" in {
        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        hbh.start()

        intercept[IllegalStateException] {
          hbh.start()
        }
      }
    }

    "stopped" must {
      "indicate that it is stopped" in {
        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        hbh.started() shouldBe false
        hbh.stopped() shouldBe true

        hbh.start()

        hbh.started() shouldBe true
        hbh.stopped() shouldBe false

        hbh.stop()

        hbh.started() shouldBe false
        hbh.stopped() shouldBe true
      }

      "throw an exception if stop is called" in {
        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        intercept[IllegalStateException] {
          hbh.stop()
        }
      }

      "throw an exception if message received is called" in {
        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, testKit.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        intercept[IllegalStateException] {
          hbh.messageReceived()
        }
      }
    }
  }
}
