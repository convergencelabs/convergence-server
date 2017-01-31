package com.convergencelabs.server.domain.model.query

import org.parboiled2._

class SelectParser(val input: ParserInput) extends Parser {
  def InputLine = rule { SelectStatement ~ EOI }

  def SelectStatement = rule { (Select ~ Fields ~ From ~ CollectionToken ~ Where ~ WhereTerms) ~> (Ast.SelectStatement(_, _))}

  def Select = rule { ignoreCase("select") }

  def Fields = rule { optional(WhiteSpace ~ "*") }

  def From = rule { WhiteSpace ~ ignoreCase("from") }

  def WhiteSpace = rule { zeroOrMore(WhiteSpaceChar) }

  def CollectionToken = rule { WhiteSpace ~ capture(oneOrMore(!EndCollection ~ ANY)) ~ EndCollection }

  def EndCollection = rule { WhiteSpaceChar }

  def Where = rule { WhiteSpace ~ ignoreCase("where") ~ WhiteSpace }

  def WhereTerms = rule { WhereTerm }

  def WhereTerm = rule { (FieldName ~ WhiteSpaceChar ~ EqualityOperator ~ WhiteSpace ~ Value) ~> (Ast.WhereTerm(_, _, _)) }

  def FieldName = rule { capture(oneOrMore(!WhiteSpaceChar ~ ANY)) }

  def LogicOperator = rule { And | Or }

  def EqualityOperator = rule { Eq | Gt | Lt | Ge | Le }

  // FIXME we need to define numbers, strings, etc...
  def Value = rule { capture(oneOrMore(ANY)) }

  def Eq = rule { capture("=") ~> (_ => Ast.Equals) }
  val Gt = rule { capture(">") ~> (_ => Ast.GreaterThan) }
  val Lt = rule { capture("<") ~> (_ => Ast.LessThan) }
  val Ge = rule { capture(">=") ~> (_ => Ast.GreaterThanOrEqual) }
  val Le = rule { capture("<=") ~> (_ => Ast.LessThanOrEqual) }

  val And = "AND"
  val Or = "OR"
  val WhiteSpaceChar = CharPredicate(" \n\r\t\f")
}

object Ast {
  case class SelectStatement(collection: String, where: WhereTerm)
  case class WhereTerm(field: String, op: EqualityOperator, value: String)

  sealed trait EqualityOperator
  case object Equals extends EqualityOperator
  case object GreaterThan extends EqualityOperator
  case object LessThan extends EqualityOperator
  case object LessThanOrEqual extends EqualityOperator
  case object GreaterThanOrEqual extends EqualityOperator
}

object Test extends App {
  println(new SelectParser("SELECT * FROM files WHERE foo = bar").InputLine.run())
}
