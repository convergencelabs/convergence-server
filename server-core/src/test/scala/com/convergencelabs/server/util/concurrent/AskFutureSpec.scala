package com.convergencelabs.server.util.concurrent

import org.scalatest.WordSpec
import org.json4s._
import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.util.Success
import scala.util.Failure
import org.scalatest.concurrent.Futures
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.Matchers
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.language.postfixOps
import org.scalatest.TryValues._
import org.scalatest.OptionValues._
import com.convergencelabs.server.util.UnexpectedError
import akka.pattern.AskTimeoutException


class AskFutureSpec
    extends WordSpec
    with Matchers
    with ScalaFutures {

  "An AskFuture" when {
    "receiving the correct value" must {
      "must map to a success with the retuned value cast correctly" in {
        implicit val ec = ExecutionContext.global
        val f = Future[Any] {
          3
        }

        val response = f.mapResponse[Integer]

        whenReady(response) { v =>
          v shouldBe 3  
        }
      }
    }
    
    "resolved with the wrong type" must {
      "must fail with an UnexpectedResponseException if the wrong type is returned" in {
        implicit val ec = ExecutionContext.global
        val f = Future[Any] {
          3
        }

        val response = f.mapResponse[String]
        Await.ready(response, 1 second)
        val futureResult = response.value.value
        futureResult.failed.get shouldBe a[UnexpectedResponseException]
      }
    }
    
    "handling an unexpected error response" must {
      "must fail with an UnexpectedErrorException if the wrong type is returned" in {
        implicit val ec = ExecutionContext.global
        val f = Future[Any] {
          UnexpectedError("code", "reason")
        }

        val response = f.mapResponse[String]
        Await.ready(response, 1 second)
        val futureResult = response.value.value
        futureResult.failed.get shouldBe a[UnexpectedErrorException]
        val ex = futureResult.failed.get.asInstanceOf[UnexpectedErrorException]
        ex.code shouldBe "code"
        ex.message shouldBe "reason"
      }
    }
    
    "handling an failure" must {
      "must fail with the same cause" in {
        implicit val ec = ExecutionContext.global
        val exception = new AskTimeoutException("timeout")
        val f = Future[Any] {
          throw exception
        }

        val response = f.mapResponse[String]
        Await.ready(response, 1 second)
        val futureResult = response.value.value
        futureResult.failed.get shouldBe exception
      }
    }
  }
}