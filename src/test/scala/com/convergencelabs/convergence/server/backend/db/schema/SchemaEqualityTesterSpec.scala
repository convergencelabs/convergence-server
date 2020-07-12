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

        val delta = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)),
          Some("Description"))

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta, db2).applyDelta()

        SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if function code is different" in {
        val delta1 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)),
          Some("Description"))

        val delta2 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nval from = parseInt(fromIndex);\narray.add(toIn, array.remove(from));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)),
          Some("Description"))

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if function name is different" in {
        val delta1 = Delta(
          List(CreateFunction("MyFunction1",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)), None)

        val delta2 = Delta(
          List(CreateFunction("MyFunction2",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if function parameters are different" in {
        val delta1 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)), None)

        val delta2 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex"), None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if one function has a different language" in {
        val delta1 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)), None)

        val delta2 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), Some("javascript"), None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if one function is idempotent and the other is not" in {
        val delta1 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, None)), None)

        val delta2 = Delta(
          List(CreateFunction("MyFunction",
            "var toIn = parseInt(toIndex);\nvar fromIn = parseInt(fromIndex);\narray.add(toIn, array.remove(fromIn));\nreturn array;",
            List("array", "fromIndex", "toIndex"), None, Some(true))), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }
    }

    "comparing sequences" must {
      "return true if sequences are the same" in {

        val delta = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta, db2).applyDelta()

        SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different types" in {

        val delta1 = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)), None)

        val delta2 = Delta(
          List(CreateSequence("MySequence", SequenceType.Cached, None, None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different names" in {
        val delta1 = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)), None)

        val delta2 = Delta(
          List(CreateSequence("MySequence2", SequenceType.Ordered, None, None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different starts" in {
        val delta1 = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, Some(5), None, None)), None)

        val delta2 = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if sequences have different increments" in {
        val delta1 = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, None, Some(5), None)), None)

        val delta2 = Delta(
          List(CreateSequence("MySequence", SequenceType.Ordered, None, None, None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }
    }

    "comparing classes" must {
      "return no error if classes are the same" in {
        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.String, None, None, None)))), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta, db2).applyDelta()

        SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if class names are different" in {
        val delta1 = Delta(
          List(CreateClass("MyClass", None, None, List())), None)

        val delta2 = Delta(
          List(CreateClass("MyClass2", None, None, List())), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }

      "return error if class superclass is different" in {
        val delta1 = Delta(
          List(CreateClass("MyClass", None, None, List())), None)

        val delta2 = Delta(
          List(CreateClass("MySuperClass", None, None, List())), None)

        val delta3 = Delta(
          List(CreateClass("MyClass", Some("MySuperClass"), None, List())), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta1, db1).applyDelta()
        new DeltaProcessor(delta2, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta2, db2).applyDelta()
        new DeltaProcessor(delta3, db2).applyDelta()

        an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
      }
    }

    "return error if one class is abstract" in {
      val delta1 = Delta(
        List(CreateClass("MyClass", None, None, List())), None)

      val delta2 = Delta(
        List(CreateClass("MyClass", None, Some(true), List())), None)

      db1.activateOnCurrentThread()
      new DeltaProcessor(delta1, db1).applyDelta()
      db2.activateOnCurrentThread()
      new DeltaProcessor(delta2, db2).applyDelta()

      an [AssertionError] should be thrownBy SchemaEqualityTester.assertEqual(db1, db2)
    }

    "comparing indexes" must {
      "return no error if indexes are the same" in {

        val delta = Delta(
          List(CreateClass("MyClass", None, None,
            List(Property("prop1", OrientType.Short, None, None, None))),
            CreateIndex("MyClass", "MyClass.prop1", IndexType.Unique, List("prop1"), None)), None)

        db1.activateOnCurrentThread()
        new DeltaProcessor(delta, db1).applyDelta()
        db2.activateOnCurrentThread()
        new DeltaProcessor(delta, db2).applyDelta()

        SchemaEqualityTester.assertEqual(db1, db2)
      }
    }
  }
}
