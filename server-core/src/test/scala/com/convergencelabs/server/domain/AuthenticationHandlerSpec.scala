package com.convergencelabs.server.domain

import java.io.StringReader
import java.security.KeyFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration.FiniteDuration
import scala.util.Failure
import scala.util.Success

import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.util.io.pem.PemReader
import org.jose4j.jws.AlgorithmIdentifiers
import org.jose4j.jws.JsonWebSignature
import org.jose4j.jwt.JwtClaims
import org.mockito.Mockito
import org.mockito.{Matchers => MockitoMatchers}
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Matchers
import org.scalatest.WordSpecLike
import org.scalatest.mockito.MockitoSugar

import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.DomainUserStore.CreateNormalDomainUser
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore
import com.convergencelabs.server.domain.model.SessionKey

import akka.actor.ActorSystem
import akka.testkit.TestKit
import akka.testkit.TestProbe
import com.convergencelabs.server.datastore.domain.SessionStore
import com.convergencelabs.server.datastore.domain.UserGroupStore
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.domain.schema.DomainSchema

class AuthenticationHandlerSpec()
    extends TestKit(ActorSystem("AuthManagerActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar
    with Matchers {

  override def afterAll(): Unit = {
    TestKit.shutdownActorSystem(system)
  }

  "A AuthenticationHandler" when {
    "authenticating a user by password" must {
      "authetnicate successfully for a correct username and password" in new TestFixture {
        val result = authHandler.authenticate(PasswordAuthRequest(existingUserName, existingCorrectPassword)).get
        result shouldBe AuthenticationSuccess(existingUserName, SessionKey(existingUserName, "1"), "123")
      }

      "Fail authetnication for an incorrect username and password" in new TestFixture {
        val result = authHandler.authenticate(PasswordAuthRequest(existingUserName, existingIncorrectPassword)).get
        result shouldBe AuthenticationFailure
      }

      "fail authenticatoin for a user that does not exist" in new TestFixture {
        val result = authHandler.authenticate(PasswordAuthRequest(nonExistingUser, "")).get
        result shouldBe AuthenticationFailure
      }

      "return an authenticatoin error when validating the cretentials fails" in new TestFixture {
        val result = authHandler.authenticate(PasswordAuthRequest(authfailureUser, authfailurePassword)).get
        result shouldBe AuthenticationError
      }
    }

    "authenticating a user by token" must {
      "successfully authenticate a user with a valid key" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(existingUserName, enabledKey.id))).get
        result shouldBe AuthenticationSuccess(existingUserName, SessionKey(existingUserName, "1"), "123")
      }

      "return an authentication failure for a non-existent key" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(existingUserName, missingKey))).get
        result shouldBe AuthenticationFailure
      }

      "return an authentication failure for a disabled key" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(existingUserName, disabledKey.id))).get
        result shouldBe AuthenticationFailure
      }

      "return an authentication failure for an invalid key" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(existingUserName, invalidKey.id))).get
        result shouldBe AuthenticationFailure
      }

      "return an authentication success for the admin key" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(existingUserName, AuthenticationHandler.AdminKeyId))).get
        val expectedUsername = DomainUserStore.adminUsername(existingUserName)
        result shouldBe AuthenticationSuccess(expectedUsername, SessionKey(expectedUsername, "1", true), "123")
      }

      "return an authentication success lazily created user" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(lazyUserName, enabledKey.id))).get
        result shouldBe AuthenticationSuccess(lazyUserName, SessionKey(lazyUserName, "1"), "123")
      }

      "return an authentication failure when the user can't be looked up" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(brokenUserName, enabledKey.id))).get
        result shouldBe AuthenticationError
      }

      "return an authentication failure when the user can't be created" in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(JwtGenerator.generate(brokenLazyUsername, enabledKey.id))).get
        result shouldBe AuthenticationError
      }
      
      "return an authentication failure when a new user has a duplicate email." in new TestFixture {
        val result = authHandler.authenticate(JwtAuthRequest(
            JwtGenerator.generate(duplicateEmailJwtUser.username, enabledKey.id, Map(JwtClaimConstants.Email -> duplicateEmailJwtUser.email.get)))).get
        result shouldBe AuthenticationError
      }
    }
  }

  trait TestFixture {
    val clientActor = new TestProbe(system).ref
    
    val sessionId = 0
    val existingUserName = "existing"
    val existingUser = DomainUser(DomainUserType.Normal, existingUserName, None, None, None, Some("existing@example.com"))

    val existingCorrectPassword = "correct"
    val existingIncorrectPassword = "incorrect"

    val nonExistingUser = "non-existing"

    val domainFqn = DomainFqn("convergence", "default")

    def nextSessionId(): String = {
      val nextSessionId = sessionId + 1
      nextSessionId.toString()
    }

    val sessionStore = mock[SessionStore]
    Mockito.when(sessionStore.nextSessionId).thenReturn(Success(nextSessionId()))
    
    
    val userStore = mock[DomainUserStore]
    
    Mockito.when(userStore.createReconnectToken(MockitoMatchers.any())).thenReturn(Success("123"))
    
    Mockito.when(userStore.domainUserExists(existingUserName)).thenReturn(Success(true))
    Mockito.when(userStore.adminUserExists(existingUserName)).thenReturn(Success(true))
    Mockito.when(userStore.getDomainUserByUsername(existingUserName)).thenReturn(Success(Some(existingUser)))

    Mockito.when(userStore.domainUserExists(nonExistingUser)).thenReturn(Success(false))
    Mockito.when(userStore.adminUserExists(nonExistingUser)).thenReturn(Success(false))
    Mockito.when(userStore.getDomainUserByUsername(nonExistingUser)).thenReturn(Success(None))

    Mockito.when(userStore.validateCredentials(existingUserName, existingCorrectPassword)).thenReturn(Success(true))
    Mockito.when(userStore.validateCredentials(existingUserName, existingIncorrectPassword)).thenReturn(Success(false))
    Mockito.when(userStore.validateCredentials(nonExistingUser, "")).thenReturn(Success(false))

    val lazyUserName = "newUserName"
    val lazyUser = CreateNormalDomainUser(lazyUserName, None, None, None, None)
    Mockito.when(userStore.getDomainUserByUsername(lazyUserName)).thenReturn(Success(None))
    Mockito.when(userStore.createNormalDomainUser(lazyUser)).thenReturn(Success(lazyUserName))
    Mockito.when(userStore.createAdminDomainUser(lazyUserName)).thenReturn(Success(lazyUserName))
    Mockito.when(userStore.domainUserExists(lazyUserName)).thenReturn(Success(false))
    Mockito.when(userStore.adminUserExists(lazyUserName)).thenReturn(Success(false))

    val brokenUserName = "brokenUser"
    Mockito.when(userStore.getDomainUserByUsername(brokenUserName)).thenReturn(Failure(new IllegalStateException("induced error for testing")))
    Mockito.when(userStore.domainUserExists(brokenUserName)).thenReturn(Failure(new IllegalStateException("induced error for testing")))
    Mockito.when(userStore.adminUserExists(brokenUserName)).thenReturn(Failure(new IllegalStateException("induced error for testing")))

    val brokenLazyUsername = "borkenLazyUserName"
    val brokenLazyUser = CreateNormalDomainUser(brokenLazyUsername, None, None, None, None)
    Mockito.when(userStore.getDomainUserByUsername(brokenLazyUsername)).thenReturn(Success(None))
    Mockito.when(userStore.createNormalDomainUser(brokenLazyUser)).thenReturn(Failure(new IllegalStateException("induced error for testing")))
    Mockito.when(userStore.domainUserExists(brokenLazyUsername)).thenReturn(Success(false))
    Mockito.when(userStore.adminUserExists(brokenLazyUsername)).thenReturn(Success(false))

    val duplicateEmailJwtUser = CreateNormalDomainUser(brokenLazyUsername, None, None, None, Some("test@example.com"))
    Mockito.when(userStore.createNormalDomainUser(duplicateEmailJwtUser)).thenReturn(Failure(new DuplicateValueException(DomainSchema.Classes.User.Fields.Email)))
    
    val authfailureUser = "authFailureUser"
    val authfailurePassword = "authFailurePassword"
    Mockito.when(userStore.validateCredentials(authfailureUser, authfailurePassword)).thenReturn(Failure(new IllegalStateException("Induced filure in testing")))
    Mockito.when(userStore.domainUserExists(authfailureUser)).thenReturn(Success(false))

    Mockito.when(userStore.domainUserExists(existingUserName)).thenReturn(Success(true))
    
    Mockito.when(userStore.updateDomainUser(MockitoMatchers.any())).thenReturn(Success(()))
    
    val userGroupStore = mock[UserGroupStore]
    Mockito.when(userGroupStore.setGroupsForUser(MockitoMatchers.any(), MockitoMatchers.any())).thenReturn(Success(()))
    
    val domainConfigStore = mock[DomainConfigStore]
    Mockito.when(domainConfigStore.isAnonymousAuthEnabled()).thenReturn(Success(true))

    val keyStore = mock[JwtAuthKeyStore]

    val enabledKey = JwtAuthKey(
      "enabledkey",
      "An enabled key",
      Instant.now(),
      KeyConstants.PublicKey,
      true)
    Mockito.when(keyStore.getKey(enabledKey.id)).thenReturn(Success(Some(enabledKey)))

    val adminKeyPair = JwtKeyPair(KeyConstants.PublicKey, KeyConstants.PrivateKey)
    Mockito.when(domainConfigStore.getAdminKeyPair()).thenReturn(Success(adminKeyPair))

    val disabledKey = JwtAuthKey(
      "disabledkey",
      "A disabled key",
      Instant.now(),
      KeyConstants.PublicKey,
      false)
    Mockito.when(keyStore.getKey(disabledKey.id)).thenReturn(Success(Some(disabledKey)))

    val invalidKey = JwtAuthKey(
      "invalidKey",
      "An invalid key",
      Instant.now(),
      "invalid",
      true)
    Mockito.when(keyStore.getKey(invalidKey.id)).thenReturn(Success(Some(invalidKey)))

    val missingKey = "missingKey"
    Mockito.when(keyStore.getKey(missingKey)).thenReturn(Success(None))

    val authHandler = new AuthenticationHandler(domainFqn, domainConfigStore, keyStore, userStore, userGroupStore, sessionStore, system.dispatcher)
  }

}

object JwtGenerator {

  def generate(username: String, keyId: String, claims: Map[String, Any] = Map()): String = {

    val pemReader = new PemReader(new StringReader(KeyConstants.PrivateKey))
    val obj = pemReader.readPemObject()
    pemReader.close()

    val keyFactory = KeyFactory.getInstance("RSA", new BouncyCastleProvider())
    val privateKeySpec = new PKCS8EncodedKeySpec(obj.getContent())
    val privateKey = keyFactory.generatePrivate(privateKeySpec)

    // Create the claims with the basic info.
    val jwtClaims = new JwtClaims()
    jwtClaims.setAudience(JwtConstants.Audiance)
    jwtClaims.setGeneratedJwtId()
    jwtClaims.setExpirationTimeMinutesInTheFuture(2)
    jwtClaims.setIssuedAtToNow()
    jwtClaims.setNotBeforeMinutesInThePast(10) // scalastyle:ignore magic.number

    claims.foreach { case (k, v) =>
       jwtClaims.setClaim(k, v)  
    }
    
    // Add claims the user is providing.
    jwtClaims.setSubject(username)

    // The JWS will be used to sign the payload.
    val jws = new JsonWebSignature()
    jws.setPayload(jwtClaims.toJson())
    jws.setKey(privateKey)

    // We set the Key Id so that the server knows which key to check against.
    jws.setKeyIdHeaderValue(keyId)
    jws.setAlgorithmHeaderValue(AlgorithmIdentifiers.RSA_USING_SHA256)
    jws.getCompactSerialization()
  }
}

object KeyConstants {
  val PublicKey =
    """-----BEGIN PUBLIC KEY-----
MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAosMIHKyiFkRZFdE2GKJZ
WR7CYgHR0GiIl3Z1B5g/hNOBieFktI4SI3eINEe8HYOBMXHlC+4oueCH0auNRdqq
fAGw/fiwiNLi5t9Q0fDIkyrEP+brqnYlwKzu5G3LpETheRKf33CkytKxsmKJec+j
B6Vu0E0XHxi3ZPIpGlr5VTdCt4T6d5i0QXCKnmMImOWgHXWT3YBdMNl/utG8eMyY
m8xo9e4xDFL98onQFPOLlWn7yZwhn9//t9L2BomLUGSJWvZYYuLfUbL/8xpbcrQ4
8ey+IIDXeIlUvhJbJ3hC2PjK1n0Pr8Zx3leoQG/sA/UU8wfK/End+RTo1XAy2+sM
N/LFwAXqiY7Ol9sD6ja+oS88dwhGs2742Vbjt0ytntdR00Smw/3g6fER1vUbox3/
G0EbKbulFXNsgqTF0Feut0ABv+PiyPgNRdjRAe/qaCOBYm5p7o198GSWiC6X0B1P
8Mujvc6fL+d9ARU0sBLfz5wTTnGFClQ1/DdG3Qhvtl5/H02lCKTS3+8lfIGXrzWt
onts35f/ovP6Y1lZYxuqkHwAsjeVNw7bnPZAo9M82G3XxirNJ9V524uuT4z6r/Xs
44KOEzsM25xsqXPcYE2VW59cSQ7tA+QU+n7hQRRRI7lemgaJnTQiecnYOlcMk8lz
JAc7zsZt/t/JIFxGZ8LFoXsCAwEAAQ==
-----END PUBLIC KEY-----"""

  val PrivateKey =
    """-----BEGIN RSA PRIVATE KEY-----
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
-----END RSA PRIVATE KEY-----"""
}
