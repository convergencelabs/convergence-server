package com.convergencelabs.server.util

import org.mockito.Mockito
import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpec

class TryWithResourcesSpec
    extends WordSpec
    with Matchers {

  "An TryWithResources" when {
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
          throw new RuntimeException("execution")
        }
        Mockito.verify(resource, Mockito.times(1)).close()
        result.failure
      }

      "succeed if the code block succeeds and the close succeeds" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { resource =>
          4
        }
        Mockito.verify(resource, Mockito.times(1)).close()
        result.success.value shouldBe 4
      }
    }
  }
}
