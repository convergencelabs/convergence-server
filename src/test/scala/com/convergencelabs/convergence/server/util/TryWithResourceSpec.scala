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

package com.convergencelabs.convergence.server.util

import com.convergencelabs.convergence.server.InducedTestingException
import org.mockito.Mockito
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

// scalastyle:off magic.number null
class TryWithResourceSpec
    extends AnyWordSpec
    with Matchers {

  val executionMessage = "execution"
  val closeMessage = "close"

  "A TryWithResources" when {
    "why trying" must {
      "fail if the resource can't be closed" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        Mockito.when(resource.close()).thenThrow(InducedTestingException())
        val result = TryWithResource(resource) { _ =>
          1
        }

        result.failure
      }

      "fail if the resource can't be resolved" in {

        def getResource: AutoCloseable = {
          throw InducedTestingException()
        }

        val result = TryWithResource(getResource) { _ =>
          1
        }

        result.failure
      }

      "fail if the code block throws an exception" in {

        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { _ =>
          throw new RuntimeException(executionMessage)
        }
        Mockito.verify(resource, Mockito.times(1)).close()

        result.failure.exception shouldBe a[RuntimeException]
        result.failure.exception.getMessage shouldBe executionMessage
      }

      "add a suppressed exception if the code block throws and the resource can't be closed" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        Mockito.when(resource.close()).thenThrow(InducedTestingException(closeMessage))

        val result = TryWithResource(resource) { _ =>
          throw InducedTestingException(executionMessage)
        }

        result.failure.exception shouldBe a[InducedTestingException]
        result.failure.exception.getMessage shouldBe executionMessage

        val suppressed = result.failure.exception.getSuppressed
        suppressed.length shouldBe 1
        suppressed(0) shouldBe a[InducedTestingException]
        suppressed(0).getMessage shouldBe closeMessage
      }

      "succeed if the code block succeeds and the close succeeds" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { _ =>
          4
        }
        Mockito.verify(resource, Mockito.times(1)).close()
        result.success.value shouldBe 4
      }

      "allow a null resource" in {
        val result = TryWithResource(null) { _ =>
          4
        }
        result.success.value shouldBe 4
      }
    }
  }
}
