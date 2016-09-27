package com.convergencelabs.server.schema

import org.scalatest.WordSpecLike
import org.scalatest.Matchers
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE

class OrientSchemaProcessorSpec extends WordSpecLike with Matchers {
  OLogManager.instance().setConsoleLevel("WARNING")

  "An OrientSchemaProcessor" when {
    "Processing a CreateClass change" must {
      "Corrrectly create class" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List())))
        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
      }

      "Correctly create class and its properties" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None,
            List(Property("prop1", OrientType.STRING, None, None)))))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
      }

      "Correctly create class with superclass" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MySuperclass", None, List()),
            CreateClass("MyClass", Some("MySuperclass"), List())))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.existsClass("MySuperclass") shouldBe true
      }
    }

    "Processing an AlterClass change" must {
      "Correctly alter class name" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List()),
            AlterClass("MyClass", Some("NewName"), None)))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe false
        db.getMetadata.getSchema.existsClass("NewName") shouldBe true
      }

      "Correctly alter superclass" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List()),
            CreateClass("MySuperclass", None, List()),
            AlterClass("MyClass", None, Some("MySuperclass"))))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").hasSuperClasses() shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").getSuperClassesNames.get(0) shouldBe "MySuperclass"
      }
    }

    "Processing a DropClass change" must {
      "Correctly drops class" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List()),
            DropClass("MyClass")))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe false
      }
    }

    "Processing a AddProperty change" must {
      "Correctly adds new property to class" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List()),
            AddProperty("MyClass", Property("prop1", OrientType.STRING, None, None))))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
      }
    }

    "Processing a AlterProperty change" must {
      "Correctly alters property class" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None,
            List(Property("prop1", OrientType.SHORT, None, None))),
            AlterProperty("MyClass", "prop1", PropertyOptions(None, Some(OrientType.INTEGER), None, None))))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").getProperty("prop1").getType shouldEqual (OType.INTEGER)
      }

      "Correctly alters property name" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None,
            List(Property("prop1", OrientType.SHORT, None, None))),
            AlterProperty("MyClass", "prop1", PropertyOptions(Some("prop2"), None, None, None))))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop2") shouldBe true
      }
    }

    "Processing a DropProperty change" must {
      "Correctly drops property from class" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None,
            List(Property("prop1", OrientType.SHORT, None, None))),
            DropProperty("MyClass", "prop1")))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe false
      }
    }

    "Processing a CreateIndex change" must {
      "Correctly creates unique index for class" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None,
            List(Property("prop1", OrientType.SHORT, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.UNIQUE, List("prop1"))))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getIndexManager.existsIndex("MyClass.prop1") shouldBe true
        val index = db.getMetadata.getIndexManager.getIndex("MyClass.prop1")
        index.getDefinition.getFields.get(0) shouldBe "prop1"
      }
    }

    "Processing a DropIndex change" must {
      "Correctly drops index" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List(Property("prop1", OrientType.SHORT, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.UNIQUE, List("prop1")),
            DropIndex("MyClass.prop1")))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getIndexManager.existsIndex("MyClass.prop1") shouldBe false
      }
    }
    
    "Processing a CreateSequence change" must {
      "Correctly creates sequence" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List()),
            CreateSequence("MySequence", SequenceType.ordered, None, None, None)))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSequenceLibrary.getSequenceNames.contains("MYSEQUENCE") shouldBe true
        val sequence = db.getMetadata.getSequenceLibrary.getSequence("MySequence")
        sequence.getSequenceType shouldBe SEQUENCE_TYPE.ORDERED
      }
    }

    "Processing a DropSequence change" must {
      "Correctly drops sequence" in withDb { db =>
        val delta = Delta(1, "Description",
          List(CreateClass("MyClass", None, List()),
            DropSequence("MySequence")))

        val processor = new OrientSchemaProcessor(db)
        processor.applyDelta(delta)
        db.getMetadata.getSequenceLibrary.getSequenceNames.contains("MYSEQUENCE") shouldBe false
      }
    }

  }

  var dbCounter = 0
  def withDb(testCode: ODatabaseDocumentTx => Any): Unit = {
    // make sure no accidental collisions
    val dbName = getClass.getSimpleName
    val uri = s"memory:${dbName}${dbCounter}"
    dbCounter += 1

    val db = new ODatabaseDocumentTx(uri)
    db.activateOnCurrentThread()
    db.create()

    try {
      testCode(db)
    } finally {
      db.activateOnCurrentThread()
      db.drop() // Drop will close and drop
    }
  }
}