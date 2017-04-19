package com.convergencelabs.server.domain.model.query

import org.scalatest.WordSpec
import org.scalatest.Matchers

import scala.collection.JavaConverters.seqAsJavaListConverter

import Ast._
import com.convergencelabs.server.datastore.domain.ModelQueryBuilder
import com.convergencelabs.server.datastore.domain.ModelQueryParameters

class ModelQueryBuilderSpec extends WordSpec with Matchers {

  "A ModelQueryBuilder" when {

    "given only a collection" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters("SELECT FROM Model WHERE collection.id = :p0", Map("p0" -> "myCollection"), Map())
      }
    }
    "given a limit" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), Some(5), None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 LIMIT 5",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given an offset" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), None, Some(5))
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 SKIP 5",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given a limit and offset" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(), Some(4), Some(5))
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 SKIP 5 LIMIT 4",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given 1 order by" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(
          OrderBy(FieldTerm(PropertyPathElement("someField")), None)), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 ORDER BY data.children[`someField`].value ASC",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given multiple order bys" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", None, List(
          OrderBy(FieldTerm(PropertyPathElement("someField")), Some(Ascending)),
          OrderBy(FieldTerm(PropertyPathElement("anotherField")), Some(Descending))), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 ORDER BY data.children[`someField`].value ASC, data.children[`anotherField`].value DESC",
            Map("p0" -> "myCollection"), Map())
      }
    }
    "given a equals where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(Equals(FieldTerm(PropertyPathElement("name")), StringTerm("Alice"))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`name`].value = :p1)",
            Map("p0" -> "myCollection", "p1" -> "Alice"), Map())
      }
    }
    "given a NotEquals where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(NotEquals(FieldTerm(PropertyPathElement("name")), StringTerm("Alice"))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`name`].value != :p1)",
            Map("p0" -> "myCollection", "p1" -> "Alice"), Map())
      }
    }
    "given a GreaterThan where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(GreaterThan(FieldTerm(PropertyPathElement("age")), DoubleTerm(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`age`].value > :p1)",
            Map("p0" -> "myCollection", "p1" -> 15d), Map())
      }
    }
    "given a LessThan where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(LessThan(FieldTerm(PropertyPathElement("age")), LongTerm(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`age`].value < :p1)",
            Map("p0" -> "myCollection", "p1" -> 15l), Map())
      }
    }
    "given a LessThanOrEqual where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(LessThanOrEqual(FieldTerm(PropertyPathElement("age")), LongTerm(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`age`].value <= :p1)",
            Map("p0" -> "myCollection", "p1" -> 15l), Map())
      }
    }
    "given a GreaterThanOrEqual where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(GreaterThanOrEqual(FieldTerm(PropertyPathElement("age")), LongTerm(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`age`].value >= :p1)",
            Map("p0" -> "myCollection", "p1" -> 15l), Map())
      }
    }
    "given an In where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(In(FieldTerm(PropertyPathElement("name")), List("Alice", "Bob"))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`name`].value in :p1)",
            Map("p0" -> "myCollection", "p1" -> List("Alice", "Bob").asJava), Map())
      }
    }
    "given a Like where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection", Some(Like(FieldTerm(PropertyPathElement("name")), StringTerm("Ali%"))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`name`].value like :p1)",
            Map("p0" -> "myCollection", "p1" -> "Ali%"), Map())
      }
    }
    "given a Add Operater clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(), "myCollection",
          Some(LessThanOrEqual(FieldTerm(PropertyPathElement("age")),
            Add(LongTerm(15), LongTerm(5)))), List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :p0 and (data.children[`age`].value <= (:p1 + :p2))",
            Map("p0" -> "myCollection", "p1" -> 15l, "p2" -> 5l), Map())
      }
    }
    "given a projection field without 'as'" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(ProjectionTerm(FieldTerm(PropertyPathElement("age")), None)), "myCollection", None, List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
              "SELECT collection.id as collectionId, id, version, createdTime, modifiedTime, data.children[`age`] as a0 FROM Model WHERE collection.id = :p0", 
              Map("p0" -> "myCollection"), Map("a0" -> "age"))
      }
    }
    "given a projection field with 'as'" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement(List(ProjectionTerm(FieldTerm(PropertyPathElement("age")), Some("myAge"))), "myCollection", None, List(), None, None)
        ModelQueryBuilder.queryModels(select, None) shouldBe
          ModelQueryParameters(
              "SELECT collection.id as collectionId, id, version, createdTime, modifiedTime, data.children[`age`] as a0 FROM Model WHERE collection.id = :p0", 
              Map("p0" -> "myCollection"), Map("a0" -> "myAge"))
      }
    }
  }
}