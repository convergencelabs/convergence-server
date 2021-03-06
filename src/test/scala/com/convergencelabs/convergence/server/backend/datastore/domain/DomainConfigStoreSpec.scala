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

package com.convergencelabs.convergence.server.backend.datastore.domain

import com.convergencelabs.convergence.server.model.domain
import com.convergencelabs.convergence.server.model.domain.CollectionConfig
import com.convergencelabs.convergence.server.model.domain.jwt.JwtKeyPair
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

import java.time.Duration
import java.time.temporal.ChronoUnit

// scalastyle:off line.size.limit
class DomainConfigStoreSpec
  extends DomainPersistenceStoreSpec
    with AnyWordSpecLike
    with Matchers {

  private val initialCollectionConfig = domain.CollectionConfig(true)

  private val initialSnapshotConfig = domain.ModelSnapshotConfig(
    snapshotsEnabled = false,
    triggerByVersion = false,
    limitedByVersion = false,
    0L,
    0L,
    triggerByTime = true,
    limitedByTime = true,
    Duration.of(60, ChronoUnit.SECONDS), // scalastyle:ignore magic.number
    Duration.of(360, ChronoUnit.SECONDS)) // scalastyle:ignore magic.number

  private val initialAdminKeyPair = JwtKeyPair(
    "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiqTif8iluSxzziE9aeNB\nxJoV3sg4iJZ/qAV8pZoVObpB10rm2C2vV8+IZXF+x9Pulk/8C8CljW8RtsaNbN4P\nqauNOm6Zk8faCbRHlt8PuN06TDRRX2FiWzeEQVFq/vnMIJwiWpYpIBB/TYYa5pnP\n4hmAdOR4XkJoSj6cACDHzEf3JTFq7HOdbwLg1GR9VTmTJvLBhKgTNINhyczN/R5Y\ndrFg/A+FqaA0gWEsvA2ig7bzwRMNBwFyPrb1//S/Qc47ndChVxK1lQchkFZKRsIz\n5ZjL5/YmfmWNpaYpQl6yH7vMIMB28arQw6FMBjqEfU2D4f0mrsKtu36xZJ8bWDN0\n8QIDAQAB\n-----END PUBLIC KEY-----\n",
    "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEAiqTif8iluSxzziE9aeNBxJoV3sg4iJZ/qAV8pZoVObpB10rm\n2C2vV8+IZXF+x9Pulk/8C8CljW8RtsaNbN4PqauNOm6Zk8faCbRHlt8PuN06TDRR\nX2FiWzeEQVFq/vnMIJwiWpYpIBB/TYYa5pnP4hmAdOR4XkJoSj6cACDHzEf3JTFq\n7HOdbwLg1GR9VTmTJvLBhKgTNINhyczN/R5YdrFg/A+FqaA0gWEsvA2ig7bzwRMN\nBwFyPrb1//S/Qc47ndChVxK1lQchkFZKRsIz5ZjL5/YmfmWNpaYpQl6yH7vMIMB2\n8arQw6FMBjqEfU2D4f0mrsKtu36xZJ8bWDN08QIDAQABAoIBAAznk/1425QEwzKo\nfWLhuDlobiiyUjDEcVVeTV/Mm7Un1QEJA7uTZQKzWmq2yaon28k54KfnYoFrmSZ+\nDKPZd3O/eAG8UacjrvPAR/mPAZOfY9G7/Ob31YPQAwthXKgm8A0I+1mbKHDHmFHK\n7a4RGvxci0xWB64eWD6w3SxV4MLuvLdDGB5mHF9DDnm0Bx9qA47WGeCdYuJMkN0p\nBwTyp72LIkoZRHazqJIHyQ3x3AoBq5K8DQ9GrKjL0odGursrJ8WjqdV8WdTCyDPb\nhihlF9hddNv8v2OLnYaHajk4OvRPVXTANo68FXjUryiynomZIvC08sEde+Ds5Iq0\nZa4IM9ECgYEAyah3rItZdf7+C5Lpflq/5M1/shwzit2fQ/yQMxKb76uQGfI6VXKk\nLZKTehA8leK2P1p73hRA+ReUGo/NlkMpcMZ7zhWTLqBeZISfZw35VUukM1ANfXK9\nBs7r0oYgHs6ulf/p7s7ErTf29MgY4FafTiPVgwYRKzqT7ZmIerpUnVcCgYEAsAFZ\nlK9nQec60FsPMIXtN8xet9WZ0gwLbs9W8/i1PqZp+MKB0V7Dp1g10KWmqcKBOdbp\nHK3E1y6AjmyTLvVU7mVfQVRgKhPbZPjBtuhoKsCttDstg0NDwq2PgCwcJ7CkDSor\nKq1youttkpw7MHr23+JrUCQ8ILPqtAfRSAw/yvcCgYAWu6MiFGN1ZdWFwH4J/Hj9\ndh0bGwrEcM9vfp1S5JonnpOUGTZyQ4Y2jPuLGyF5VCFvaufj1Syt5/aON//ZHKEj\nUXzLcqsw6ms5sam2mGCvOOO91RxwM+sTRWhYRz5/upT72+mnPi/1xwVT+uqy/5Dd\n4jRDnP96fBQJCPHVxAOd6QKBgQCda8esxinL9z5Sh2+Rjef7hU6ejG01QtKi/M4g\nDq5FZ+DWv1oPYvwKXEpd4RutMKwWiJMdtIqfkBcpzBDk6kdZps/JBeexGuubZycU\nKtColIeI8Xkms24S3NvB3zIbidFhePr9A//Jmlr5y8Tg+sp+uuwS1SX2dhWRioB0\nOYiBRwKBgQC4AhlBquYd0GMHIAoT+05UsMSoWJarSG0VxFli+j3P3elEPdyh27RL\nOtg1kEbzrPtPQw0GqDlgjN3JfmdEPQYtwqXzmhyhGgpzQ0XL9qNLR3fr3jL0c/B2\nCRtfM37osDmuRQVjkjtc01XMtQCLEVafjKZK2Ikn6SMEunpDJR2epw==\n-----END RSA PRIVATE KEY-----\n")

  "A DomainConfigStore" when {

    "uninitialized" must {
      "return false for isInitialized" in withPersistenceStore { store =>
        store.configStore.isInitialized().get shouldBe false
      }
    }

    "initialized" must {
      "return true for isInitialized" in withTestData { store =>
        store.configStore.isInitialized().get shouldBe true
      }

      "have the correct reconnect token validity" in withTestData { store =>
        store.configStore.getReconnectTokenTimeoutMinutes().get shouldBe 60L
      }

      "have the correct snapshot config" in withTestData { store =>
        store.configStore.getModelSnapshotConfig().get shouldBe initialSnapshotConfig
      }

      "have the correct admin key" in withTestData { store =>
        store.configStore.getAdminKeyPair().get shouldBe initialAdminKeyPair
      }

      "have anonymous auth disabled" in withTestData { store =>
        store.configStore.isAnonymousAuthEnabled().get shouldBe false
      }

      "have the correct collection config" in withTestData { store =>
        store.configStore.getCollectionConfig().get shouldBe CollectionConfig(true)
      }
    }

    "getting and setting the admin key pair" must {
      "set and return the correct key by id" in withTestData { store =>
        val setPair = JwtKeyPair("pub", "priv")
        store.configStore.setAdminKeyPair(setPair).get
        store.configStore.getAdminKeyPair().get shouldBe setPair
      }
    }

    "setting the model snapshot config" must {
      "get and set the correct object" in withTestData { store =>
        val configToSet = domain.ModelSnapshotConfig(
          snapshotsEnabled = true,
          triggerByVersion = true,
          limitedByVersion = true,
          1L,
          1L,
          triggerByTime = false,
          limitedByTime = false,
          Duration.of(1, ChronoUnit.SECONDS), // scalastyle:ignore magic.number
          Duration.of(1, ChronoUnit.SECONDS)) // scalastyle:ignore magic.number

        store.configStore.setModelSnapshotConfig(configToSet).success
        store.configStore.getModelSnapshotConfig().success.value shouldBe configToSet
      }
    }

    "setting the anonymousAuthEnabled config" must {
      "get and set the correct value" in withTestData { store =>
        store.configStore.isAnonymousAuthEnabled().success.value shouldBe false
        store.configStore.setAnonymousAuthEnabled(enabled = true).success
        store.configStore.isAnonymousAuthEnabled().success.value shouldBe true
      }
    }

    "getting and setting the collection config" must {
      "set and return the correct config" in withTestData { store =>
        val collectionConfig = CollectionConfig(false)
        store.configStore.setCollectionConfig(collectionConfig).get
        store.configStore.getCollectionConfig().get shouldBe collectionConfig
      }
    }

    "getting and setting the reconnect token validity " must {
      "set and return the correct config" in withTestData { store =>
        store.configStore.setReconnectTokenTimeoutMinutes(40L).get
        store.configStore.getReconnectTokenTimeoutMinutes().get shouldBe 40L
      }
    }
  }

  def withTestData(testCode: DomainPersistenceProvider => Any): Unit = {
    this.withPersistenceStore { provider =>
      provider.configStore.initializeDomainConfig(
        initialAdminKeyPair,
        initialCollectionConfig,
        initialSnapshotConfig,
        anonymousAuthEnabled = false,
        60L)
      testCode(provider)
    }
  }
}
