package com.convergencelabs.server.domain

import akka.testkit.TestKit
import org.scalatest.mock.MockitoSugar
import org.scalatest.BeforeAndAfterAll
import akka.actor.ActorSystem
import org.scalatest.WordSpecLike
import org.junit.runner.RunWith
import org.scalatest.junit.JUnitRunner
import org.json4s.JsonAST.JObject
import com.convergencelabs.server.datastore.domain.ModelData
import org.json4s.JsonAST.JString
import com.convergencelabs.server.datastore.domain.ModelMetaData
import com.convergencelabs.server.datastore.domain.SnapshotMetaData
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import akka.testkit.TestProbe
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.ProtocolConfiguration
import org.mockito.Mockito
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.convergencelabs.server.datastore.ConfigurationStore
import com.convergencelabs.server.datastore.DomainConfig
import com.convergencelabs.server.datastore.TokenPublicKey
import com.convergencelabs.server.datastore.TokenKeyPair
import scala.concurrent.Future
import com.convergencelabs.server.datastore.domain.DomainUserStore
import scala.concurrent.Await

@RunWith(classOf[JUnitRunner])
class AuthenticationHandlerSpec()
    extends TestKit(ActorSystem("AuthManagerActorSpec"))
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  // FIXME we need to test that models actually get created and deteled.  Not sure how to do this.

  "A AuthenticationHandler" when {
    "authenticating a user by password" must {
      "authetnicate successfully for a correct username and password" in new TestFixture {
        val f = authHandler.authenticate(PasswordAuthRequest(existingUser, existingCorrectPassword))
        val result = Await.result(f, FiniteDuration(1, TimeUnit.SECONDS))
        assert(result == AuthenticationSuccess(existingUser))
      }
      
      "Fail authetnication for an incorrect username and password" in new TestFixture {
        val f = authHandler.authenticate(PasswordAuthRequest(existingUser, existingIncorrectPassword))
        val result = Await.result(f, FiniteDuration(1, TimeUnit.SECONDS))
        assert(result == AuthenticationFailure)
      }
      
      "fail authenticatoin for a user that does not exist" in new TestFixture {
        val f = authHandler.authenticate(PasswordAuthRequest(nonExistingUser, ""))
        val result = Await.result(f, FiniteDuration(1, TimeUnit.SECONDS))
        assert(result == AuthenticationFailure)
      }
    }
  }

  trait TestFixture {
    val existingUser = "existing"
    val existingCorrectPassword = "correct"
    val existingIncorrectPassword = "incorrect"

    val nonExistingUser = "non-existing"

    val domainFqn = DomainFqn("convergence", "default")
    val keys = Map[String, TokenPublicKey]()
    val adminKeyPair = TokenKeyPair("", "")
    val domainConfig = DomainConfig(
      "d1",
      domainFqn,
      "Default",
      "",
      "",
      keys,
      adminKeyPair)

    val userStore = mock[DomainUserStore]
    Mockito.when(userStore.domainUserExists(existingUser)).thenReturn(true)
    Mockito.when(userStore.domainUserExists(nonExistingUser)).thenReturn(false)
    Mockito.when(userStore.validateCredentials(existingUser, existingCorrectPassword)).thenReturn(true)
    Mockito.when(userStore.validateCredentials(existingUser, existingIncorrectPassword)).thenReturn(false)
    Mockito.when(userStore.validateCredentials(nonExistingUser, "")).thenReturn(false)
    
    val authHandler = new AuthenticationHandler(domainConfig, userStore, system.dispatcher)
  }
}