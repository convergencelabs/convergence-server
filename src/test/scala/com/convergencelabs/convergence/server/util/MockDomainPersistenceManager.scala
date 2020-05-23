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
import org.mockito.Mockito

import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceManager
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.domain.DomainId

import akka.actor.ActorContext
import akka.actor.ActorRef
import com.convergencelabs.convergence.server.datastore.domain.CollectionStore
import org.scalatestplus.mockito.MockitoSugar
import com.convergencelabs.convergence.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.convergence.server.datastore.domain.DomainConfigStore
import com.convergencelabs.convergence.server.datastore.domain.ChatStore
import com.convergencelabs.convergence.server.datastore.domain.DomainUserStore
import com.convergencelabs.convergence.server.datastore.domain.ModelOperationStore
import com.convergencelabs.convergence.server.datastore.domain.JwtAuthKeyStore
import com.convergencelabs.convergence.server.datastore.domain.SessionStore
import com.convergencelabs.convergence.server.datastore.domain.UserGroupStore
import com.convergencelabs.convergence.server.datastore.domain.ModelPermissionsStore
import com.convergencelabs.convergence.server.datastore.domain.ModelSnapshotStore
import com.convergencelabs.convergence.server.datastore.domain.ModelStore
import com.convergencelabs.convergence.server.datastore.domain.ModelOperationProcessor
import com.convergencelabs.convergence.server.datastore.domain.PermissionsStore
import com.convergencelabs.convergence.server.db.DatabaseProvider

class MockDomainPersistenceManager(val mockProviders: Map[DomainId, MockDomainPersistenceProvider]) extends DomainPersistenceManager {

  def acquirePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainId): Try[MockDomainPersistenceProvider] = {
    mockProviders.get(domainFqn) match {
      case Some(provider) => Success(provider)
      case None => Failure(new IllegalArgumentException(s"Don't have provider for domain ${domainFqn}"))
    }
  }

  def releasePersistenceProvider(requestor: ActorRef, context: ActorContext, domainFqn: DomainId): Unit = {

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

  val chatStore: ChatStore = mock[ChatStore]

  val permissionsStore: PermissionsStore = mock[PermissionsStore]
  
  private[this] var validateConnectionResponse: Try[Unit] = Success(())
  
  def setValidateConnectionResponse(result: Try[Unit]): Unit = {
    this.validateConnectionResponse = result;
  }
  
  def validateConnection(): Try[Unit] = {
    this.validateConnectionResponse
  }

  def shutdown(): Unit = {
  }
}
