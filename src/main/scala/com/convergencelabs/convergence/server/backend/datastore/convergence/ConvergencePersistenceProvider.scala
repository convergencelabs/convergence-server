/*
 * Copyright (c) 2021 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.backend.datastore.convergence

import com.convergencelabs.convergence.server.backend.datastore.AbstractPersistenceProvider
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider

import scala.util.Try

trait ConvergencePersistenceProvider {
  
  val dbProvider: DatabaseProvider

  val configStore: ConfigStore

  val convergenceSchemaVersionLogStore: ConvergenceSchemaVersionLogStore
  
  val convergenceSchemaDeltaLogStore: ConvergenceSchemaDeltaLogStore

  val domainSchemaVersionLogStore: DomainSchemaVersionLogStore

  val domainSchemaDeltaLogStore: DomainSchemaDeltaLogStore
  
  val domainStore: DomainStore
  
  val namespaceStore: NamespaceStore
  
  val roleStore: RoleStore
  
  val userApiKeyStore: UserApiKeyStore
  
  val userFavoriteDomainStore: UserFavoriteDomainStore
  
  val userSessionTokenStore: UserSessionTokenStore
  
  val userStore: UserStore

  def validateConnection(): Try[Unit]

  def shutdown(): Unit
}

class ConvergencePersistenceProviderImpl(val dbProvider: DatabaseProvider)
    extends AbstractPersistenceProvider(dbProvider)
    with ConvergencePersistenceProvider {
  
  val configStore = new ConfigStore(dbProvider)

  val convergenceSchemaVersionLogStore = new ConvergenceSchemaVersionLogStore(dbProvider)

  val convergenceSchemaDeltaLogStore = new ConvergenceSchemaDeltaLogStore(dbProvider)

  val domainSchemaVersionLogStore = new DomainSchemaVersionLogStore(dbProvider)

  val domainSchemaDeltaLogStore = new DomainSchemaDeltaLogStore(dbProvider)

  val domainStore = new DomainStore(dbProvider)

  val namespaceStore = new NamespaceStore(dbProvider)

  val roleStore = new RoleStore(dbProvider)

  val userApiKeyStore = new UserApiKeyStore(dbProvider)

  val userFavoriteDomainStore = new UserFavoriteDomainStore(dbProvider)

  val userSessionTokenStore = new UserSessionTokenStore(dbProvider)

  val userStore = new UserStore(dbProvider)
}
