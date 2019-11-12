/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.domain.model.query

import com.convergencelabs.server.domain.model.query.Ast._
import org.parboiled2._
import scala.annotation.switch
import scala.util.Try

object QueryParser {
  def main(args: Array[String]): Unit = {
    val parsed = QueryParser("as a123456 ").ProjectionValueName.run().get
    println(parsed)
  }

  def apply(input: ParserInput): QueryParser = {
    new QueryParser(input)
  }

  def parse(input: ParserInput): Try[SelectStatement] = {
    QueryParser(input).InputLine.run().asInstanceOf[Try[SelectStatement]]
  }
}

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
      OffsetSection ~> (SelectStatement(_, _, _, _, _, _))
  }

  def SelectSection = rule { SkipWS ~ Keyword.Select ~ RequireWS }

  def FieldsSection = rule {
    ('*' ~ RequireWS) ~> (() => List[ProjectionTerm]()) |
      SkipWS ~ zeroOrMore(ProjectionValue).separatedBy(",") ~> ((s: Seq[ProjectionTerm]) => s.toList) ~ SkipWS
  }

  def ProjectionValue =
    rule { (FieldValue ~ SkipWS ~ optional(ProjectionValueName)) ~> ((term: FieldTerm, name: Option[String]) => ProjectionTerm(term, name)) }

  def ProjectionValueName: Rule1[String] = rule { Keyword.AS ~ RequireWS ~ FieldName }

  def FromSection = rule { Keyword.From ~ RequireWS }

  def CollectionSection = rule { capture(oneOrMore(!WhiteSpaceChar ~ ANY)) ~ SkipWS }

  def WhereSection: Rule1[Option[WhereExpression]] = rule {
    Keyword.Where ~ RequireWS ~ WhereRule ~> (Some(_)) | (!Keyword.Where ~> (() => push(None)))
  }

  def LimitSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Keyword.Limit ~
      RequireWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) |
      SkipWS ~ !Keyword.Limit ~> (() => push(None))
  }

  def OffsetSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Keyword.Offset ~
      RequireWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) |
      SkipWS ~ !Keyword.Offset ~> (() => push(None))
  }

  def OrderBySection: Rule1[List[OrderBy]] = rule {
    SkipWS ~ Keyword.ORDERBY ~
      RequireWS ~ oneOrMore(OrderByRule).separatedBy(",") ~> ((s: Seq[OrderBy]) => s.toList) |
      SkipWS ~ !Keyword.ORDERBY ~> (() => List[OrderBy]())
  }

  /////////////////////////////////////////////////////////////////////////////
  // Where Expression
  /////////////////////////////////////////////////////////////////////////////

  def WhereRule: Rule1[WhereExpression] = rule { OrRule }

  /////////////////////////////////////////////////////////////////////////////
  // Logical Expressions
  /////////////////////////////////////////////////////////////////////////////

  def OrRule: Rule1[WhereExpression] = rule {
    AndRule ~ zeroOrMore(Keyword.Or ~ AndRule ~> Or)
  }

  def AndRule: Rule1[WhereExpression] = rule {
    LogicalTerms ~ zeroOrMore(Keyword.And ~ LogicalTerms ~> And)
  }

  def LogicalTerms = rule { NotRule | LogicalParens | ConditionalRule }

  def NotRule: Rule1[WhereExpression] = rule {
    Keyword.Not ~ ConditionalRule ~> Not |
      Keyword.Not ~ LogicalParens ~> Not
  }

  def LogicalParens = rule {
    SkipWS ~ "(" ~ SkipWS ~ WhereRule ~ SkipWS ~ ")" ~ SkipWS
  }

  /////////////////////////////////////////////////////////////////////////////
  // Conditional Expressions
  /////////////////////////////////////////////////////////////////////////////

  def ConditionalRule: Rule1[WhereExpression] = rule {
    LowPrecMathRule ~ SkipWS ~ (
      ComparisonOperator.Eq ~ LowPrecMathRule ~> Equals |
      ComparisonOperator.Ne ~ LowPrecMathRule ~> NotEquals |
      ComparisonOperator.Gt ~ LowPrecMathRule ~> GreaterThan |
      ComparisonOperator.Lt ~ LowPrecMathRule ~> LessThan |
      ComparisonOperator.Ge ~ LowPrecMathRule ~> GreaterThanOrEqual |
      ComparisonOperator.Le ~ LowPrecMathRule ~> LessThanOrEqual) |
      LikeRule
  }

  def LikeRule = rule {
    FieldValue ~ ignoreCase(ComparisonOperator.Like) ~ SkipWS ~ StringValue ~> Like
  }

  /////////////////////////////////////////////////////////////////////////////
  // Mathematical Operator
  /////////////////////////////////////////////////////////////////////////////

  def LowPrecMathRule: Rule1[ConditionalTerm] = rule {
    HiPrecMathRule ~ SkipWS ~ zeroOrMore(
      '+' ~ HiPrecMathRule ~> Add |
        '-' ~ HiPrecMathRule ~> Subtract)
  }

  def HiPrecMathRule: Rule1[ConditionalTerm] = rule {
    (MathParens | Value) ~ SkipWS ~ zeroOrMore(
      '*' ~ Value ~> Multiply |
        '/' ~ Value ~> Divide |
        '%' ~ Value ~> Mod)
  }

  def MathParens = rule { SkipWS ~ "(" ~ SkipWS ~ LowPrecMathRule ~ SkipWS ~ ")" ~ SkipWS }

  /////////////////////////////////////////////////////////////////////////////
  /////////////////////////////////////////////////////////////////////////////

  def Value = rule {
    SkipWS ~ (StringValue | BooleanValue | DoubleValue | LongValue | FieldValue) ~ SkipWS
  }

  // FIXME this is not correct since it will include keywords, and also operators like +
  def FieldValue: Rule1[FieldTerm] = rule {
    SkipWS ~
      PropertyPath ~
      zeroOrMore(IndexPath | PropertyPath) ~
      SkipWS ~> ((first: PropertyPathElement, rest: Seq[FieldPathElement]) => {
        FieldTerm(first, rest.toList)
      })
  }

  def IndexPath: Rule1[IndexPathElement] = rule {
    "[" ~ capture(oneOrMore(CharPredicate.Digit)) ~ "]" ~> ((str: String) => IndexPathElement(str.toInt))
  }

  def PropertyPath: Rule1[PropertyPathElement] = rule {
    "." ~ capture(Field) ~> PropertyPathElement | capture(Field) ~> PropertyPathElement
  }

  def StringValue = rule { DoubleQuotedString | SingleQuotedString }

  def DoubleQuotedString = rule {
    DoubleQuote ~
      capture(zeroOrMore(EscapedDoubleQuote | noneOf(DoubleQuote))) ~
      DoubleQuote ~> ((str: String) => StringTerm(str.replace(EscapedDoubleQuote, DoubleQuote)))
  }

  def SingleQuotedString = rule {
    SingleQuote ~
      capture(zeroOrMore(EscapedSingleQuote | noneOf(SingleQuote))) ~
      SingleQuote ~> ((str: String) => StringTerm(str.replace(EscapedSingleQuote, SingleQuote)))
  }

  def LongValue = rule {
    capture(SignedNumber) ~> ((str: String) => LongTerm(str.toLong))
  }

  def DoubleValue = rule {
    capture(SignedNumber ~ FracExp) ~> ((str: String) => DoubleTerm(str.toDouble))
  }

  def SignedNumber = rule { optional(anyOf("+-")) ~ Digits }

  def FracExp = rule(Frac ~ Exp | Frac | Exp)

  def Frac = rule(ch('.') ~ Digits)
  def Exp = rule(Ex ~ Digits)
  def Ex = rule(ignoreCase('e') ~ optional(anyOf("+-")))

  def Digits = rule { oneOrMore(CharPredicate.Digit) }

  def BooleanValue = rule { (True | False) ~> (BooleanTerm(_)) }

  def Field = rule { ('`' ~ oneOrMore(!'`' ~ ANY) ~ '`') | oneOrMore(!'`' ~ !WhiteSpaceChar ~ !Keywords ~ !ComparisonOperators ~ !MathOperators ~ !Symbols ~ ANY) }

  def FieldName: Rule1[String] = rule { ('`' ~ capture(oneOrMore(!'`' ~ ANY)) ~ '`') | (capture(CharPredicate.Alpha ~ oneOrMore(CharPredicate.AlphaNum)) ~ RequireWS) }

  /////////////////////////////////////////////////////////////////////////////
  // Order By
  /////////////////////////////////////////////////////////////////////////////

  def OrderByRule = rule { OrderByWithDirection | OrderByWithoutDirection }

  def OrderByWithDirection: Rule1[OrderBy] = rule {
    SkipWS ~
      (FieldValue ~ SkipWS ~
        OrderByDirection) ~> ((field: FieldTerm, dir: OrderByDirection) => OrderBy(field, Some(dir)))
  }

  def OrderByWithoutDirection: Rule1[OrderBy] = rule {
    SkipWS ~ FieldValue ~> ((field: FieldTerm) => OrderBy(field, None))
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
  def RequireWS = rule(oneOrMore(WhiteSpaceChar))

  /////////////////////////////////////////////////////////////////////////////
  // Constants and Keywords
  /////////////////////////////////////////////////////////////////////////////

  def True = rule { ignoreCase("true") ~ push(true) }
  def False = rule { ignoreCase("false") ~ push(false) }

  object MathOperator {
    val Plus = "+"
    val Minus = "-"
    val Times = "*"
    val Divide = "/"
    val Mod = "%"
  }

  def MathOperators = rule { MathOperator.Plus | MathOperator.Minus | MathOperator.Times | MathOperator.Divide | MathOperator.Mod }

  object ComparisonOperator {
    val Eq = "="
    val Ne = "!="
    val Gt = ">"
    val Lt = "<"
    val Ge = ">="
    val Le = "<="
    val Like = "like"
  }

  def ComparisonOperators = rule { ComparisonOperator.Eq | ComparisonOperator.Ne | ComparisonOperator.Gt | ComparisonOperator.Lt | ComparisonOperator.Ge | ComparisonOperator.Le }

  object Keyword {
    def Select = rule { ignoreCase("select") }
    def Limit = rule { ignoreCase("limit") }
    def Offset = rule { ignoreCase("offset") }
    def Where = rule { ignoreCase("where") }
    def From = rule { ignoreCase("from") }
    def AS = rule { ignoreCase("as") }
    def ORDERBY = rule { ignoreCase("order by") }

    def And = rule { ignoreCase("and") }
    def Or = rule { ignoreCase("or") }
    def Not = rule { ignoreCase("not") }
  }

  object Symbol {
    val LeftParen = "("
    val RightParen = ")"

    val LeftBracket = "["
    val RightBracket = "]"
    val Dot = "."
    val Comma = ","
  }

  def Symbols = rule { Symbol.LeftParen | Symbol.RightParen | Symbol.LeftBracket | Symbol.RightBracket | Symbol.Dot | Symbol.Comma }

  def Keywords = rule { Keyword.Select | Keyword.From | Keyword.Limit | Keyword.Offset | Keyword.Where }
}
