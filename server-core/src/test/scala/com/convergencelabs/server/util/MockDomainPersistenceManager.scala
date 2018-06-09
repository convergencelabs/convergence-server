package com.convergencelabs.server.util
import org.mockito.Mockito

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.domain.DomainFqn

import akka.actor.ActorContext
import akka.actor.ActorRef
import com.convergencelabs.server.datastore.domain.CollectionStore
import org.scalatest.mockito.MockitoSugar
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainConfigStore
import com.convergencelabs.server.datastore.domain.ChatChannelStore
import com.convergencelabs.server.datastore.domain.DomainUserStore
import com.convergencelabs.server.datastore.domain.ModelOperationStore
import com.convergencelabs.server.datastore.domain.JwtAuthKeyStore
import com.convergencelabs.server.datastore.domain.SessionStore
import com.convergencelabs.server.datastore.domain.UserGroupStore
import com.convergencelabs.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.server.datastore.domain.ModelStore
import com.convergencelabs.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.server.datastore.domain.PermissionsStore
import com.convergencelabs.server.datastore.DatabaseProvider

class MockDomainPersistenceManager(val mockProviders: Map[DomainFqn, MockDomainPersistenceProvider]) extends DomainPersistenceManager {

  def acquirePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Try[MockDomainPersistenceProvider] = {
    mockProviders.get(domainFqn) match {
      case Some(provider) => Success(provider)
      case None => Failure(new IllegalArgumentException(s"Don't have provider for domain ${domainFqn}"))
    }
  }

  def releasePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainFqn): Unit = {

  }
}

class MockDomainPersistenceProvider extends DomainPersistenceProvider with MockitoSugar {
  
  val dbProvider: DatabaseProvider = mock[DatabaseProvider]
  
  val configStore: DomainConfigStore = mock[DomainConfigStore]

  val userStore: DomainUserStore = mock[DomainUserStore]

  val userGroupStore: UserGroupStore = mock[UserGroupStore]

  val sessionStore: SessionStore = mock[SessionStore]

  val jwtAuthKeyStore: JwtAuthKeyStore = mock[JwtAuthKeyStore]

  val modelOperationStore: ModelOperationStore = mock[ModelOperationStore]

  val modelSnapshotStore: ModelSnapshotStore = mock[ModelSnapshotStore]

  val modelStore: ModelStore = mock[ModelStore]

  val collectionStore: CollectionStore = mock[CollectionStore]

  val modelOperationProcessor: ModelOperationProcessor = mock[ModelOperationProcessor]

  val modelPermissionsStore: ModelPermissionsStore = mock[ModelPermissionsStore]

  val chatChannelStore: ChatChannelStore = mock[ChatChannelStore]

  val permissionsStore: PermissionsStore = mock[PermissionsStore]
  
  private[this] var validateConnectionResponse: Try[Unit] = Success(Unit)
  
  def setValidateConnectionResponse(result: Try[Unit]): Unit = {
    this.validateConnectionResponse = result;
  }
  
  def validateConnection(): Try[Unit] = {
    this.validateConnectionResponse
  }

  def shutdown(): Unit = {
  }
}
