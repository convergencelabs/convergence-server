package com.convergencelabs.server.db.data

import java.time.Instant

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.SortOrder
import com.convergencelabs.server.datastore.domain.CollectionPermissions
import com.convergencelabs.server.datastore.domain.DomainPersistenceProvider
import com.convergencelabs.server.datastore.domain.DomainUserField
import com.convergencelabs.server.datastore.domain.ModelPermissions
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.db.schema.TestingSchemaManager
import com.convergencelabs.server.domain.DomainUser
import com.convergencelabs.server.domain.DomainUserType
import com.convergencelabs.server.domain.JwtAuthKey
import com.convergencelabs.server.domain.JwtKeyPair
import com.convergencelabs.server.domain.model.Collection
import com.convergencelabs.server.domain.model.Model
import com.convergencelabs.server.domain.model.ModelMetaData
import com.convergencelabs.server.domain.model.ModelOperation
import com.convergencelabs.server.domain.model.ModelSnapshot
import com.convergencelabs.server.domain.model.ModelSnapshotMetaData
import com.convergencelabs.server.domain.model.data.ObjectValue
import com.convergencelabs.server.domain.model.data.StringValue
import com.convergencelabs.server.domain.model.ot.AppliedStringInsertOperation
import com.convergencelabs.server.datastore.domain.DomainPersistenceProviderImpl
import com.convergencelabs.server.db.ConnectedSingleDatabaseProvider
import com.orientechnologies.orient.core.db.OrientDBConfig
import com.orientechnologies.orient.core.db.OrientDB
import com.orientechnologies.orient.core.db.ODatabaseType

class DomainImporterSpec extends WordSpecLike with Matchers {

  "A DomainImporterSpec" when {
    "importing" must {
      "import the correct data" in {
        val dbName = "DomainImporterSpec-" + System.nanoTime()
        val orientDB = new OrientDB("memory:DomainImporterSpec", "root", "password", OrientDBConfig.defaultConfig());
        orientDB.create(dbName, ODatabaseType.MEMORY);
        val db = orientDB.open(dbName, "admin", "admin")
        db.activateOnCurrentThread()

        val dbPool = new ConnectedSingleDatabaseProvider(db)

        val upgrader = new TestingSchemaManager(db, DeltaCategory.Domain, true)
        upgrader.install()

        val provider = new DomainPersistenceProviderImpl(dbPool)
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
        collections(0) shouldBe Collection("collection1", "Collection 1", false, DomainImporter.DefaultSnapshotConfig, CollectionPermissions(true, true, true, true, true))

        val collectionId = "collection1"
        val modelId = "someId"

        val model = provider.modelStore.getModel(modelId).get.value
        model shouldBe Model(
          ModelMetaData(
            modelId,
            collectionId,
            2L,
            Instant.parse("2016-11-16T17:49:15.233Z"),
            Instant.parse("2016-11-16T17:49:15.233Z"),
            true,
            ModelPermissions(true, true, true, true),
            1),
          ObjectValue(
            "vid1",
            Map("myString" -> StringValue("vid2", "my string"))))

        val operations = provider.modelOperationStore.getOperationsAfterVersion(modelId, 0L).get
        operations.size shouldBe 2
        operations(0) shouldBe ModelOperation(
          modelId,
          1L,
          Instant.parse("2016-11-16T17:49:15.233Z"),
          "test1",
          "84hf",
          AppliedStringInsertOperation("vid2", false, 0, "!"))
        operations(1) shouldBe ModelOperation(
          modelId,
          2L,
          Instant.parse("2016-11-16T17:49:15.233Z"),
          "test1",
          "84hf",
          AppliedStringInsertOperation("vid2", false, 1, "@"))

        val snapshot = provider.modelSnapshotStore.getSnapshot(modelId, 1).get.value
        snapshot shouldBe ModelSnapshot(
          ModelSnapshotMetaData(
            modelId,
            1L,
            Instant.parse("2016-11-16T17:49:15.233Z")),
          ObjectValue(
            "vid1",
            Map("myString" -> StringValue("vid2", "my string"))))

        dbPool.shutdown()
        orientDB.drop(dbName)
        orientDB.close()
      }
    }
  }
}
