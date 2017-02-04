package com.convergencelabs.server.domain.model.query

import com.convergencelabs.server.domain.model.query.Ast._
import org.parboiled2._
import scala.annotation.switch
import javax.swing.text.html.CSS.StringValue
import scala.util.Try

object QueryParser {
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
      OffsetSection ~> (SelectStatement(_, _, _, _, _))
  }

  def SelectSection = rule { SkipWS ~ Keyword.Select ~ SkipWS }

  def FieldsSection = rule { optional(SkipWS ~ "*" ~ SkipWS) }

  def FromSection = rule { SkipWS ~ Keyword.From ~ SkipWS }

  def CollectionSection = rule { SkipWS ~ capture(oneOrMore(!WhiteSpaceChar ~ ANY)) ~ SkipWS }

  def WhereSection: Rule1[Option[WhereExpression]] = rule {
    Keyword.Where ~ WhereRule ~> (Some(_)) | push(None)
  }

  def LimitSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Keyword.Limit ~
      SkipWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) | push(None)
  }

  def OffsetSection: Rule1[Option[Int]] = rule {
    SkipWS ~ Keyword.Offset ~
      SkipWS ~ capture(oneOrMore(CharPredicate.Digit)) ~> ((str: String) => Some(str.toInt)) | push(None)
  }

  def OrderBySection: Rule1[List[OrderBy]] = rule {
    ignoreCase("order by") ~ oneOrMore(OrderByRule).separatedBy(",") ~> ((s: Seq[OrderBy]) => s.toList) | push(List())
  }

  /////////////////////////////////////////////////////////////////////////////
  // Where Expression
  /////////////////////////////////////////////////////////////////////////////

  def WhereRule: Rule1[WhereExpression] = rule { OrRule }

  /////////////////////////////////////////////////////////////////////////////
  // Logical Expressions
  /////////////////////////////////////////////////////////////////////////////

  def OrRule: Rule1[WhereExpression] = rule {
    AndRule ~ zeroOrMore(ignoreCase("or") ~ AndRule ~> Or)
  }

  def AndRule: Rule1[WhereExpression] = rule {
    LogicalTerms ~ zeroOrMore(ignoreCase("and") ~ LogicalTerms ~> And)
  }

  def LogicalTerms = rule { NotRule | LogicalParens | ConditionalRule }

  def NotRule: Rule1[WhereExpression] = rule {
    ignoreCase("not") ~ WhereRule ~> Not
  }

  def LogicalParens = rule {
    SkipWS ~ "(" ~ SkipWS ~ WhereRule ~ SkipWS ~ ")" ~ SkipWS
  }

  /////////////////////////////////////////////////////////////////////////////
  // Conditional Expressions
  /////////////////////////////////////////////////////////////////////////////

  def ConditionalRule: Rule1[WhereExpression] = rule {
    LowPrecMathRule ~ SkipWS ~ (
      "=" ~ LowPrecMathRule ~> Equals |
      "!=" ~ LowPrecMathRule ~> NotEquals |
      ">" ~ LowPrecMathRule ~> GreaterThan |
      "<" ~ LowPrecMathRule ~> LessThan |
      ">=" ~ LowPrecMathRule ~> GreaterThanOrEqual |
      "<=" ~ LowPrecMathRule ~> LessThanOrEqual)
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
        '×' ~ Value ~> Multiply |
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
  def FieldValue = rule {
    SkipWS ~ capture(Field) ~ SkipWS ~> FieldTerm
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

  def Field = rule { oneOrMore(!WhiteSpaceChar ~ !Keywords ~ !ComparisonOperators ~ !MathOperators ~ ANY) }

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
  }
  
  def ComparisonOperators = rule { ComparisonOperator.Eq | ComparisonOperator.Ne | ComparisonOperator.Gt | ComparisonOperator.Lt | ComparisonOperator.Ge | ComparisonOperator.Le }
  
  object Keyword {
    def Select = rule { ignoreCase("select") }
    def Limit = rule { ignoreCase("limit") }
    def Offset = rule { ignoreCase("offset") }
    def Where = rule { ignoreCase("where") }
    def From = rule { ignoreCase("from") }
    
    def And = rule { ignoreCase("and") }
    def Or = rule { ignoreCase("or") }
    def Not = rule { ignoreCase("not") }
  }
  
  def Keywords = rule { Keyword.Select | Keyword.From | Keyword.Limit | Keyword.Offset | Keyword.Where }
}
