package com.convergencelabs.server.frontend.realtime

import org.scalatest.WordSpec
import org.scalatest.Matchers
import scala.concurrent.ExecutionContext
import scala.concurrent.Promise
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.concurrent.Futures
import org.scalatest.time.{ Millis, Seconds, Span }
import akka.util.Timeout
import scala.concurrent.Await
import com.miguno.akka.testing.VirtualTime

class HeartbeatHelperSpec extends WordSpec with Matchers with ScalaFutures {

  implicit val ec = ExecutionContext.Implicits.global

  val pingInterval = FiniteDuration(5, TimeUnit.SECONDS)
  val pongTimeout = FiniteDuration(10, TimeUnit.SECONDS)

  val resolutionTimeout = FiniteDuration(250, TimeUnit.MILLISECONDS)

  "A HeartbeatHelper" when {
    "started" must {
      "emit a ping request event once the ping interval has been reached" in {
        val time = new VirtualTime

        val p = Promise[Unit]

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
          case PingRequest => p.success(Unit)
          case PongTimeout =>
        })

        hbh.start()

        time.advance(pingInterval)

        Await.ready(p.future, resolutionTimeout)

        hbh.stop()
      }

      "emit a pong timeout event once the ping interval plus pong timeout has been reached" in {
        val time = new VirtualTime

        val p = Promise[Unit]

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
          case PingRequest =>
          case PongTimeout => p.success(Unit)
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

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
          case PingRequest =>
          case PongTimeout => p.success(Unit)
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

      "inidcate that it is started" in {
        val time = new VirtualTime

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        hbh.start()

        hbh.started() shouldBe true
        hbh.stopped() shouldBe false

        hbh.stop()
      }

      "throw an exception if start is called" in {
        val time = new VirtualTime

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
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
      "inidcate that it is stopped" in {
        val time = new VirtualTime

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
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
        val time = new VirtualTime

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
          case PingRequest =>
          case PongTimeout =>
        })

        intercept[IllegalStateException] {
          hbh.stop()
        }
      }
      
      "throw an exception if message received is called" in {
        val time = new VirtualTime

        val hbh = new HeartbeatHelper(pingInterval, pongTimeout, time.scheduler, ec, {
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