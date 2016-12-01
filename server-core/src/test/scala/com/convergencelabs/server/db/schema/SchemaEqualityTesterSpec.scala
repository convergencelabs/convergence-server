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

  var processor1: DatabaseDeltaProcessor = null
  var processor2: DatabaseDeltaProcessor = null

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
  }

  override def afterEach() {
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

        val delta = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }

      "return false if function code is different" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nval from = parseInt(fromIndex);\narray.add(toIn, array.remove(from));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if function name is different" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction1",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction2",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if function parameters are different" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex"), None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if one function has a different language" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), Some("javascript"), None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if one function is idempotent and the other is not" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, Some(true))))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }
    }

    "comparing sequences" must {
      "return true if sequences are the same" in {

        val delta = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }

      "return false if sequences have different types" in {

        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Cached, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if sequences have different names" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence2", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if sequences have different starts" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, Some(5), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if sequences have different increments" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, Some(5), None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }
    }

    "comparing classes" must {
      "return true if classes are the same" in {
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.String, None, None, None)))))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }

      "return false if class names are different" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List())))

        val delta2 = Delta(1, Some("Description"),
          List(CreateClass("MyClass2", None, None, List())))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }

      "return false if class superclass is different" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List())))

        val delta2 = Delta(1, Some("Description"),
          List(CreateClass("MySuperClass", None, None, List())))

        val delta3 = Delta(2, Some("Description"),
          List(CreateClass("MyClass", Some("MySuperClass"), None, List())))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        DatabaseDeltaProcessor.apply(delta2, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)
        DatabaseDeltaProcessor.apply(delta3, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe false
      }
    }

    "return false if one class is abstract" in {
      val delta1 = Delta(1, Some("Description"),
        List(CreateClass("MyClass", None, None, List())))

      val delta2 = Delta(1, Some("Description"),
        List(CreateClass("MyClass", None, Some(true), List())))

      db1.activateOnCurrentThread()
      DatabaseDeltaProcessor.apply(delta1, db1)
      db2.activateOnCurrentThread()
      DatabaseDeltaProcessor.apply(delta2, db2)

      SchemaEqualityTester.isEqual(db1, db2) shouldBe false
    }

    "comparing indexes" must {
      "return true if indexes are the same" in {

        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.isEqual(db1, db2) shouldBe true
      }
    }
  }
}
