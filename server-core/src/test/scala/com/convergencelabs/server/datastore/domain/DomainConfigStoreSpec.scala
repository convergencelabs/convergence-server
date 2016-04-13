package com.convergencelabs.server.datastore.domain

import java.text.SimpleDateFormat
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit

import org.scalatest.Finders
import org.scalatest.Matchers
import org.scalatest.TryValues.convertTryToSuccessOrFailure
import org.scalatest.WordSpecLike

import com.convergencelabs.server.domain.ModelSnapshotConfig
import com.convergencelabs.server.domain.TokenKeyPair
import com.convergencelabs.server.domain.TokenPublicKey
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool

// scalastyle:off line.size.limit
class DomainConfigStoreSpec
    extends PersistenceStoreSpec[DomainConfigStore]("/dbfiles/n1-d1.json.gz")
    with WordSpecLike
    with Matchers {

  val df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSz")

  val adminKeyPair = TokenKeyPair(
    "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAiqTif8iluSxzziE9aeNB\nxJoV3sg4iJZ/qAV8pZoVObpB10rm2C2vV8+IZXF+x9Pulk/8C8CljW8RtsaNbN4P\nqauNOm6Zk8faCbRHlt8PuN06TDRRX2FiWzeEQVFq/vnMIJwiWpYpIBB/TYYa5pnP\n4hmAdOR4XkJoSj6cACDHzEf3JTFq7HOdbwLg1GR9VTmTJvLBhKgTNINhyczN/R5Y\ndrFg/A+FqaA0gWEsvA2ig7bzwRMNBwFyPrb1//S/Qc47ndChVxK1lQchkFZKRsIz\n5ZjL5/YmfmWNpaYpQl6yH7vMIMB28arQw6FMBjqEfU2D4f0mrsKtu36xZJ8bWDN0\n8QIDAQAB\n-----END PUBLIC KEY-----\n",
    "-----BEGIN RSA PRIVATE KEY-----\nMIIEpAIBAAKCAQEAiqTif8iluSxzziE9aeNBxJoV3sg4iJZ/qAV8pZoVObpB10rm\n2C2vV8+IZXF+x9Pulk/8C8CljW8RtsaNbN4PqauNOm6Zk8faCbRHlt8PuN06TDRR\nX2FiWzeEQVFq/vnMIJwiWpYpIBB/TYYa5pnP4hmAdOR4XkJoSj6cACDHzEf3JTFq\n7HOdbwLg1GR9VTmTJvLBhKgTNINhyczN/R5YdrFg/A+FqaA0gWEsvA2ig7bzwRMN\nBwFyPrb1//S/Qc47ndChVxK1lQchkFZKRsIz5ZjL5/YmfmWNpaYpQl6yH7vMIMB2\n8arQw6FMBjqEfU2D4f0mrsKtu36xZJ8bWDN08QIDAQABAoIBAAznk/1425QEwzKo\nfWLhuDlobiiyUjDEcVVeTV/Mm7Un1QEJA7uTZQKzWmq2yaon28k54KfnYoFrmSZ+\nDKPZd3O/eAG8UacjrvPAR/mPAZOfY9G7/Ob31YPQAwthXKgm8A0I+1mbKHDHmFHK\n7a4RGvxci0xWB64eWD6w3SxV4MLuvLdDGB5mHF9DDnm0Bx9qA47WGeCdYuJMkN0p\nBwTyp72LIkoZRHazqJIHyQ3x3AoBq5K8DQ9GrKjL0odGursrJ8WjqdV8WdTCyDPb\nhihlF9hddNv8v2OLnYaHajk4OvRPVXTANo68FXjUryiynomZIvC08sEde+Ds5Iq0\nZa4IM9ECgYEAyah3rItZdf7+C5Lpflq/5M1/shwzit2fQ/yQMxKb76uQGfI6VXKk\nLZKTehA8leK2P1p73hRA+ReUGo/NlkMpcMZ7zhWTLqBeZISfZw35VUukM1ANfXK9\nBs7r0oYgHs6ulf/p7s7ErTf29MgY4FafTiPVgwYRKzqT7ZmIerpUnVcCgYEAsAFZ\nlK9nQec60FsPMIXtN8xet9WZ0gwLbs9W8/i1PqZp+MKB0V7Dp1g10KWmqcKBOdbp\nHK3E1y6AjmyTLvVU7mVfQVRgKhPbZPjBtuhoKsCttDstg0NDwq2PgCwcJ7CkDSor\nKq1youttkpw7MHr23+JrUCQ8ILPqtAfRSAw/yvcCgYAWu6MiFGN1ZdWFwH4J/Hj9\ndh0bGwrEcM9vfp1S5JonnpOUGTZyQ4Y2jPuLGyF5VCFvaufj1Syt5/aON//ZHKEj\nUXzLcqsw6ms5sam2mGCvOOO91RxwM+sTRWhYRz5/upT72+mnPi/1xwVT+uqy/5Dd\n4jRDnP96fBQJCPHVxAOd6QKBgQCda8esxinL9z5Sh2+Rjef7hU6ejG01QtKi/M4g\nDq5FZ+DWv1oPYvwKXEpd4RutMKwWiJMdtIqfkBcpzBDk6kdZps/JBeexGuubZycU\nKtColIeI8Xkms24S3NvB3zIbidFhePr9A//Jmlr5y8Tg+sp+uuwS1SX2dhWRioB0\nOYiBRwKBgQC4AhlBquYd0GMHIAoT+05UsMSoWJarSG0VxFli+j3P3elEPdyh27RL\nOtg1kEbzrPtPQw0GqDlgjN3JfmdEPQYtwqXzmhyhGgpzQ0XL9qNLR3fr3jL0c/B2\nCRtfM37osDmuRQVjkjtc01XMtQCLEVafjKZK2Ikn6SMEunpDJR2epw==\n-----END RSA PRIVATE KEY-----\n")

  def createStore(dbPool: OPartitionedDatabasePool): DomainConfigStore = new DomainConfigStore(dbPool)

  "A DomainConfigStore" when {
    "getting the admin key pair" must {
      "return the correct key by id" in withPersistenceStore { store =>
        store.getAdminKeyPair().success.value shouldBe adminKeyPair
      }
    }

    "getting the model snapshot config" must {
      "return the correct object" in withPersistenceStore { store =>
        store.getModelSnapshotConfig().success.value shouldBe
          ModelSnapshotConfig(
            true,
            true,
            true,
            250L, // scalastyle:ignore magic.number
            500L, // scalastyle:ignore magic.number
            false,
            false,
            Duration.of(0, ChronoUnit.SECONDS),
            Duration.of(0, ChronoUnit.SECONDS))
      }
    }

    "setting the model snapshot config" must {
      "get and set the correct object" in withPersistenceStore { store =>
        val configToSet = ModelSnapshotConfig(
          false,
          false,
          false,
          0L,
          0L,
          true,
          true,
          Duration.of(60, ChronoUnit.SECONDS), // scalastyle:ignore magic.number
          Duration.of(360, ChronoUnit.SECONDS)) // scalastyle:ignore magic.number

        store.setModelSnapshotConfig(configToSet).success
        store.getModelSnapshotConfig().success.value shouldBe configToSet
      }
    }
  }
}
