package com.convergencelabs.server.db.data

import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainUserField
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.JwtKeyPair
import com.convergencelabs.server.domain.JwtAuthKey
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
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.convergencelabs.server.db.schema.DomainSchemaManager
import com.convergencelabs.server.db.schema.TestingSchemaManager
import com.convergencelabs.server.datastore.DatabaseProvider

class DomainImporterSpec extends WordSpecLike with Matchers {

  "A DomainImporterSpec" when {
    "importing" must {
      "import the correct data" in {
        val url = "memory:DomainImporterSpec-" + System.nanoTime()
        
        val db = new ODatabaseDocumentTx(url)
        db.activateOnCurrentThread()
        db.create()

        val dbPool = DatabaseProvider(db)

        val upgrader = new TestingSchemaManager(db, DeltaCategory.Domain, true)
        upgrader.install()

        val provider = new DomainPersistenceProvider(dbPool)
        provider.validateConnection().success
        
        val serializer = new DomainScriptSerializer()
        val in = getClass.getResourceAsStream("/com/convergencelabs/server/db/data/import-domain-test.yaml")
        val script = serializer.deserialize(in).get

        val importer = new DomainImporter(provider, script)

        importer.importDomain().get

        provider.configStore.isAnonymousAuthEnabled().get shouldBe true
        provider.configStore.getAdminKeyPair().get shouldBe JwtKeyPair("Public Key", "Private Key")

        val keys = provider.jwtAuthKeyStore.getKeys(None, None).get
        keys.size shouldBe 1
        keys(0) shouldBe JwtAuthKey(
          "test-key",
          "a test key",
          Instant.parse("2016-11-16T17:49:15.233Z"),
          "Public Key",
          true)

        val users = provider.userStore.getAllDomainUsers(
          Some(DomainUserField.Username),
          Some(SortOrder.Ascending),
          None, None).get
        users.size shouldBe 2

        users(0) shouldBe DomainUser(DomainUserType.Normal, "test1", Some("Test"), Some("One"), Some("Test One"), Some("test1@example.com"))
        users(1) shouldBe DomainUser(DomainUserType.Normal, "test2", Some("Test"), Some("Two"), Some("Test Two"), Some("test2@example.com"))

        provider.userStore.validateCredentials("test1", "somePassword").get shouldBe true
        provider.userStore.getDomainUserPasswordHash("test2").get.value shouldBe "someHash"
        
        val collections = provider.collectionStore.getAllCollections(None, None).get
        collections.size shouldBe 1
        collections(0) shouldBe Collection("collection1", "Collection 1", false, DomainImporter.DefaultSnapshotConfig)

        val modelFqn = ModelFqn("collection1", "someId")

        val model = provider.modelStore.getModel(modelFqn).get.value
        model shouldBe Model(
          ModelMetaData(
            modelFqn,
            2L,
            Instant.parse("2016-11-16T17:49:15.233Z"),
            Instant.parse("2016-11-16T17:49:15.233Z")),
          ObjectValue(
            "vid1",
            Map("myString" -> StringValue("vid2", "my string"))))

        val operations = provider.modelOperationStore.getOperationsAfterVersion(modelFqn, 0L).get
        operations.size shouldBe 2
        operations(0) shouldBe ModelOperation(
          modelFqn,
          1L,
          Instant.parse("2016-11-16T17:49:15.233Z"),
          "test1",
          "84hf",
          AppliedStringInsertOperation("vid2", false, 0, "!"))
        operations(1) shouldBe ModelOperation(
          modelFqn,
          2L,
          Instant.parse("2016-11-16T17:49:15.233Z"),
          "test1",
          "84hf",
          AppliedStringInsertOperation("vid2", false, 1, "@"))

        val snapshot = provider.modelSnapshotStore.getSnapshot(modelFqn, 1).get.value
        snapshot shouldBe ModelSnapshot(
          ModelSnapshotMetaData(
            modelFqn,
            1L,
            Instant.parse("2016-11-16T17:49:15.233Z")),
          ObjectValue(
            "vid1",
            Map("myString" -> StringValue("vid2", "my string"))))
        
        db.activateOnCurrentThread()
        db.drop()
      }
    }
  }
}
