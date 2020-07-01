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

package com.convergencelabs.convergence.server.backend.services.domain.chat.processors

import java.util.concurrent.TimeUnit

import akka.actor.testkit.typed.scaladsl.{ScalaTestWithActorTestKit, TestProbe}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.duration.FiniteDuration

class MessageReplyTaskSpec extends ScalaTestWithActorTestKit
  with AnyWordSpecLike
  with BeforeAndAfterAll
  with MockitoSugar {

  case class TestMessage()

  "A MessageReplyTaskSpec" when {
    "replying" must {
      "send the correct reply to the replyTo" in {
        val probe = TestProbe[TestMessage]()
        val message = TestMessage()
        val mr = MessageReplyTask(probe.ref, message)
        mr.execute()
        probe.expectMessage(FiniteDuration(100, TimeUnit.MILLISECONDS), message)
      }
    }
  }
}
