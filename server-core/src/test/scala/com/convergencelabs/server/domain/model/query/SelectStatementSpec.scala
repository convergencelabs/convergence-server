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
        new QueryParser("'test'").Value.run().get shouldBe StringExpressionValue("test")
      }
      
      "parse a single quoted string containing an escaped single quote" in {
        new QueryParser("'x\\'y'").Value.run().get shouldBe StringExpressionValue("x'y")
      }
      
      "parse a single quoted string containing a double quote" in {
        new QueryParser("'x\"y'").Value.run().get shouldBe StringExpressionValue("x\"y")
      }
      
      "parse a double quoted string" in {
        new QueryParser("\"test\"").Value.run().get shouldBe StringExpressionValue("test")
      }
      
      "parse a double quoted string containing an escaped double quote" in {
        new QueryParser("\"x\\\"y\"").Value.run().get shouldBe StringExpressionValue("x\"y")
      }
      
      "parse a double quoted string containing an single quote" in {
        new QueryParser("\"x'y\"").Value.run().get shouldBe StringExpressionValue("x'y")
      }
    }
   }
   
   "parsing a Boolean" must {
      "parse true ignoring case" in {
        new QueryParser("tRuE").Value.run().get shouldBe BooleanExpressionValue(true)
      }
      
      "parse false ignoring case" in {
        new QueryParser("FaLsE").Value.run().get shouldBe BooleanExpressionValue(false)
      }
   }
   
   "parsing a number" must {
      "parse a unsigned long" in {
        new QueryParser("42").Value.run().get shouldBe LongExpressionValue(42)
      }
      
      "parse a positive signed long" in {
        new QueryParser("+42").Value.run().get shouldBe LongExpressionValue(42)
      }
      
      "parse a negative signed long" in {
        new QueryParser("-42").Value.run().get shouldBe LongExpressionValue(-42)
      }
      
      "parse an unsigned double" in {
        new QueryParser("4.2").Value.run().get shouldBe DoubleExpressionValue(4.2)
      }
      
      "parse a positive signed double" in {
        new QueryParser("+4.2").Value.run().get shouldBe DoubleExpressionValue(4.2)
      }
      
      "parse a negative signed double" in {
        new QueryParser("-4.2").Value.run().get shouldBe DoubleExpressionValue(-4.2)
      }
   }
   
   
//   "parsing an Order By" must {
//     "parse DESC as descending" in {
//       new QueryParser("ORDER BY foo DESC").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Descending)))
//     }
//     
//     "parse DESCENDING as descending" in {
//       new QueryParser("ORDER BY foo DESCENDING").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Descending)))
//     }
//     
//     "parse ASC as ascending" in {
//       new QueryParser("ORDER BY foo ASC").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Ascending)))
//     }
//     
//     "parse ASCENDING as ascending" in {
//       new QueryParser("ORDER BY foo ASCENDING").OrderBySection.run().get shouldBe List(OrderBy("foo", Some(Ascending)))
//     }
//     
//     "parse no direction as None" in {
//       new QueryParser("ORDER BY foo").OrderBySection.run().get shouldBe List(OrderBy("foo", None))
//     }
//   }
//   
//   "parsing a WHERE Section" must {
//      "parse a unsigned long" in {
//        // FIXME Broken
//        // println(new QueryParser("field < 5").Term.run().get)
//      }
//   }
//   
//   "parsing a SELECT Statement" must {
//      "parse a unsigned long" in {
//        println(new QueryParser("WHERE blah = 'Blah' or (foo = 'bar' and bob = 'alice')").WhereSection.run().get)
//      }
//   }

}