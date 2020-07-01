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

package com.convergencelabs.convergence.server.backend.datastore.domain.model.query

import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

object Ast {

  final case class SelectStatement(fields: List[ProjectionTerm],
                                   collection: String,
                                   where: Option[WhereExpression],
                                   orderBy: List[OrderBy],
                                   limit: QueryLimit,
                                   offset: QueryOffset)

  sealed trait WhereExpression

  sealed trait LogicalExpression extends WhereExpression

  final case class And(lhs: WhereExpression, rhs: WhereExpression) extends LogicalExpression

  final case class Or(lhs: WhereExpression, rhs: WhereExpression) extends LogicalExpression

  final case class Not(exp: WhereExpression) extends LogicalExpression

  sealed trait ConditionalExpression extends WhereExpression

  final case class Equals(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression

  final case class NotEquals(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression

  final case class GreaterThan(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression

  final case class LessThan(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression

  final case class LessThanOrEqual(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression

  final case class GreaterThanOrEqual(lhs: ConditionalTerm, rhs: ConditionalTerm) extends ConditionalExpression

  final case class In(field: FieldTerm, value: List[Any]) extends ConditionalExpression

  final case class Like(field: FieldTerm, value: StringTerm) extends ConditionalExpression


  final case class ProjectionTerm(field: FieldTerm, name: Option[String])


  sealed trait ConditionalTerm

  sealed trait ValueTerm extends ConditionalTerm

  final case class LongTerm(value: Long) extends ValueTerm

  final case class DoubleTerm(value: Double) extends ValueTerm

  final case class StringTerm(value: String) extends ValueTerm

  final case class FieldTerm(field: PropertyPathElement, subpath: List[FieldPathElement] = List()) extends ValueTerm

  final case class BooleanTerm(value: Boolean) extends ValueTerm

  sealed trait FieldPathElement

  final case class PropertyPathElement(property: String) extends FieldPathElement

  final case class IndexPathElement(index: Int) extends FieldPathElement

  sealed trait MathematicalOperator extends ConditionalTerm

  final case class Add(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator

  final case class Subtract(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator

  final case class Divide(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator

  final case class Multiply(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator

  final case class Mod(lhs: ConditionalTerm, rhs: ConditionalTerm) extends MathematicalOperator

  final case class OrderBy(field: FieldTerm, direction: Option[OrderByDirection])

  sealed trait OrderByDirection

  final case object Ascending extends OrderByDirection

  final case object Descending extends OrderByDirection

}