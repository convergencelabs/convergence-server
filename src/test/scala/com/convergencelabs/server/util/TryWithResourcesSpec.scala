package com.convergencelabs.server.util

import org.scalatest.WordSpec
import org.json4s._
import java.math.BigInteger
import scala.util.Success
import org.mockito.Mockito

class TryWithResourcesSpec extends WordSpec {

  "An TryWithResources" when {
    "why trying" must {
      "fail if the resource can't be closed" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        Mockito.when(resource.close()).thenThrow(new RuntimeException("close"))
        val result = TryWithResource(resource) { resource =>
          1
        }
        
        assert(result.isFailure)
      }
      
      "fail if the code block throws an exception" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { resource =>
          throw new RuntimeException("execution")
        }
        Mockito.verify(resource, Mockito.times(1)).close()
        assert(result.isFailure)
      }
      
      "succeed if the code block succeeds and the close succeeds" in {
        val resource = Mockito.mock(classOf[AutoCloseable])
        val result = TryWithResource(resource) { resource =>
          4
        }
        Mockito.verify(resource, Mockito.times(1)).close()
        assert(result == Success(4))
      }
    }
  }
}