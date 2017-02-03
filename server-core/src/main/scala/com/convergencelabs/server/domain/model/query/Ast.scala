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
  case class Not(conditional: WhereExpression) extends LogicalExpression 
 
  sealed trait ConditionalExpression extends WhereExpression 
  case class Equals(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class NotEquals(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class GreaterThan(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class LessThan(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class LessThanOrEqual(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class GreaterThanOrEqual(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class In(field: String, value: List[Any]) extends ConditionalExpression 
  case class Like(field: String, value: String) extends ConditionalExpression 
  
 
  sealed trait ConditionalTerm 
 
  sealed trait ExpressionValue extends ConditionalTerm 
  case class LongExpressionValue(value: Long) extends ExpressionValue
  case class DoubleExpressionValue(value: Double) extends ExpressionValue 
  case class StringExpressionValue(value: String) extends ExpressionValue 
  case class FieldExpressionValue(value: String) extends ExpressionValue 
  case class BooleanExpressionValue(value: Boolean) extends ExpressionValue 
 
 
  sealed trait MathematicalOperator extends ConditionalTerm 
  case class Add(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Subtract(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Divide(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Multiply(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Mod(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
 
 
  case class OrderBy(field: String, direction: Option[OrderByDirection])  
 
  sealed trait OrderByDirection 
  case object Ascending extends OrderByDirection
  case object Descending extends OrderByDirection 
}