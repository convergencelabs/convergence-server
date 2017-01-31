package com.convergencelabs.server.domain.model.query

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
