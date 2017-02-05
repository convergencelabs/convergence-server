package com.convergencelabs.server.domain.model.query

import org.scalatest.WordSpec
import org.scalatest.Matchers

import Ast._

class SelectStatementSpec
    extends WordSpec
    with Matchers {

  "A QueryParser" when {

    "parsing a String" must {
      "parse a single quoted string" in {
        new QueryParser("'test'").Value.run().get shouldBe StringTerm("test")
      }

      "parse a single quoted string containing an escaped single quote" in {
        new QueryParser("'x\\'y'").Value.run().get shouldBe StringTerm("x'y")
      }

      "parse a single quoted string containing a double quote" in {
        new QueryParser("'x\"y'").Value.run().get shouldBe StringTerm("x\"y")
      }

      "parse a double quoted string" in {
        new QueryParser("\"test\"").Value.run().get shouldBe StringTerm("test")
      }

      "parse a double quoted string containing an escaped double quote" in {
        new QueryParser("\"x\\\"y\"").Value.run().get shouldBe StringTerm("x\"y")
      }

      "parse a double quoted string containing an single quote" in {
        new QueryParser("\"x'y\"").Value.run().get shouldBe StringTerm("x'y")
      }
    }
  }

  "parsing a Boolean" must {
    "parse true ignoring case" in {
      new QueryParser("tRuE").Value.run().get shouldBe BooleanTerm(true)
    }

    "parse false ignoring case" in {
      new QueryParser("FaLsE").Value.run().get shouldBe BooleanTerm(false)
    }
  }

  "parsing a number" must {
    "parse a unsigned long" in {
      new QueryParser("42").Value.run().get shouldBe LongTerm(42)
    }

    "parse a positive signed long" in {
      new QueryParser("+42").Value.run().get shouldBe LongTerm(42)
    }

    "parse a negative signed long" in {
      new QueryParser("-42").Value.run().get shouldBe LongTerm(-42)
    }

    "parse an unsigned double" in {
      new QueryParser("4.2").Value.run().get shouldBe DoubleTerm(4.2)
    }

    "parse a positive signed double" in {
      new QueryParser("+4.2").Value.run().get shouldBe DoubleTerm(4.2)
    }

    "parse a negative signed double" in {
      new QueryParser("-4.2").Value.run().get shouldBe DoubleTerm(-4.2)
    }
  }

  "parsing an Order By" must {
    "parse DESC as descending" in {
      new QueryParser("ORDER BY foo DESC").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Descending)))
    }

    "parse DESCENDING as descending" in {
      new QueryParser("ORDER BY foo DESCENDING").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Descending)))
    }

    "parse ASC as ascending" in {
      new QueryParser("ORDER BY foo ASC").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Ascending)))
    }

    "parse ASCENDING as ascending" in {
      new QueryParser("ORDER BY foo ASCENDING").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Ascending)))
    }

    "parse no direction as None" in {
      new QueryParser("ORDER BY foo").OrderBySection.run().get shouldBe List(OrderBy("foo", None))
    }
  }

  "parsing a ConditionalRule" must {
    "Correctly parse a less than" in {
      QueryParser("field < 5").ConditionalRule.run().get shouldBe
        LessThan(FieldTerm("field"), LongTerm(5))
    }

    "Correctly parse a greater than" in {
      QueryParser("10 > -5").ConditionalRule.run().get shouldBe
        GreaterThan(LongTerm(10), LongTerm(-5))
    }

    "Correctly parse equals" in {
      QueryParser("10 = -5.5").ConditionalRule.run().get shouldBe
        Equals(LongTerm(10), DoubleTerm(-5.5))
    }

    "Correctly parse not equals" in {
      QueryParser("+5.5 != test").ConditionalRule.run().get shouldBe
        NotEquals(DoubleTerm(5.5), FieldTerm("test"))
    }

    "Correctly parse less than or equal" in {
      QueryParser("foo <= bar").ConditionalRule.run().get shouldBe
        LessThanOrEqual(FieldTerm("foo"), FieldTerm("bar"))
    }

    "Correctly parse greater than or equal" in {
      QueryParser("true >= bar").ConditionalRule.run().get shouldBe
        GreaterThanOrEqual(BooleanTerm(true), FieldTerm("bar"))
    }
  }

  "parsing a Where Expression" must {
    "Give precedence to AND over OR" in {
      QueryParser("foo = 1 OR bar = 2 AND baz = 3").WhereRule.run().get shouldBe
        Or(
          Equals(FieldTerm("foo"), LongTerm(1)),
          And(
            Equals(FieldTerm("bar"), LongTerm(2)),
            Equals(FieldTerm("baz"), LongTerm(3))))
    }

    "Give precedence parenthesis" in {
      QueryParser("(foo = 1 OR bar = 2) AND baz = 3").WhereRule.run().get shouldBe
        And(
          Or(
            Equals(FieldTerm("foo"), LongTerm(1)),
            Equals(FieldTerm("bar"), LongTerm(2))),
          Equals(FieldTerm("baz"), LongTerm(3)))
    }

    "Apply not with no parens" in {
      QueryParser("NOT foo = 1").WhereRule.run().get shouldBe
        Not(Equals(FieldTerm("foo"), LongTerm(1)))
    }

    "Apply not with  parens" in {
      val result = QueryParser("NOT (foo = 1)").WhereRule.run().get
      println(result)
        result shouldBe Not(Equals(FieldTerm("foo"), LongTerm(1)))
    }

    "Apply not to only first term wothout parens" in {
      QueryParser("NOT foo = 1 AND bar = 2").WhereRule.run().get shouldBe
        And(
          Not(Equals(FieldTerm("foo"), LongTerm(1))),
            Equals(FieldTerm("bar"), LongTerm(2)))
    }
    
    "Terminate field name on a symbol" in {
      QueryParser("foo=1").WhereRule.run().get shouldBe
         Equals(FieldTerm("foo"), LongTerm(1))
    }
  }

  "parsing a SELECT Statement" must {
    "parse SELECT FROM collection" in {
      QueryParser.parse("SELECT FROM collection").get shouldBe SelectStatement("collection", None, List(), None, None)
    }

    "parse SELECT * FROM collection" in {
      QueryParser.parse("SELECT * FROM collection").get shouldBe SelectStatement("collection", None, List(), None, None)
    }

    "parse SELECT * FROM collection WHERE foo = 1" in {
      QueryParser.parse("SELECT * FROM collection WHERE foo = 1").get shouldBe SelectStatement(
        "collection",
        Some(Equals(FieldTerm("foo"), LongTerm(1))),
        List(),
        None,
        None)
    }

    "parse SELECT * FROM collection WHERE foo = 1 LIMIT 3 OFFSET 10" in {
      QueryParser.parse("SELECT * FROM collection WHERE foo = 1 LIMIT 3 OFFSET 10").get shouldBe SelectStatement(
        "collection",
        Some(Equals(FieldTerm("foo"), LongTerm(1))),
        List(),
        Some(3),
        Some(10))
    }

    "parse SELECT * FROM collection WHERE foo = 1 ORDER BY bar LIMIT 3 OFFSET 10" in {
      QueryParser.parse("SELECT * FROM collection WHERE foo = 1 ORDER BY bar LIMIT 3 OFFSET 10").get shouldBe SelectStatement(
        "collection",
        Some(Equals(FieldTerm("foo"), LongTerm(1))),
        List(OrderBy("bar", None)),
        Some(3),
        Some(10))
    }
  }
}