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

package com.convergencelabs.convergence.server.util

import org.scalatest.wordspec.AnyWordSpecLike
import org.scalatest.matchers.should.Matchers
import com.orientechnologies.common.log.OLogManager
import com.orientechnologies.orient.core.db.document.ODatabaseDocumentTx
import com.orientechnologies.orient.core.metadata.schema.OType
import com.orientechnologies.orient.core.index.OIndex
import com.orientechnologies.orient.core.metadata.sequence.OSequence.SEQUENCE_TYPE
import com.orientechnologies.orient.core.metadata.function.OFunction
import org.json4s.Extraction
import org.json4s.DefaultFormats
import org.json4s.JsonAST.JObject

import org.json4s._
import org.json4s.JsonDSL._
import org.json4s.jackson.JsonMethods._

class PolymorphicSerializerSpec extends AnyWordSpecLike with Matchers {

  "An PolymorphicSerializer" when {
    "Being constructed" must {
      "Disallow duplicate classes" in {
        intercept[IllegalArgumentException] {
          val ser = new PolymorphicSerializer[Person]("t", Map("c" -> classOf[Customer], "r" -> classOf[Customer]))
        }
      }
    }

    "Serializing" must {
      "Respect the type map" in {
        val ser = new PolymorphicSerializer[Person]("tpe", Map("c" -> classOf[Customer], "e" -> classOf[Employee]))
        implicit val formats: Formats = DefaultFormats + ser
        val jValue = Extraction.decompose(Employee("test", "id1"))
        jValue shouldBe JObject(("tpe" -> "e"), ("name" -> "test"), ("employeeId" -> "id1"))
      }
    }
  }
}

sealed trait Person
case class Customer(name: String, customerId: String) extends Person
case class Employee(name: String, employeeId: String) extends Person
