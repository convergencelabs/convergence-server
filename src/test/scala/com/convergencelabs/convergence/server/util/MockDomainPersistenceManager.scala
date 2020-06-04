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
import akka.actor.typed.{ActorRef, ActorSystem}
import com.convergencelabs.convergence.server.datastore.domain._
import com.convergencelabs.convergence.server.db.DatabaseProvider
import com.convergencelabs.convergence.server.domain.DomainId
import org.scalatestplus.mockito.MockitoSugar

import scala.util.{Failure, Success, Try}

class MockDomainPersistenceManager(val mockProviders: Map[DomainId, MockDomainPersistenceProvider]) extends DomainPersistenceManager {

  override def acquirePersistenceProvider(consumer: ActorRef[_], system: ActorSystem[_], domainId: DomainId): Try[MockDomainPersistenceProvider] = {
    mockProviders.get(domainId) match {
      case Some(provider) => Success(provider)
      case None => Failure(new IllegalArgumentException(s"Don't have provider for domain: $domainId"))
    }
  }

  override def releasePersistenceProvider(consumer: ActorRef[_], system: ActorSystem[_], domainId: DomainId): Unit = {

  }
}

class MockDomainPersistenceProvider(override val domainId: DomainId)
  extends DomainPersistenceProvider with MockitoSugar {

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
    this.validateConnectionResponse = result
  }
  
  def validateConnection(): Try[Unit] = {
    this.validateConnectionResponse
  }

  def shutdown(): Unit = {
  }
}
