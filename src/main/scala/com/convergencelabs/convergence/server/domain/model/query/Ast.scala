/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.domain.model.query

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
  case class In(field: FieldTerm, value: List[Any]) extends ConditionalExpression 
  case class Like(field: FieldTerm, value: StringTerm) extends ConditionalExpression 
  
  
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