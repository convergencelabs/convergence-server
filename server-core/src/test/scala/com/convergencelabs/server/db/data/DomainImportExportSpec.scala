package com.convergencelabs.server.db.data

import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues._
import org.scalatest.WordSpecLike

import com.convergencelabs.db.deltas.DeltaCategory
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainUserField
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.JwtKeyPair
import com.convergencelabs.server.domain.JwtPublicKey
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelFqn
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.schema.DatabaseSchemaManager
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import scala.util.Success

class DomainImportExportSpec extends WordSpecLike with Matchers {

  "A DomainImport and Export" must {
    "import the correct data" in {
      val url = "memory:DomainImporterSpec-" + System.nanoTime()

      val db = new ODatabaseDocumentTx(url)
      db.activateOnCurrentThread()
      db.create()

      val dbPool = new OPartitionedDatabasePool(url, "admin", "admin")

      val upgrader = new DatabaseSchemaManager(dbPool, DeltaCategory.Domain)
      upgrader.upgradeToLatest()

      val provider = new DomainPersistenceProvider(dbPool)
      provider.validateConnection().success

      val serializer = new DomainScriptSerializer()
      val in = getClass.getResourceAsStream("/com/convergencelabs/server/db/data/import-domain-test.yaml")
      val importScript = serializer.deserialize(in).success.value

      val importer = new DomainImporter(provider, importScript)

      importer.importDomain().success

      val exporter = new DomainExporter(provider)
      val exportedScript = exporter.exportDomain().success.value

      val DomainScript(importConfig, importJwtKeys, importUsers, importCollections, importModels) = importScript
      val DomainScript(exportConfig, exportJwtKeys, exportUsers, exportCollections, exportModels) = exportedScript

      importConfig shouldBe exportConfig
      importJwtKeys.toSet shouldBe exportJwtKeys.toSet
      importCollections.toSet shouldBe exportCollections.toSet
      importModels.toSet shouldBe exportModels.toSet
      importUsers.value.toSet should be(exportUsers.value.toSet)
    }
  }
}