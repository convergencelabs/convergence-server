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

import com.orientechnologies.orient.core.db.{ODatabaseSession, ODatabaseType, OrientDB, OrientDBConfig}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.{BeforeAndAfterAll, BeforeAndAfterEach}

class SchemaEqualityTesterSpec 
  extends AnyWordSpecLike
  with Matchers
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  private[this] val odb = new OrientDB("memory:", "admin", "admin", OrientDBConfig.defaultConfig())

  private[this] var dbCounter = 1
  private[this] val dbName = getClass.getSimpleName

  private[this] var db1: ODatabaseSession = _
  private[this] var db2: ODatabaseSession = _

  override def beforeEach(): Unit =  {
    val dbName1 = s"$dbName$dbCounter"
    dbCounter += 1
    
    odb.create(dbName1, ODatabaseType.MEMORY)
    db1 = odb.open(dbName1, "admin", "admin")

    val dbName2 = s"$dbName$dbCounter"
    dbCounter += 1

    odb.create(dbName2, ODatabaseType.MEMORY)
    db2 = odb.open(dbName2, "admin", "admin")
  }

  override def afterEach(): Unit = {
    db1.activateOnCurrentThread()
    db1.close()
    
    db2.activateOnCurrentThread()
    db2.close()
    
    odb.drop(db1.getName)
    odb.drop(db2.getName)
    
    db1 = null
    db2 = null
  }
  
  override def afterAll(): Unit = {
    odb.close()
  }

  "SchemaEqualityTester" when {
    "comparing functions" must {
      "return no error if functions are the same" in {

        val delta = Delta(1, Some("Description"),
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if function code is different" in {
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

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if function name is different" in {
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

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if function parameters are different" in {
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

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if one function has a different language" in {
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

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if one function is idempotent and the other is not" in {
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

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
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

        SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different types" in {

        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Cached, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different names" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence2", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different starts" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, Some(5), None, None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different increments" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, Some(5), None)))

        val delta2 = Delta(1, Some("Description"),
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }
    }

    "comparing classes" must {
      "return no error if classes are the same" in {
        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.String, None, None, None)))))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if class names are different" in {
        val delta1 = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None, List())))

        val delta2 = Delta(1, Some("Description"),
          List(CreateClass("MyClass2", None, None, List())))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta1, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta2, db2)

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if class superclass is different" in {
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

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }
    }

    "return error if one class is abstract" in {
      val delta1 = Delta(1, Some("Description"),
        List(CreateClass("MyClass", None, None, List())))

      val delta2 = Delta(1, Some("Description"),
        List(CreateClass("MyClass", None, Some(true), List())))

      db1.activateOnCurrentThread()
      DatabaseDeltaProcessor.apply(delta1, db1)
      db2.activateOnCurrentThread()
      DatabaseDeltaProcessor.apply(delta2, db2)

      an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
    }

    "comparing indexes" must {
      "return no error if indexes are the same" in {

        val delta = Delta(1, Some("Description"),
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None)))

        db1.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db1)
        db2.activateOnCurrentThread()
        DatabaseDeltaProcessor.apply(delta, db2)

        SchemaEqualityTester.assertEqual(db1, db2)
      }
    }
  }
}
