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
        val select = SelectStatement("myCollection", None, List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters("SELECT FROM Model WHERE collection.id = :0", Map("0" -> "myCollection"))
      }
    }
    "given a limit" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", None, List(), Some(5), None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 LIMIT 5",
            Map("0" -> "myCollection"))
      }
    }
    "given an offset" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", None, List(), None, Some(5))
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 SKIP 5",
            Map("0" -> "myCollection"))
      }
    }
    "given a limit and offset" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", None, List(), Some(4), Some(5))
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 SKIP 5 LIMIT 4",
            Map("0" -> "myCollection"))
      }
    }
    "given 1 order by" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", None, List(OrderBy("someField", None)), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 ORDER BY someField ASC",
            Map("0" -> "myCollection"))
      }
    }
    "given multiple order bys" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", None, List(OrderBy("someField", Some(Ascending)), OrderBy("anotherField", Some(Descending))), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 ORDER BY someField ASC, anotherField DESC",
            Map("0" -> "myCollection"))
      }
    }
    "given a equals where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(Equals(FieldExpressionValue("name"), StringExpressionValue("Alice"))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (name = :1)",
            Map("0" -> "myCollection", "1" -> "Alice"))
      }
    }
    "given a NotEquals where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(NotEquals(FieldExpressionValue("name"), StringExpressionValue("Alice"))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (name != :1)",
            Map("0" -> "myCollection", "1" -> "Alice"))
      }
    }
    "given a GreaterThan where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(GreaterThan(FieldExpressionValue("age"), DoubleExpressionValue(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (age > :1)",
            Map("0" -> "myCollection", "1" -> 15d))
      }
    }
    "given a LessThan where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(LessThan(FieldExpressionValue("age"), LongExpressionValue(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (age < :1)",
            Map("0" -> "myCollection", "1" -> 15l))
      }
    }
    "given a LessThanOrEqual where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(LessThanOrEqual(FieldExpressionValue("age"), LongExpressionValue(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (age <= :1)",
            Map("0" -> "myCollection", "1" -> 15l))
      }
    }
    "given a GreaterThanOrEqual where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(GreaterThanOrEqual(FieldExpressionValue("age"), LongExpressionValue(15))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (age >= :1)",
            Map("0" -> "myCollection", "1" -> 15l))
      }
    }
    "given an In where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(In("name", List("Alice", "Bob"))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (name in :1)",
            Map("0" -> "myCollection", "1" -> List("Alice", "Bob").asJava))
      }
    }
    "given a Like where clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", Some(Like("name", "Ali%")), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (name like :1)",
            Map("0" -> "myCollection", "1" -> "Ali%"))
      }
    }
    "given a Add Operater clause" must {
      "return correct ModelQueryParameters" in {
        val select = SelectStatement("myCollection", 
            Some(LessThanOrEqual(FieldExpressionValue("age"), 
                Add(LongExpressionValue(15), LongExpressionValue(5)))), List(), None, None)
        ModelQueryBuilder.queryModels(select) shouldBe
          ModelQueryParameters(
            "SELECT FROM Model WHERE collection.id = :0 and (age <= (:1 + :2))",
            Map("0" -> "myCollection", "1" -> 15l, "2" -> 5l))
      }
    }
  }
}