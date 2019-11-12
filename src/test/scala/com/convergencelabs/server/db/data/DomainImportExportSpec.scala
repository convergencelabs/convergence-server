/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.db.data

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.db.schema.DeltaCategory
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.db.schema.TestingSchemaManager
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.domain.DomainPersistenceProviderImpl
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.convergencelabs.server.db.ConnectedSingleDatabaseProvider
import com.orientechnologies.orient.core.db.ODatabaseType

class DomainImportExportSpec extends WordSpecLike with Matchers {

  "A DomainImport and Export" must {
    "import the correct data" in {
      val dbName = "DomainImporterSpec-" + System.nanoTime()

      val orientDB = new OrientDB("memory:target/orientdb/DomainImportExportSpec", "root", "password", OrientDBConfig.defaultConfig());
      orientDB.create(dbName, ODatabaseType.MEMORY);
      val db = orientDB.open(dbName, "admin", "admin")
      db.activateOnCurrentThread()

      val dbPool = new ConnectedSingleDatabaseProvider(db)

      val upgrader = new TestingSchemaManager(db, DeltaCategory.Domain, true)
      upgrader.install()

      val provider = new DomainPersistenceProviderImpl(dbPool)
      provider.validateConnection().get

      val serializer = new DomainScriptSerializer()
      val in = getClass.getResourceAsStream("/com/convergencelabs/server/db/data/import-export-domain-test.yaml")
      val importScript = serializer.deserialize(in).success.value

      val importer = new DomainImporter(provider, importScript)

      importer.importDomain().get

      val exporter = new DomainExporter(provider)
      val exportedScript = exporter.exportDomain().get

      val DomainScript(importConfig, importJwtKeys, importUsers, importSessions, importCollections, importModels) = importScript
      val DomainScript(exportConfig, exportJwtKeys, exportUsers, exportSessions, exportCollections, exportModels) = exportedScript

      importConfig shouldBe exportConfig
      importJwtKeys.toSet shouldBe exportJwtKeys.toSet
      importCollections.toSet shouldBe exportCollections.toSet
      importModels.toSet shouldBe exportModels.toSet
      importUsers.value.toSet should be(exportUsers.value.toSet)
      importSessions.value.toSet should be(exportSessions.value.toSet)

      dbPool.shutdown()
      orientDB.drop(dbName)
      orientDB.close()
    }
  }
}