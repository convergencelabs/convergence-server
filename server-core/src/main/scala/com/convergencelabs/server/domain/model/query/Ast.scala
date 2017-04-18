package com.convergencelabs.server.domain.model.query

object Ast {  
  case class SelectStatement(
    fields: List[ProjectionTerm],
    collection: String,  
    where: Option[WhereExpression],  
    orderBy: List[OrderBy],
    limit: Option[Int],  
    offset: Option[Int])  
 
  sealed trait WhereExpression 
 
  sealed trait LogicalExpression extends WhereExpression 
  case class And(lhs: WhereExpression, rhs: WhereExpression) extends LogicalExpression 
  case class Or(lhs: WhereExpression, rhs: WhereExpression) extends LogicalExpression 
  case class Not(exp: WhereExpression) extends LogicalExpression 
 
  sealed trait ConditionalExpression extends WhereExpression 
  case class Equals(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class NotEquals(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class GreaterThan(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class LessThan(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class LessThanOrEqual(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class GreaterThanOrEqual(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression  
  case class In(field: String, value: List[Any]) extends ConditionalExpression 
  case class Like(field: String, value: String) extends ConditionalExpression 
  
  
  case class ProjectionTerm(field: FieldTerm, name: Option[String])
  
 
  sealed trait ConditionalTerm 
 
  sealed trait ValueTerm extends ConditionalTerm 
  case class LongTerm(value: Long) extends ValueTerm
  case class DoubleTerm(value: Double) extends ValueTerm 
  case class StringTerm(value: String) extends ValueTerm 
  case class FieldTerm(field: PropertyPathElement, subpath: List[FieldPathElement] = List()) extends ValueTerm
  case class BooleanTerm(value: Boolean) extends ValueTerm 
  
  sealed trait FieldPathElement
  case class PropertyPathElement(property: String) extends FieldPathElement
  case class IndexPathElement(index: Int) extends FieldPathElement
 
  sealed trait MathematicalOperator extends ConditionalTerm 
  case class Add(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Subtract(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Divide(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Multiply(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
  case class Mod(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator 
 
  case class OrderBy(field: FieldTerm, direction: Option[OrderByDirection])  
 
  sealed trait OrderByDirection 
  case object Ascending extends OrderByDirection
  case object Descending extends OrderByDirection 
}