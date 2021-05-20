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
package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.backend.datastore.PersistenceStoreSpec
import com.convergencelabs.convergence.server.backend.db.DatabaseProvider
import com.convergencelabs.convergence.server.backend.db.schema.NonRecordingSchemaManager
import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.DomainStatus
import org.mockito.Mockito
import org.scalatestplus.mockito.MockitoSugar

import scala.util.Success

class DomainPersistenceStoreSpec
  extends PersistenceStoreSpec[DomainPersistenceProvider](NonRecordingSchemaManager.SchemaType.Domain)
    with MockitoSugar {

  protected val domainId: DomainId = DomainId("ns", "domain")

  protected val domainStatusProvider: DomainStatusProvider = mock[DomainStatusProvider]
  Mockito.when(domainStatusProvider.getDomainStatus).thenReturn(Success(Some(DomainStatus.Online)))

  protected def createStore(dbProvider: DatabaseProvider): DomainPersistenceProvider =
    new DomainPersistenceProviderImpl(domainId, dbProvider, domainStatusProvider)
}
