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

package com.convergencelabs.convergence.server.backend.db.schema

import com.convergencelabs.convergence.server.backend.datastore.convergence.{DomainSchemaDeltaLogStore, DomainSchemaVersionLogStore}
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.model.DomainId

object DomainSchemaManager {
  val BasePath = "/com/convergencelabs/convergence/server/db/schema/domain"
}

class DomainSchemaManager(domainId: DomainId, convergenceDatabaseProvider: DatabaseProvider, domainDatabaseProvider: DatabaseProvider) extends SchemaManager(
  new SchemaMetaDataRepository(DomainSchemaManager.BasePath),
  new DomainSchemaStatePersistence(
    domainId,
    new DomainSchemaDeltaLogStore(convergenceDatabaseProvider),
    new DomainSchemaVersionLogStore(convergenceDatabaseProvider)
  ),
  new OrientDBDeltaApplicator(domainDatabaseProvider)) {
}
