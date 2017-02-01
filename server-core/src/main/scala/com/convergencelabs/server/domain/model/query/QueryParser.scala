package com.convergencelabs.server.domain.model.query

import org.parboiled2._
import scala.annotation.switch
import javax.swing.text.html.CSS.StringValue

class QueryParser(val input: ParserInput) extends Parser {
  def InputLine = rule { SelectStatement ~ EOI }

  def SelectStatement = rule {
    SelectSection ~ 
    FieldsSection ~ 
    FromSection ~ 
    CollectionSection ~ 
    OrderBySection ~ 
    LimitSection ~ 
    OffsetSection ~> (Ast.SelectStatement(_, None, _, _, _))
  }

  def SelectSection = rule { SkipWS ~ Select ~ SkipWS }

  def FieldsSection = rule { optional(SkipWS ~ "*" ~ SkipWS) }

  def FromSection = rule { SkipWS ~ From ~ SkipWS }

  def CollectionSection = rule { SkipWS ~ capture(oneOrMore(!WhiteSpaceChar ~ ANY)) ~ SkipWS }

  def WhereSection: Rule1[Option[Ast.WhereExpression]] = rule {
    Where ~ WhereExpression ~> (Some(_)) | push(None)
  }

  def LimitSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Limit ~
      SkipWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) | push(None)
  }

  def OffsetSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Offset ~
      SkipWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) | push(None)
  }

  def OrderBySection: Rule1[List[Ast.OrderBy]] = rule {
    ignoreCase("order by") ~ oneOrMore(OrderBy).separatedBy(",") ~> ((s: Seq[Ast.OrderBy]) => s.toList) | push(List())
  }

  /////////////////////////////////////////////////////////////////////////////
  // Where Expression
  /////////////////////////////////////////////////////////////////////////////

  def WhereExpression: Rule1[Ast.WhereExpression] = rule { ConditionalExpression }

  /////////////////////////////////////////////////////////////////////////////
  // Logical Expressions
  /////////////////////////////////////////////////////////////////////////////

  def LogicalExpression: Rule1[Ast.LogicalExpression] = rule { And | Or | Not }
  def And = rule { WhereExpression ~ SkipWS ~ ignoreCase("and") ~ SkipWS ~ WhereExpression ~> (Ast.And(_, _)) }
  def Or = rule { WhereExpression ~ SkipWS ~ ignoreCase("or") ~ SkipWS ~ WhereExpression ~> (Ast.Or(_, _)) }
  def Not = rule {
    ignoreCase("not") ~
      SkipWS ~ "(" ~
      SkipWS ~ ConditionalExpression ~
      SkipWS ~ ")" ~> (Ast.Not(_))
  }

  /////////////////////////////////////////////////////////////////////////////
  // Conditional Expressions
  /////////////////////////////////////////////////////////////////////////////

  def ConditionalExpression: Rule1[Ast.ConditionalExpression] = rule { Eq | Ne | Gt | Lt | Ge | Le }
  def Eq = rule { Term ~ SkipWS ~ "=" ~ SkipWS ~ Term ~> (Ast.Equals(_, _)) }
  def Ne = rule { Term ~ SkipWS ~ "!=" ~ SkipWS ~ Term ~> (Ast.NotEquals(_, _)) }
  def Gt = rule { Term ~ SkipWS ~ ">" ~ SkipWS ~ Term ~> (Ast.GreaterThan(_, _)) }
  def Lt = rule { Term ~ SkipWS ~ "<" ~ SkipWS ~ Term ~> (Ast.LessThan(_, _)) }
  def Ge = rule { Term ~ SkipWS ~ ">=" ~ SkipWS ~ Term ~> (Ast.GreaterThanOrEqual(_, _)) }
  def Le = rule { Term ~ SkipWS ~ "<=" ~ SkipWS ~ Term ~> (Ast.LessThanOrEqual(_, _)) }

  /////////////////////////////////////////////////////////////////////////////
  // Terms
  /////////////////////////////////////////////////////////////////////////////

  def Term: Rule1[Ast.Term] = rule { MathematicalOperator | Value}

  /////////////////////////////////////////////////////////////////////////////
  // Mathematical Operator
  /////////////////////////////////////////////////////////////////////////////

  def MathematicalOperator: Rule1[Ast.MathematicalOperator] = rule { Add | Subtract | Multiply | Divide | Mod }
  def Add = rule { Term ~ SkipWS ~ "+" ~ SkipWS ~ Term ~> (Ast.Add(_, _)) }
  def Subtract = rule { Term ~ SkipWS ~ "-" ~ SkipWS ~ Term ~> (Ast.Subtract(_, _)) }
  def Multiply = rule { Term ~ SkipWS ~ "*" ~ SkipWS ~ Term ~> (Ast.Multiply(_, _)) }
  def Divide = rule { Term ~ SkipWS ~ "/" ~ SkipWS ~ Term ~> (Ast.Divide(_, _)) }
  def Mod = rule { Term ~ SkipWS ~ "%" ~ SkipWS ~ Term ~> (Ast.Mod(_, _)) }

  /////////////////////////////////////////////////////////////////////////////
  // Values
  /////////////////////////////////////////////////////////////////////////////
  def Value = rule {
    SkipWS ~ (StringValue | BooleanValue |DoubleValue | LongValue | FieldValue) ~ SkipWS
  }
  
  def FieldValue = rule {
    SkipWS ~ capture(oneOrMore(!WhiteSpaceChar ~ !Keywords ~ ANY)) ~ SkipWS ~> (Ast.FieldExpressionValue(_))
  }

  def StringValue = rule { DoubleQuotedString | SingleQuotedString }

  def DoubleQuotedString = rule {
    DoubleQuote ~
      capture(zeroOrMore(EscapedDoubleQuote | noneOf(DoubleQuote))) ~
      DoubleQuote ~> ((str: String) => Ast.StringExpressionValue(str.replace(EscapedDoubleQuote, DoubleQuote)))
  }

  def SingleQuotedString = rule {
    SingleQuote ~
      capture(zeroOrMore(EscapedSingleQuote | noneOf(SingleQuote))) ~
      SingleQuote ~> ((str: String) => Ast.StringExpressionValue(str.replace(EscapedSingleQuote, SingleQuote)))
  }

  def LongValue = rule {
    capture(SignedNumber) ~> ((str: String) => Ast.LongExpressionValue(str.toLong))
  }

  def DoubleValue = rule {
    capture(SignedNumber ~ FracExp) ~> ((str: String) => Ast.DoubleExpressionValue(str.toDouble))
  }

  def SignedNumber = rule { optional(anyOf("+-")) ~ Digits }

  def FracExp = rule(Frac ~ Exp | Frac | Exp)

  def Frac = rule(ch('.') ~ Digits)
  def Exp = rule(Ex ~ Digits)
  def Ex = rule(ignoreCase('e') ~ optional(anyOf("+-")))

  def Digits = rule { oneOrMore(CharPredicate.Digit) }

  def True = rule { ignoreCase("true") ~ push(true) }
  def False = rule { ignoreCase("false") ~ push(false) }

  def BooleanValue = rule { (True | False) ~> (Ast.BooleanExpressionValue(_)) }

  def Field = rule { oneOrMore(!WhiteSpaceChar ~ ANY) }

  /////////////////////////////////////////////////////////////////////////////
  // Order By
  /////////////////////////////////////////////////////////////////////////////

  def OrderBy = rule { OrderByWithDirection | OrderByWithoutDirection }

  def OrderByWithDirection: Rule1[Ast.OrderBy] = rule {
    SkipWS ~
      (capture(Field) ~ SkipWS ~
        OrderByDirection) ~> ((field: String, dir: Ast.OrderByDirection) => Ast.OrderBy(field, Some(dir)))
  }

  def OrderByWithoutDirection: Rule1[Ast.OrderBy] = rule {
    SkipWS ~ capture(Field) ~> ((str: String) => Ast.OrderBy(str, None))
  }

  def OrderByDirection: Rule1[Ast.OrderByDirection] = rule { Ascending | Descending }

  def Ascending: Rule1[Ast.OrderByDirection] = rule {
    (ignoreCase("asc") | ignoreCase("ascending")) ~ push(Ast.Ascending)
  }

  def Descending: Rule1[Ast.OrderByDirection] = rule {
    (ignoreCase("desc") | ignoreCase("descending")) ~ push(Ast.Descending)
  }

  def DoubleQuote = "\""
  def EscapedDoubleQuote = "\\\""

  def SingleQuote = "'"
  def EscapedSingleQuote = "\\'"
  val WhiteSpaceChar = CharPredicate(" \n\r\t\f")

  def SkipWS = rule(zeroOrMore(WhiteSpaceChar))
  
  // FIXM we need to exclude keywords from certian places like orderby, fields, etc.
  def Keywords = rule {Select | From | Limit | Offset | Where}
  
  def Select = rule { ignoreCase("select") }
  def Limit = rule { ignoreCase("limit") }
  def Offset = rule { ignoreCase("offset") }
  def Where = rule { ignoreCase("where") }
  def From = rule { ignoreCase("from") }

}

object Test extends App {
  //  println(new QueryParser("SELECT * FROM files WHERE foo = bar").InputLine.run())
}
