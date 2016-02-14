package com.convergencelabs.server.util

import org.mockito.Mockito
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpec

// scalastyle:off magic.number null
class TryWithResourceSpec
    extends WordSpec
    with Matchers {

  val executionMessage = "execution"
  val closeMessage = "close"

  "A TryWithResources" when {
    "why trying" must {
      "fail if the resource can't be closed" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        Mockito.when(resource.close()).thenThrow(new RuntimeException("close"))
        val result = TryWithResource(resource) { resource =>
          1
        }

        result.failure
      }

      "fail if the resource can't be resolved" in {

        def getResource(): AutoCloseable = {
          throw new IllegalStateException("induced error")
        }

        val result = TryWithResource(getResource()) { resource =>
          1
        }

        result.failure
      }

      "fail if the code block throws an exception" in {

        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { resource =>
          throw new RuntimeException(executionMessage)
        }
        Mockito.verify(resource, Mockito.times(1)).close()

        result.failure.exception shouldBe a[RuntimeException]
        result.failure.exception.getMessage shouldBe executionMessage
      }

      "add a suppressed exception if the code block throws and the resource can't be closed" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        Mockito.when(resource.close()).thenThrow(new RuntimeException(closeMessage))

        val result = TryWithResource(resource) { resource =>
          throw new RuntimeException(executionMessage)
        }

        result.failure.exception shouldBe a[RuntimeException]
        result.failure.exception.getMessage shouldBe executionMessage

        val suppressed = result.failure.exception.getSuppressed
        suppressed.length shouldBe 1
        suppressed(0) shouldBe a[RuntimeException]
        suppressed(0).getMessage shouldBe closeMessage
      }

      "succeed if the code block succeeds and the close succeeds" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { resource =>
          4
        }
        Mockito.verify(resource, Mockito.times(1)).close()
        result.success.value shouldBe 4
      }

      "allow a null resource" in {
        val result = TryWithResource(null) { resource =>
          4
        }
        result.success.value shouldBe 4
      }
    }
  }
}
