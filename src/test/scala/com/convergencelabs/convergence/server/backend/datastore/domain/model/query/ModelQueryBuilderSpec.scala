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

package com.convergencelabs.convergence.server.backend.datastore.domain.model.query

import com.convergencelabs.convergence.server.backend.datastore.domain.model.query.Ast._
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

import scala.jdk.CollectionConverters._

class ModelQueryBuilderSpec extends AnyWordSpec with Matchers {

  "A ModelQueryBuilder" when {

    "given only a collection" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters("SELECT FROM Model WHERE collection.id = :p0", Map("p0" -> "myCollection"), Map())
      }
    }
    "given a limit" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), QueryLimit(5), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 LIMIT 5",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given an offset" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), QueryLimit(), QueryOffset(5))
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 SKIP 5",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given a limit and offset" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), QueryLimit(4), QueryOffset(5))
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 SKIP 5 LIMIT 4",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given 1 order by" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(
          OrderBy(FieldTerm(PropertyPathElement("someField")), None)), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 ORDER BY data.children.`someField`.value ASC",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given multiple order bys" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(
          OrderBy(FieldTerm(PropertyPathElement("someField")), Some(Ascending)),
          OrderBy(FieldTerm(PropertyPathElement("anotherField")), Some(Descending))), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 ORDER BY data.children.`someField`.value ASC, data.children.`anotherField`.value DESC",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given a equals where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(Equals(FieldTerm(PropertyPathElement("name")), StringTerm("Alice"))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`name`.value = :p1)",
            Map("p0" -> "myCollection", "p1" -> "Alice"), Map())
      }
    }
    "given a NotEquals where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(NotEquals(FieldTerm(PropertyPathElement("name")), StringTerm("Alice"))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`name`.value != :p1)",
            Map("p0" -> "myCollection", "p1" -> "Alice"), Map())
      }
    }
    "given a GreaterThan where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(GreaterThan(FieldTerm(PropertyPathElement("age")), DoubleTerm(15))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`age`.value > :p1)",
            Map("p0" -> "myCollection", "p1" -> 15d), Map())
      }
    }
    "given a LessThan where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(LessThan(FieldTerm(PropertyPathElement("age")), LongTerm(15))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`age`.value < :p1)",
            Map("p0" -> "myCollection", "p1" -> 15L), Map())
      }
    }
    "given a LessThanOrEqual where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(LessThanOrEqual(FieldTerm(PropertyPathElement("age")), LongTerm(15))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`age`.value <= :p1)",
            Map("p0" -> "myCollection", "p1" -> 15L), Map())
      }
    }
    "given a GreaterThanOrEqual where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(GreaterThanOrEqual(FieldTerm(PropertyPathElement("age")), LongTerm(15))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`age`.value >= :p1)",
            Map("p0" -> "myCollection", "p1" -> 15L), Map())
      }
    }
    "given an In where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(In(FieldTerm(PropertyPathElement("name")), List("Alice", "Bob"))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`name`.value in :p1)",
            Map("p0" -> "myCollection", "p1" -> List("Alice", "Bob").asJava), Map())
      }
    }
    "given a Like where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(Like(FieldTerm(PropertyPathElement("name")), StringTerm("Ali%"))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`name`.value like :p1)",
            Map("p0" -> "myCollection", "p1" -> "Ali%"), Map())
      }
    }
    "given a Add Operator clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection",
          Some(LessThanOrEqual(FieldTerm(PropertyPathElement("age")),
            Add(LongTerm(15), LongTerm(5)))), List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 AND (data.children.`age`.value <= (:p1 + :p2))",
            Map("p0" -> "myCollection", "p1" -> 15L, "p2" -> 5L), Map())
      }
    }
    "given a projection field without 'as'" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(ProjectionTerm(FieldTerm(PropertyPathElement("age")), None)), "myCollection", None, List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
              "SELECT collection.id as collectionId, id, version, createdTime, modifiedTime, data.children.`age` as a0 FROM Model WHERE collection.id = :p0", 
              Map("p0" -> "myCollection"), Map("a0" -> "age"))
      }
    }
    "given a projection field with 'as'" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(ProjectionTerm(FieldTerm(PropertyPathElement("age")), Some("myAge"))), "myCollection", None, List(), QueryLimit(), QueryOffset())
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
              "SELECT collection.id as collectionId, id, version, createdTime, modifiedTime, data.children.`age` as a0 FROM Model WHERE collection.id = :p0", 
              Map("p0" -> "myCollection"), Map("a0" -> "myAge"))
      }
    }
  }
}