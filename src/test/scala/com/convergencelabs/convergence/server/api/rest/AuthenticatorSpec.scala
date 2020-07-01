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

package com.convergencelabs.convergence.server.api.rest

import java.util.concurrent.TimeUnit

import akka.actor.typed.scaladsl.adapter._
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.{Authorization, GenericHttpCredentials}
import akka.http.scaladsl.server.Directives.{complete, extractRequest}
import akka.http.scaladsl.testkit.{RouteTestTimeout, ScalatestRouteTest}
import akka.testkit.{TestDuration, TestProbe}
import akka.util.Timeout
import com.convergencelabs.convergence.server.backend.datastore.convergence.RoleStore.UserRoles
import com.convergencelabs.convergence.server.backend.services.server.AuthenticationActor
import com.convergencelabs.convergence.server.security.AuthorizationProfileData
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatestplus.mockito.MockitoSugar

import scala.concurrent.Future
import scala.concurrent.duration.{FiniteDuration, _}

class AuthenticatorSpec extends AnyWordSpecLike with Matchers with ScalatestRouteTest
  with MockitoSugar {

  implicit val timeout: RouteTestTimeout = RouteTestTimeout(5.seconds.dilated)
  private val Success = "success"

  private val SessionToken = "a session token"
  private val SessionTokenHeader = Authorization(GenericHttpCredentials(Authenticator.SessionTokenScheme, SessionToken))
  private val SessionTokenGet =  Get().addHeader(SessionTokenHeader)

  private val BearerToken = "a bearer token"
  private val BearerTokenHeader = Authorization(GenericHttpCredentials(Authenticator.BearerTokenScheme, BearerToken))
  private val BearerTokenGet =  Get().addHeader(BearerTokenHeader)

  private val ApiKey = "a user api key"
  private val ApiKeyHeader = Authorization(GenericHttpCredentials(Authenticator.UserApiKeyScheme, ApiKey))
  private val ApiKeyGet =  Get().addHeader(ApiKeyHeader)

  "A Authenticator" when {
    "handling invalid requests" must {
      "return Unauthorized if no credentials are provided" in {
        val request = Get()
        val authActor: TestProbe = TestProbe()
        test(request, authActor) ~> check {
          status shouldEqual StatusCodes.Unauthorized
        }
      }

      "return Unauthorized if an unexpected auth header is provided" in {
        val unknownHeader = Authorization(GenericHttpCredentials("invalid", "invalid"))
        val request = Get().addHeader(unknownHeader)

        val authActor: TestProbe = TestProbe()
        test(request, authActor) ~> check {
          status shouldEqual StatusCodes.Unauthorized
        }
      }
    }

    "handling Session Tokens" must {
      "return Internal Server Error if no response is received" in {
        val authActor: TestProbe = TestProbe()
        test(SessionTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }

      "complete if a Session Token is valid" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateSessionTokenRequest])
          msg.token shouldBe SessionToken
          msg.replyTo ! AuthenticationActor.ValidateResponse(Right(AuthorizationProfileData("username", UserRoles("username", Set()))))
        }

        test(SessionTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual Success
        }
      }

      "return Unauthorized if a Session Token is invalid" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateSessionTokenRequest])
          msg.token shouldBe SessionToken
          msg.replyTo ! AuthenticationActor.ValidateResponse(Left(AuthenticationActor.ValidationFailed()))
        }

        test(SessionTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.Unauthorized
        }
      }

      "return InternalServerError if a Session Token fails" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateSessionTokenRequest])
          msg.token shouldBe SessionToken
          msg.replyTo ! AuthenticationActor.ValidateResponse(Left(AuthenticationActor.UnknownError()))
        }

        test(SessionTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }
    }

    "handling Bearer Tokens" must {
      "return Internal Server Error if no response is received" in {
        val authActor: TestProbe = TestProbe()
        test(BearerTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }

      "complete if a Session Token is valid" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateUserBearerTokenRequest])
          msg.bearerToken shouldBe BearerToken
          msg.replyTo ! AuthenticationActor.ValidateResponse(Right(AuthorizationProfileData("username", UserRoles("username", Set()))))
        }

        test(BearerTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual Success
        }
      }

      "return Unauthorized if a Session Token is invalid" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateUserBearerTokenRequest])
          msg.bearerToken shouldBe BearerToken
          msg.replyTo ! AuthenticationActor.ValidateResponse(Left(AuthenticationActor.ValidationFailed()))
        }

        test(BearerTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.Unauthorized
        }
      }

      "return InternalServerError if a Session Token fails" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateUserBearerTokenRequest])
          msg.bearerToken shouldBe BearerToken
          msg.replyTo ! AuthenticationActor.ValidateResponse(Left(AuthenticationActor.UnknownError()))
        }

        test(BearerTokenGet, authActor) ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }
    }

    "handling User API Keys" must {
      "return Internal Server Error if no response is received" in {
        val authActor: TestProbe = TestProbe()
        test(ApiKeyGet, authActor) ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }

      "complete if a Session Token is valid" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateUserApiKeyRequest])
          msg.apiKey shouldBe ApiKey
          msg.replyTo ! AuthenticationActor.ValidateResponse(Right(AuthorizationProfileData("username", UserRoles("username", Set()))))
        }

        test(ApiKeyGet, authActor) ~> check {
          status shouldEqual StatusCodes.OK
          responseAs[String] shouldEqual Success
        }
      }

      "return Unauthorized if a Session Token is invalid" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateUserApiKeyRequest])
          msg.apiKey shouldBe ApiKey
          msg.replyTo ! AuthenticationActor.ValidateResponse(Left(AuthenticationActor.ValidationFailed()))
        }

        test(ApiKeyGet, authActor) ~> check {
          status shouldEqual StatusCodes.Unauthorized
        }
      }

      "return InternalServerError if a Session Token fails" in {
        val authActor: TestProbe = TestProbe()

        Future {
          val msg = authActor.expectMsgClass(FiniteDuration(250, TimeUnit.MILLISECONDS), classOf[AuthenticationActor.ValidateUserApiKeyRequest])
          msg.apiKey shouldBe ApiKey
          msg.replyTo ! AuthenticationActor.ValidateResponse(Left(AuthenticationActor.UnknownError()))
        }

        test(ApiKeyGet, authActor) ~> check {
          status shouldEqual StatusCodes.InternalServerError
        }
      }
    }
  }

  def test(request: HttpRequest, authActor: TestProbe): RouteTestResult = {
    val timeout: Timeout = new Timeout(250, TimeUnit.MILLISECONDS)
    val typedSystem = system.toTyped
    val authenticator = new Authenticator(
      authActor.ref.toTyped[AuthenticationActor.Message],
      typedSystem.scheduler,
      typedSystem.executionContext,
      timeout)


    request ~> extractRequest { request =>
      authenticator.requireAuthenticatedUser(request) { _ =>
        complete(Success)
      }
    }
  }
}
