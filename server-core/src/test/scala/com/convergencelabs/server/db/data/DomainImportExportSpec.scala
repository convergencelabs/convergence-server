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
import com.convergencelabs.server.datastore.DatabaseProvider

class DomainImportExportSpec extends WordSpecLike with Matchers {

  "A DomainImport and Export" must {
    "import the correct data" in {
      val url = "memory:DomainImporterSpec-" + System.nanoTime()

      val db = new ODatabaseDocumentTx(url)
      db.activateOnCurrentThread()
      db.create()

      val dbPool = DatabaseProvider(db)

      val upgrader = new TestingSchemaManager(db, DeltaCategory.Domain, true)
      upgrader.install()

      val provider = new DomainPersistenceProvider(dbPool)
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
    }
  }
}