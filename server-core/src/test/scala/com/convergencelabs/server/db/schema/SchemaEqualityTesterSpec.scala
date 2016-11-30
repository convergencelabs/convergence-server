package com.convergencelabs.server.db.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import org.scalatest.BeforeAndAfterEach

class SchemaEqualityTesterSpec extends WordSpecLike with Matchers with BeforeAndAfterEach {

  var dbCounter = 1
  val dbName = getClass.getSimpleName

  var db1: ODatabaseDocumentTx = null
  var db2: ODatabaseDocumentTx = null

  var pool1: OPartitionedDatabasePool = null
  var pool2: OPartitionedDatabasePool = null

  var processor1: DatabaseSchemaProcessor = null
  var processor2: DatabaseSchemaProcessor = null

  override def beforeEach() {
    val uri1 = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    db1 = new ODatabaseDocumentTx(uri1)
    db1.activateOnCurrentThread()
    db1.create()

    val uri2 = s"memory:${dbName}${dbCounter}"
    dbCounter += 1
    db2 = new ODatabaseDocumentTx(uri2)
    db2.activateOnCurrentThread()
    db2.create()

    pool1 = new OPartitionedDatabasePool(uri1, "admin", "admin")
    pool2 = new OPartitionedDatabasePool(uri2, "admin", "admin")

    processor1 = new DatabaseSchemaProcessor(pool1)
    processor2 = new DatabaseSchemaProcessor(pool2)
  }

  override def afterEach() {
    pool1.close()
    pool1 = null

    pool2.close()
    pool2 = null

    db1.activateOnCurrentThread()
    db1.drop()
    db1 = null

    db2.activateOnCurrentThread()
    db2.drop()
    db2 = null
  }

  "SchemaEqualityTester" when {
    "comparing functions" must {
      "return true if functions are the same" in {

        val delta = Delta(1, "Description",
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        processor1.applyDelta(delta)
        processor2.applyDelta(delta)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }

      "return false if function code is different" in {
        val delta1 = Delta(1, "Description",
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, "Description",
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nval fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "anotherIndex"), None, None)))

        processor1.applyDelta(delta1)
        processor2.applyDelta(delta2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if function name is different" in {
        val delta1 = Delta(1, "Description",
          List(CreateFunction("MyFunction1",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, "Description",
          List(CreateFunction("MyFunction2",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        processor1.applyDelta(delta1)
        processor2.applyDelta(delta2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if function parameters are different" in {
        val delta1 = Delta(1, "Description",
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, "Description",
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex"), None, None)))

        processor1.applyDelta(delta1)
        processor2.applyDelta(delta2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }
    }
  }
}
