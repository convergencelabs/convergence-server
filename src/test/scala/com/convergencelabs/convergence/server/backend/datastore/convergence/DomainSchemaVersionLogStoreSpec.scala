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

package com.convergencelabs.convergence.server.backend.datastore.convergence

import com.convergencelabs.convergence.server.model.DomainId
import com.convergencelabs.convergence.server.model.server.domain.{DomainDatabase, Namespace}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.{Instant, LocalDate, ZoneOffset}


class DomainSchemaVersionLogStoreSpec
  extends ConvergencePersistenceStoreSpec
    with AnyWordSpecLike
    with Matchers {

  private val namespace = Namespace("ns", "", userNamespace = false)

  private val domain1Id = DomainId(namespace.id, "1")
  private val domain2Id = DomainId(namespace.id, "2")

  private val database1 = DomainDatabase("1", "1.0", "", "", "", "")
  private val database2 = DomainDatabase("2", "1.0", "", "", "", "")

  private val version_1_0 = "1.0"
  private val version_1_1 = "1.1"
  private val version_1_2 = "1.2"

  private val domain1Version_1_0 = DomainSchemaVersionLogEntry(domain1Id, version_1_0, createDate("2020-10-21"))
  private val domain1Version_1_1 = DomainSchemaVersionLogEntry(domain1Id, version_1_1, createDate("2020-10-26"))

  private val domain2Version_1_1 = DomainSchemaVersionLogEntry(domain2Id, version_1_1, createDate("2020-10-01"))
  private val domain2Version_1_2 = DomainSchemaVersionLogEntry(domain2Id, version_1_2, createDate("2020-11-01"))

  "A DomainSchemaVersionLogStore" when {
    "creating versions for a domain" must {
      "store those versions" in withTestData { provider =>
        val logStore = provider.domainSchemaVersionLogStore
        logStore.createDomainSchemaVersionLogEntry(domain1Version_1_0).get
        logStore.createDomainSchemaVersionLogEntry(domain1Version_1_1).get

        val versions = logStore.getDomainSchemaVersionLog(domain1Id).get

        versions shouldBe List(domain1Version_1_0, domain1Version_1_1)
      }
    }

    "getting all domain current versions" must {
      "get the correct versions" in withTestData { provider =>
        val logStore = provider.domainSchemaVersionLogStore
        logStore.createDomainSchemaVersionLogEntry(domain1Version_1_0).get
        logStore.createDomainSchemaVersionLogEntry(domain1Version_1_1).get

        logStore.createDomainSchemaVersionLogEntry(domain2Version_1_1).get
        logStore.createDomainSchemaVersionLogEntry(domain2Version_1_2).get


        val versions = logStore.getDomainSchemaVersions().get

        versions shouldBe Map(
          domain1Id -> version_1_1,
          domain2Id -> version_1_2
        )
      }
    }

    "deleting the log for a domain" must {
      "remove the log that domain" in withTestData { provider =>
        val logStore = provider.domainSchemaVersionLogStore
        logStore.createDomainSchemaVersionLogEntry(domain1Version_1_0).get
        logStore.createDomainSchemaVersionLogEntry(domain1Version_1_1).get

        logStore.createDomainSchemaVersionLogEntry(domain2Version_1_1).get
        logStore.createDomainSchemaVersionLogEntry(domain2Version_1_2).get

        logStore.removeVersionLogForDomain(domain1Id).get

        logStore.getDomainSchemaVersionLog(domain1Id).get shouldBe List()
        logStore.getDomainSchemaVersionLog(domain2Id).get shouldBe List(domain2Version_1_1, domain2Version_1_2)
      }
    }
  }

  def withTestData(testCode: ConvergencePersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>

      provider.namespaceStore.createNamespace(namespace).get

      provider.domainStore.createDomain(domain1Id, "", database1).get
      provider.domainStore.createDomain(domain2Id, "", database2).get

      testCode(provider)
    }
  }

  private[this] def createDate(date: String): Instant = {
    val localDate = LocalDate.parse(date)
    val localDateTime = localDate.atStartOfDay
    localDateTime.toInstant(ZoneOffset.UTC)
  }
}
