package com.convergencelabs.server.domain.model.query

object Ast {  
  case class SelectStatement( 
    collection: String,  
    where: Option[WhereExpression],  
    orderBy: List[OrderBy],
    limit: Option[Int],  
    offset: Option[Int])  
 
  sealed trait WhereExpression 
 
  sealed trait LogicalExpression extends WhereExpression 
  case class And(lhs: WhereExpression, rhs: WhereExpression) extends LogicalExpression 
  case class Or(lhs: WhereExpression, rhs: WhereExpression) extends LogicalExpression 
  case class Not(conditional: ConditionalExpression) extends LogicalExpression 
 
  sealed trait ConditionalExpression extends WhereExpression 
  case class Equals(lhs: Term, rhs: Term) extends ConditionalExpression  
  case class NotEquals(lhs: Term, rhs: Term) extends ConditionalExpression  
  case class GreaterThan(lhs: Term, rhs: Term) extends ConditionalExpression  
  case class LessThan(lhs: Term, rhs: Term) extends ConditionalExpression  
  case class LessThanOrEqual(lhs: Term, rhs: Term) extends ConditionalExpression  
  case class GreaterThanOrEqual(lhs: Term, rhs: Term) extends ConditionalExpression  
  case class In(field: String, value: List[Any]) extends ConditionalExpression 
  case class Like(field: String, value: String) extends ConditionalExpression 
  
 
  sealed trait Term 
 
  sealed trait ExpressionValue extends Term 
  case class LongExpressionValue(value: Long) extends Term
  case class DoubleExpressionValue(value: Double) extends Term 
  case class StringExpressionValue(value: String) extends Term 
  case class FieldExpressionValue(value: String) extends Term 
  case class BooleanExpressionValue(value: Boolean) extends Term 
 
 
  sealed trait MathematicalOperator extends Term 
  case class Add(lhs: Term, rhs: Term) extends MathematicalOperator 
  case class Subtract(lhs: Term, rhs: Term) extends MathematicalOperator 
  case class Divide(lhs: Term, rhs: Term) extends MathematicalOperator 
  case class Multiply(lhs: Term, rhs: Term) extends MathematicalOperator 
  case class Mod(lhs: Term, rhs: Term) extends MathematicalOperator 
 
 
  case class OrderBy(field: String, direction: Option[OrderByDirection])  
 
  sealed trait OrderByDirection 
  case object Ascending extends OrderByDirection
  case object Descending extends OrderByDirection 
}