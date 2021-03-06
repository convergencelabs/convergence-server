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

package com.convergencelabs.convergence.server.backend.db.schema

import com.convergencelabs.convergence.server.backend.db.schema.delta._
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db._
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike

class DeltaProcessorSpec extends AnyWordSpecLike with Matchers {
  OLogManager.instance().setConsoleLevel("WARNING")

  "A DeltaProcessor" when {
    "Processing a CreateClass change" must {
      "Correctly create class" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List())),
          Some("Description"))

        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.close()
      }

      "Correctly create class and its properties" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.String, None, None, None)))),
          Some("Description"))

        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.close()
      }

      "Correctly create class with superclass" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MySuperclass", None, None, List()),
            CreateClass("MyClass", Some("MySuperclass"), None, List())),
          Some("Description"))

        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.existsClass("MySuperclass") shouldBe true
        db.close()
      }
    }

    "Processing an AlterClass change" must {
      "Correctly alter class name" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List()),
            AlterClass("MyClass", Some("NewName"), None)),
          Some("Description"))
        val db = dbPool.acquire()

        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe false
        db.getMetadata.getSchema.existsClass("NewName") shouldBe true
        db.close()
      }

      "Correctly alter superclass" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List()),
            CreateClass("MySuperclass", None, None, List()),
            AlterClass("MyClass", None, Some("MySuperclass"))),
          Some("Description"))
        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").hasSuperClasses shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").getSuperClassesNames.get(0) shouldBe "MySuperclass"
        db.close()
      }
    }

    "Processing a DropClass change" must {
      "Correctly drops class" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List()),
            DropClass("MyClass")),
          Some("Description"))
        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe false
        db.close()
      }
    }

    "Processing a AddProperty change" must {
      "Correctly adds new property to class" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List()),
            AddProperty("MyClass", Property("prop1", OrientType.String, None, None, None))),
          Some("Description"))
        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.close()
      }
    }

    "Processing a AlterProperty change" must {
      "Correctly alters property class" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            AlterProperty("MyClass", "prop1", PropertyOptions(None, Some(OrientType.Integer), None, None, None))),
          Some("Description"))
        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").getProperty("prop1").getType shouldEqual OType.INTEGER
        db.close()
      }

      "Correctly alters property name" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            AlterProperty("MyClass", "prop1", PropertyOptions(Some("prop2"), None, None, None, None))),
          Some("Description"))
        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop2") shouldBe true

        db.close()
      }
    }

    "Processing a DropProperty change" must {
      "Correctly drops property from class" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            DropProperty("MyClass", "prop1")),
          Some("Description"))
        val db = dbPool.acquire()

        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSchema.existsClass("MyClass") shouldBe true
        db.getMetadata.getSchema.getClass("MyClass").existsProperty("prop1") shouldBe false
        db.close()
      }
    }

    "Processing a CreateIndex change" must {
      "Correctly creates unique index for class" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None)),
          Some("Description"))

        val db = dbPool.acquire()

        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getIndexManager.existsIndex("MyClass.prop1") shouldBe true
        val index = db.getMetadata.getIndexManager.getIndex("MyClass.prop1")
        index.getDefinition.getFields.get(0) shouldBe "prop1"
        db.close()
      }
    }

    "Processing a DropIndex change" must {
      "Correctly drops index" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None),
            DropIndex("MyClass.prop1")),
          Some("Description"))
        val db = dbPool.acquire()

        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getIndexManager.existsIndex("MyClass.prop1") shouldBe false
        db.close()
      }
    }

    "Processing a CreateSequence change" must {
      "Correctly creates sequence" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List()),
            CreateSequence("MySequence", SequenceType.Ordered, None, None, None)),
          Some("Description"))

        val db = dbPool.acquire()
        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSequenceLibrary.getSequenceNames.contains("MYSEQUENCE") shouldBe true
        val sequence = db.getMetadata.getSequenceLibrary.getSequence("MySequence")
        sequence.getSequenceType shouldBe SEQUENCE_TYPE.ORDERED
        db.close()
      }
    }

    "Processing a DropSequence change" must {
      "Correctly drops sequence" in withDb { dbPool =>
        val delta = Delta(
          List(CreateClass("MyClass", None, None, List()),
            DropSequence("MySequence")),
          Some("Description"))

        val db = dbPool.acquire()

        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getSequenceLibrary.getSequenceNames.contains("MYSEQUENCE") shouldBe false
        db.close()
      }
    }

    "Processing a CreateFunction change" must {
      "Correctly creates function" in withDb { dbPool =>
        val delta = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)),
          Some("Description"))
        val db = dbPool.acquire()

        val processor = new DeltaProcessor(delta, db)
        processor.applyDelta().get

        db.getMetadata.getFunctionLibrary.getFunction("MyFunction") != null shouldBe true
        db.close()
      }
    }
  }

  var dbCounter = 0

  def withDb(testCode: ODatabasePool => Any): Unit = {
    val dbName = getClass.getSimpleName + dbCounter
    dbCounter += 1

    val odb = new OrientDB("memory:", "admin", "admin", OrientDBConfig.defaultConfig())
    odb.create(dbName, ODatabaseType.MEMORY)

    val dbPool = new ODatabasePool(odb, dbName, "admin", "admin")

    try {
      testCode(dbPool)
    } finally {
      dbPool.close()
      odb.drop(dbName)
      odb.close()
    }
  }
}
