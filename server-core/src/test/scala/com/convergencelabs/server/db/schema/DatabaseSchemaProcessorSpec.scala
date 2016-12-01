package com.convergencelabs.server.db.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import com.orientechnologies.orient.core.metadata.function.OFunction
import com.orientechnologies.orient.core.db.OPartitionedDatabasePool
import com.orientechnologies.orient.core.db.ODatabaseRecordThreadLocal

class DatabaseSchemaProcessorSpec extends WordSpecLike with Matchers {
  OLogManager.instance().setConsoleLevel("WARNING")

  "An DatabaseSchemaProcessor" when {
    "Processing a CreateClass change" must {
      "Corrrectly create class" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List())))
          
        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.close()
      }

      "Correctly create class and its properties" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.String, None, None, None)))))

        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.close()
      }

      "Correctly create class with superclass" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MySuperclass", None, None, List()),
            CreateClass("MyClass", Some("MySuperclass"), None, List())))

        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.existsClass("MySuperclass") shouldBe true
        db.close()
      }
    }

    "Processing an AlterClass change" must {
      "Correctly alter class name" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List()),
            AlterClass("MyClass", Some("NewName"), None)))
        val db = dbPool.acquire()

        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe false
        db.getMetadata.getSchema.existsClass("NewName") shouldBe true
        db.close()
      }

      "Correctly alter superclass" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List()),
            CreateClass("MySuperclass", None, None, List()),
            AlterClass("MyClass", None, Some("MySuperclass"))))
        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").hasSuperClasses() shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").getSuperClassesNames.get(0) shouldBe "MySuperclass"
        db.close()
      }
    }

    "Processing a DropClass change" must {
      "Correctly drops class" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List()),
            DropClass("MyClass")))
        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe false
        db.close()
      }
    }

    "Processing a AddProperty change" must {
      "Correctly adds new property to class" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List()),
            AddProperty("MyClass", Property("prop1", OrientType.String, None, None, None))))
        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.close()
      }
    }

    "Processing a AlterProperty change" must {
      "Correctly alters property class" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            AlterProperty("MyClass", "prop1", PropertyOptions(None, Some(OrientType.Integer), None, None, None))))
        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").getProperty("prop1").getType shouldEqual (OType.INTEGER)
        db.close()
      }

      "Correctly alters property name" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            AlterProperty("MyClass", "prop1", PropertyOptions(Some("prop2"), None, None, None, None))))
        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop2") shouldBe true
        val func: OFunction = db.getMetadata.getFunctionLibrary.createFunction("")
        db.close()

      }
    }

    "Processing a DropProperty change" must {
      "Correctly drops property from class" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            DropProperty("MyClass", "prop1")))
        val db = dbPool.acquire()

        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe false
        db.close()
      }
    }

    "Processing a CreateIndex change" must {
      "Correctly creates unique index for class" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None)))

        val db = dbPool.acquire()

        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getIndexManager.existsIndex("MyClass.prop1") shouldBe true
        val index = db.getMetadata.getIndexManager.getIndex("MyClass.prop1")
        index.getDefinition.getFields.get(0) shouldBe "prop1"
        db.close()
      }
    }

    "Processing a DropIndex change" must {
      "Correctly drops index" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None),
            DropIndex("MyClass.prop1")))
        val db = dbPool.acquire()

        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getIndexManager.existsIndex("MyClass.prop1") shouldBe false
        db.close()
      }
    }

    "Processing a CreateSequence change" must {
      "Correctly creates sequence" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List()),
            CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        val db = dbPool.acquire()
        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSequenceLibrary.getSequenceNames.contains("MYSEQUENCE") shouldBe true
        val sequence = db.getMetadata.getSequenceLibrary.getSequence("MySequence")
        sequence.getSequenceType shouldBe SEQUENCE_TYPE.ORDERED
        db.close()
      }
    }

    "Processing a DropSequence change" must {
      "Correctly drops sequence" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List()),
            DropSequence("MySequence")))

        val db = dbPool.acquire()

        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getSequenceLibrary.getSequenceNames.contains("MYSEQUENCE") shouldBe false
        db.close()
      }
    }

    "Processing a CreateFunction change" must {
      "Correctly creates function" in withDb { dbPool =>
        val delta = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))
        val db = dbPool.acquire()

        val processor = new DatabaseDeltaProcessor(delta, db)
        processor.apply()

        db.getMetadata.getFunctionLibrary.getFunction("MyFunction") != null shouldBe true
        db.close()
      }
    }

  }

  var dbCounter = 0
  def withDb(testCode: OPartitionedDatabasePool => Any): Unit = {
    // make sure no accidental collisions
    val dbName = getClass.getSimpleName
    val uri = s"memory:${dbName}${dbCounter}"
    dbCounter += 1

    // FIXME see https://github.com/orientechnologies/orientdb/issues/5146
    ODatabaseRecordThreadLocal.INSTANCE

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()

    val dbPool = new OPartitionedDatabasePool(uri, "admin", "admin")

    try {
      testCode(dbPool)
    } finally {
      dbPool.close()
      db.activateOnCurrentThread()
      db.drop() // Drop will close and drop
    }
  }
}