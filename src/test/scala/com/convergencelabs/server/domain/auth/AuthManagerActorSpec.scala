package com.convergencelabs.server.domain.auth

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
import com.convergencelabs.server.domain.DomainFqn
import org.mockito.Mockito
import scala.concurrent.duration.FiniteDuration
import java.util.concurrent.TimeUnit
import com.convergencelabs.server.ErrorMessage
import com.convergencelabs.server.ErrorMessage
import com.convergencelabs.server.datastore.ConfigurationStore
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.datastore.DomainConfig
import com.convergencelabs.server.datastore.TokenPublicKey
import com.convergencelabs.server.datastore.TokenKeyPair
import scala.concurrent.Future

@RunWith(classOf[JUnitRunner])
class AuthManagerActorSpec(system: ActorSystem)
    extends TestKit(system)
    with WordSpecLike
    with BeforeAndAfterAll
    with MockitoSugar {

  def this() = this(ActorSystem("AuthManagerActorSpec"))

  override def afterAll() {
    TestKit.shutdownActorSystem(system)
  }

  // FIXME we need to test that models actually get created and deteled.  Not sure how to do this.

  "A AuthManagerActor" when {
    "authenticating a user by password" must {
      "authetnicate successfully for a correct username and password" in new TestFixture {
        val client = new TestProbe(system)
        authManagerActor.tell(PasswordAuthRequest(existingUser, existingCorrectPassword), client.ref)
        val response = client.expectMsgClass(FiniteDuration(1, TimeUnit.SECONDS), classOf[AuthSuccess])
      }
      
      "Fail authetnication for an incorrect username and password" in new TestFixture {
        val client = new TestProbe(system)
        authManagerActor.tell(PasswordAuthRequest(existingUser, existingIncorrectPassword), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), AuthFailure)
      }
      
      "fail authenticatoin for a user that does not exist" in new TestFixture {
        val client = new TestProbe(system)
        authManagerActor.tell(PasswordAuthRequest(nonExistingUser, ""), client.ref)
        val response = client.expectMsg(FiniteDuration(1, TimeUnit.SECONDS), AuthFailure)
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
      JObject(),
      keys,
      adminKeyPair)

    val domainPersistence = mock[DomainPersistenceProvider]
    val internalAuthProvider = mock[InternalDomainAuthenticationProvider]

    Mockito.when(internalAuthProvider.userExists(domainFqn, existingUser)).thenReturn(Future.successful(true))
    Mockito.when(internalAuthProvider.userExists(domainFqn, nonExistingUser)).thenReturn(Future.successful(false))
   
    Mockito.when(internalAuthProvider.verfifyCredentials(
      domainFqn, existingUser, existingCorrectPassword)).thenReturn(Future.successful(true))
    Mockito.when(internalAuthProvider.verfifyCredentials(
      domainFqn, existingUser, existingIncorrectPassword)).thenReturn(Future.successful(false))
    Mockito.when(internalAuthProvider.verfifyCredentials(
      domainFqn, nonExistingUser, "")).thenReturn(Future.successful(false))

    val props = AuthManagerActor.props(
      domainConfig,
      domainPersistence,
      internalAuthProvider)

    val authManagerActor = system.actorOf(props)
  }
}