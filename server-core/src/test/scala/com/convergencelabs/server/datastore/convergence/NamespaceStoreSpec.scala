package com.convergencelabs.server.datastore.convergence

import java.time.Duration

import org.scalatest.Matchers
import org.scalatest.OptionValues.convertOptionToValuable
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.datastore.convergence.UserStore.User
import com.convergencelabs.server.datastore.domain.PersistenceStoreSpec
import com.convergencelabs.server.db.schema.DeltaCategory
import com.convergencelabs.server.domain.Domain
import com.convergencelabs.server.domain.DomainFqn
import com.convergencelabs.server.domain.DomainStatus
import com.convergencelabs.server.db.DatabaseProvider
import com.convergencelabs.server.datastore.DuplicateValueException
import com.convergencelabs.server.datastore.EntityNotFoundException
import com.convergencelabs.server.domain.DomainDatabase
import com.convergencelabs.server.domain.Namespace
import com.sun.jna.platform.unix.X11.Display

object NamespaceStoreSpec {
  case class SpecStores(namespace: NamespaceStore)
}

class NamespaceStoreSpec
  extends PersistenceStoreSpec[NamespaceStoreSpec.SpecStores](DeltaCategory.Convergence)
  with WordSpecLike
  with Matchers {

  def createStore(dbProvider: DatabaseProvider): NamespaceStoreSpec.SpecStores = {
    NamespaceStoreSpec.SpecStores(new NamespaceStore(dbProvider))
  }

  val Namespace1 = Namespace("namespace1", "Namespace 1")
  val Namespace2 = Namespace("namespace2", "Namespace 2")

  "A NamespaceStore" when {

    "asked whether a namespace exists" must {
      "return false if it doesn't exist" in withPersistenceStore { stores =>
        stores.namespace.namespaceExists(Namespace1.id).get shouldBe false
      }

      "return true if it does exist" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.namespaceExists(Namespace1.id).get shouldBe true
      }
    }

    "retrieving a namespace by id" must {
      "return None if the namespace doesn't exist" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.getNamespace("none").get shouldBe None
      }

      "return the correct namespace if it exists" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(Namespace1)
      }
    }

    "creating a namespace" must {
      "create the namespace correct record in the database" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(Namespace1)
      }

      "allow creating multiple namespaces with non-conclifcting id's" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace2).get
        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(Namespace1)
        stores.namespace.getNamespace(Namespace2.id).get shouldBe Some(Namespace2)
      }

      "return a DuplicateValueExcpetion if a namesspace exists with the same id" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace(Namespace1.id, "other display name")).failure.exception shouldBe a[DuplicateValueException]
      }

      "return a DuplicateValueExcpetion if a namesspace exists with the same display name" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace("Other Id", Namespace1.displayName)).failure.exception shouldBe a[DuplicateValueException]
      }
    }

    "removing a namespace" must {
      "remove the domain record in the database if it exists" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace2).get
        stores.namespace.deleteNamespace(Namespace1.id).get
        stores.namespace.namespaceExists(Namespace1.id).get shouldBe false
        stores.namespace.namespaceExists(Namespace2.id).get shouldBe true
      }

      //      "not throw an exception if the domain does not exist" in withPersistenceStore { stores =>
      //        stores.namespace.deleteNamespace("none").failure.exception shouldBe a[EntityNotFoundException]
      //      }
    }

    "updating a namespace" must {
      "sucessfully update an existing namespace" in withPersistenceStore { stores =>
        stores.namespace.createNamespace(Namespace1).get
        stores.namespace.createNamespace(Namespace2).get

        val updated = Namespace1.copy(displayName = "updated")
        stores.namespace.updateNamespace(updated).get

        stores.namespace.getNamespace(Namespace1.id).get shouldBe Some(updated)
        stores.namespace.getNamespace(Namespace2.id).get shouldBe Some(Namespace2)
      }

      "fail to update an non-existing namespace" in withPersistenceStore { stores =>
        val updated = Namespace1.copy(displayName = "updated")
        stores.namespace.updateNamespace(updated).failure.exception shouldBe a[EntityNotFoundException]
      }
    }
  }
}
