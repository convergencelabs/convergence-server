package com.convergencelabs.server.domain.model.query

import com.convergencelabs.server.domain.model.query.Ast._
import org.parboiled2._
import scala.annotation.switch
import javax.swing.text.html.CSS.StringValue

class QueryParser(val input: ParserInput) extends Parser {
  def InputLine = rule { SelectStatementRule ~ EOI }

  def SelectStatementRule = rule {
    SelectSection ~
      FieldsSection ~
      FromSection ~
      CollectionSection ~
      WhereSection ~
      OrderBySection ~
      LimitSection ~
      OffsetSection ~> (SelectStatement(_, _, _, _, _))
  }

  def SelectSection = rule { SkipWS ~ Select ~ SkipWS }

  def FieldsSection = rule { optional(SkipWS ~ "*" ~ SkipWS) }

  def FromSection = rule { SkipWS ~ From ~ SkipWS }

  def CollectionSection = rule { SkipWS ~ capture(oneOrMore(!WhiteSpaceChar ~ ANY)) ~ SkipWS }

  def WhereSection: Rule1[Option[WhereExpression]] = rule {
    Where ~ WhereRule ~> (Some(_)) | push(None)
  }

  def LimitSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Limit ~
      SkipWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) | push(None)
  }

  def OffsetSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Offset ~
      SkipWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) | push(None)
  }

  def OrderBySection: Rule1[List[OrderBy]] = rule {
    ignoreCase("order by") ~ oneOrMore(OrderByRule).separatedBy(",") ~> ((s: Seq[OrderBy]) => s.toList) | push(List())
  }

  /////////////////////////////////////////////////////////////////////////////
  // Where Expression
  /////////////////////////////////////////////////////////////////////////////

  def WhereRule: Rule1[WhereExpression] = rule { Parens | AndRule | OrRule | NotRule}

  /////////////////////////////////////////////////////////////////////////////
  // Logical Expressions
  /////////////////////////////////////////////////////////////////////////////
  
  def Parens = rule {SkipWS ~ "(" ~ WhereRule ~ ")" ~ SkipWS ~ optional(
      ignoreCase("and") ~ WhereRule ~> And |
      ignoreCase("or") ~ WhereRule ~> Or
   )  
  }
  
  def AndRule: Rule1[WhereExpression] = rule {
    OrRule ~ zeroOrMore(ignoreCase("and") ~ WhereRule ~> And)
  }

  def OrRule: Rule1[WhereExpression] = rule {
    NotRule ~ zeroOrMore(ignoreCase("or") ~ WhereRule ~> Or)
  }
  
  
  // FIXME: Not rule needs to be fixed
  def NotRule: Rule1[WhereExpression] = rule {
    ConditionalRule ~ zeroOrMore(ignoreCase("not") ~ WhereRule ~> Or)
  }

  /////////////////////////////////////////////////////////////////////////////
  // Conditional Expressions
  /////////////////////////////////////////////////////////////////////////////

  def ConditionalRule: Rule1[WhereExpression] = rule { Eq | Ne | Gt | Lt | Ge | Le}
  def Eq = rule { HiPrecMathRule ~ SkipWS ~ "=" ~ SkipWS ~ HiPrecMathRule ~> Equals }
  def Ne = rule { HiPrecMathRule ~ SkipWS ~ "!=" ~ SkipWS ~ HiPrecMathRule ~> NotEquals }
  def Gt = rule { HiPrecMathRule ~ SkipWS ~ ">" ~ SkipWS ~ HiPrecMathRule ~> GreaterThan }
  def Lt = rule { HiPrecMathRule ~ SkipWS ~ "<" ~ SkipWS ~ HiPrecMathRule ~> LessThan }
  def Ge = rule { HiPrecMathRule ~ SkipWS ~ ">=" ~ SkipWS ~ HiPrecMathRule ~> GreaterThanOrEqual }
  def Le = rule { HiPrecMathRule ~ SkipWS ~ "<=" ~ SkipWS ~ HiPrecMathRule ~> LessThanOrEqual }


  /////////////////////////////////////////////////////////////////////////////
  // Mathematical Operator
  /////////////////////////////////////////////////////////////////////////////

  
  def HiPrecMathRule: Rule1[Term] = rule {
    LowPrecMathRule ~ zeroOrMore(
        "*" ~ HiPrecMathRule ~> Multiply |
        "/" ~ HiPrecMathRule ~> Divide |
        "%" ~ HiPrecMathRule ~> Mod
    )
  }
  
  def LowPrecMathRule: Rule1[Term] = rule {
    Value ~ zeroOrMore(
        "+" ~ HiPrecMathRule ~> Add |
        "-" ~ HiPrecMathRule ~> Subtract
    )
  }

  /////////////////////////////////////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////
  
  def Value = rule {
    SkipWS ~ (StringValue | BooleanValue | DoubleValue | LongValue | FieldValue) ~ SkipWS
  }

  def FieldValue = rule {
    SkipWS ~ capture(oneOrMore(!WhiteSpaceChar ~ !Keywords ~ ANY)) ~ SkipWS ~> (FieldExpressionValue(_))
  }

  def StringValue = rule { DoubleQuotedString | SingleQuotedString }

  def DoubleQuotedString = rule {
    DoubleQuote ~
      capture(zeroOrMore(EscapedDoubleQuote | noneOf(DoubleQuote))) ~
      DoubleQuote ~> ((str: String) => StringExpressionValue(str.replace(EscapedDoubleQuote, DoubleQuote)))
  }

  def SingleQuotedString = rule {
    SingleQuote ~
      capture(zeroOrMore(EscapedSingleQuote | noneOf(SingleQuote))) ~
      SingleQuote ~> ((str: String) => StringExpressionValue(str.replace(EscapedSingleQuote, SingleQuote)))
  }

  def LongValue = rule {
    capture(SignedNumber) ~> ((str: String) => LongExpressionValue(str.toLong))
  }

  def DoubleValue = rule {
    capture(SignedNumber ~ FracExp) ~> ((str: String) => DoubleExpressionValue(str.toDouble))
  }

  def SignedNumber = rule { optional(anyOf("+-")) ~ Digits }

  def FracExp = rule(Frac ~ Exp | Frac | Exp)

  def Frac = rule(ch('.') ~ Digits)
  def Exp = rule(Ex ~ Digits)
  def Ex = rule(ignoreCase('e') ~ optional(anyOf("+-")))

  def Digits = rule { oneOrMore(CharPredicate.Digit) }

  def True = rule { ignoreCase("true") ~ push(true) }
  def False = rule { ignoreCase("false") ~ push(false) }

  def BooleanValue = rule { (True | False) ~> (BooleanExpressionValue(_)) }

  def Field = rule { oneOrMore(!WhiteSpaceChar ~ ANY) }

  /////////////////////////////////////////////////////////////////////////////
  // Order By
  /////////////////////////////////////////////////////////////////////////////

  def OrderByRule = rule { OrderByWithDirection | OrderByWithoutDirection }

  def OrderByWithDirection: Rule1[OrderBy] = rule {
    SkipWS ~
      (capture(Field) ~ SkipWS ~
        OrderByDirection) ~> ((field: String, dir: OrderByDirection) => OrderBy(field, Some(dir)))
  }

  def OrderByWithoutDirection: Rule1[OrderBy] = rule {
    SkipWS ~ capture(Field) ~> ((str: String) => OrderBy(str, None))
  }

  def OrderByDirection: Rule1[OrderByDirection] = rule { AscendingRule | DescendingRule }

  def AscendingRule: Rule1[OrderByDirection] = rule {
    (ignoreCase("asc") | ignoreCase("ascending")) ~ push(Ascending)
  }

  def DescendingRule: Rule1[OrderByDirection] = rule {
    (ignoreCase("desc") | ignoreCase("descending")) ~ push(Descending)
  }

  def DoubleQuote = "\""
  def EscapedDoubleQuote = "\\\""

  def SingleQuote = "'"
  def EscapedSingleQuote = "\\'"
  val WhiteSpaceChar = CharPredicate(" \n\r\t\f")

  def SkipWS = rule(zeroOrMore(WhiteSpaceChar))

  // FIXM we need to exclude keywords from certian places like orderby, fields, etc.
  def Keywords = rule { Select | From | Limit | Offset | Where }

  def Select = rule { ignoreCase("select") }
  def Limit = rule { ignoreCase("limit") }
  def Offset = rule { ignoreCase("offset") }
  def Where = rule { ignoreCase("where") }
  def From = rule { ignoreCase("from") }

}

object Test extends App {
  println(new QueryParser("SELECT * FROM files WHERE foo = 'bar' and (baz = 5 + (someField * 8) and age < 64 or ahhh != 'bahhhh')").InputLine.run())
}
