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

import com.convergencelabs.convergence.server.datastore.OrientDBUtil
import com.convergencelabs.convergence.server.datastore.domain.schema.DomainSchema
import com.convergencelabs.convergence.server.domain.DomainUserId
import com.convergencelabs.convergence.server.domain.model.query.Ast._
import com.convergencelabs.convergence.server.util.{QueryLimit, QueryOffset}

import scala.collection.mutable
import scala.jdk.CollectionConverters._

case class ModelQueryParameters(query: String, params: Map[String, Any], as: Map[String, String])

object ModelQueryBuilder {

  private[this] val ReservedFields = Map[String, String](
    "@id" -> "id",
    "@version" -> "version"
  )

  def queryModels(select: SelectStatement, userId: Option[DomainUserId]): ModelQueryParameters = {
    // TODO use a let to query for the user first.
    implicit val params: mutable.Map[String, Any] = mutable.Map[String, Any]()
    implicit val as: mutable.Map[String, String] = mutable.Map[String, String]()

    val projectionString =
      if (select.fields.isEmpty) {
        ""
      } else {
        val sb = new StringBuilder()
        sb.append("collection.id as collectionId, ")
        sb.append("id, ")
        sb.append("version, ")
        sb.append("createdTime, ")
        sb.append("modifiedTime, ")

        sb.append((select.fields map {
          term =>
            val fieldPath = buildProjectionPath(term.field)
            s"$fieldPath as ${addAs(term.name.getOrElse(buildFieldName(term.field)))}"
        }).mkString(", "))
        sb.append(" ")
        sb.toString()
      }

    val queryString = buildSelectString(select, userId, projectionString, params)
    ModelQueryParameters(OrientDBUtil.buildPagedQuery(queryString, select.limit, select.offset), params.toMap, as.toMap)
  }

  def countModels(select: SelectStatement, userId: Option[DomainUserId]): ModelQueryParameters = {
    // TODO use a let to query for the user first.
    implicit val params: mutable.Map[String, Any] = mutable.Map[String, Any]()

    val projectionString = "count(*) as count "
    val queryString = buildSelectString(select, userId, projectionString, params)
    ModelQueryParameters(OrientDBUtil.buildPagedQuery(queryString, QueryLimit(), QueryOffset()), params.toMap, Map())
  }

  private[this] def buildSelectString(select: SelectStatement, userId: Option[DomainUserId], projectionString: String, params: mutable.Map[String, Any]): String = {
    val selectString = this.buildSelectString(select, projectionString)(params)
    val whereString = this.buildWhereString(select)(params)
    val permissionString = this.buildPermissionsString(userId)(params)
    val orderString: String = this.buildOrderString(select)
    s"$selectString$whereString$permissionString$orderString"
  }

  private[this] def buildSelectString(select: SelectStatement, projectionString: String)(
    implicit
    params: mutable.Map[String, Any]): String = {

    s"SELECT ${projectionString}FROM Model WHERE ${DomainSchema.Classes.Model.Fields.Collection}.${DomainSchema.Classes.Collection.Fields.Id} = ${addParam(select.collection)}"
  }

  private[this] def buildWhereString(select: SelectStatement)(implicit params: mutable.Map[String, Any]): String = {
    (select.where map { where =>
      s" AND ${buildExpressionString(where)}"
    }) getOrElse ""
  }

  private[this] def buildPermissionsString(userId: Option[DomainUserId])(implicit params: mutable.Map[String, Any]): String = {
    userId.map { uid =>
      val usernameParam = addParam(uid.username)
      val userTypeParam = addParam(uid.userType.toString.toLowerCase)

      s""" AND (
         |  (overridePermissions = false AND collection.worldPermissions.read = true) OR
         |  (overridePermissions = true AND worldPermissions.read = true) OR
         |  userPermissions CONTAINS (user.username = $usernameParam AND user.userType = $userTypeParam) OR
         |  collection.userPermissions CONTAINS (user.username = $usernameParam AND user.userType = $userTypeParam)
         |)""".stripMargin
    } getOrElse ""
  }

  private[this] def buildOrderString(select: SelectStatement): String = {
    if (select.orderBy.isEmpty) {
      ""
    } else {
      " ORDER BY " + (select.orderBy map { orderBy =>
        val ascendingParam = orderBy.direction map {
          case Ascending => "ASC"
          case Descending => "DESC"
        } getOrElse "ASC"
        s"${buildFieldPath(orderBy.field)} $ascendingParam"
      }).mkString(", ")
    }
  }

  private[this] def buildFieldPath(field: FieldTerm): String = {
    if (ReservedFields.contains(field.field.property) && field.subpath.isEmpty) {
      this.ReservedFields(field.field.property)
    } else {
      s"${buildProjectionPath(field)}.value"
    }
  }

  private[this] def buildProjectionPath(field: FieldTerm): String = {
    val sb = new StringBuilder()
    sb.append("data.children.`")

    val property = if (field.field.property.startsWith("@@")) {
      field.field.property.substring(1)
    } else {
      field.field.property
    }

    sb.append(property)
    sb.append("`")
    field.subpath.foreach {
      case IndexPathElement(i) =>
        sb.append(".children").append("[").append(i.toString).append("]")
      case PropertyPathElement(p) =>
        sb.append(".children").append(".`").append(p).append("`")
    }

    sb.toString
  }

  private[this] def buildFieldName(field: FieldTerm): String = {
    val sb = new StringBuilder()
    sb.append(field.field.property)
    field.subpath.foreach {
      case IndexPathElement(i) =>
        sb.append("[").append(i.toString).append("]")
      case PropertyPathElement(p) =>
        sb.append(".").append(p)
    }
    sb.toString
  }

  private[this] def buildExpressionString(where: WhereExpression)(implicit params: mutable.Map[String, Any]): String = {
    where match {
      case expression: LogicalExpression => buildLogicalExpressionString(expression)
      case expression: ConditionalExpression => buildConditionalExpressionString(expression)
    }
  }

  private[this] def buildLogicalExpressionString(expression: LogicalExpression)(implicit params: mutable.Map[String, Any]): String = {
    expression match {
      case And(lhs, rhs) => s"(${buildExpressionString(lhs)} and ${buildExpressionString(rhs)})"
      case Or(lhs, rhs) => s"(${buildExpressionString(lhs)} or ${buildExpressionString(rhs)})"
      case Not(expression) => s"not(${buildExpressionString(expression)})"
    }
  }

  private[this] def buildConditionalExpressionString(expression: ConditionalExpression)(implicit params: mutable.Map[String, Any]): String = {
    expression match {
      case Equals(lhs, rhs) => s"(${buildTermString(lhs)} = ${buildTermString(rhs)})"
      case NotEquals(lhs, rhs) => s"(${buildTermString(lhs)} != ${buildTermString(rhs)})"
      case GreaterThan(lhs, rhs) => s"(${buildTermString(lhs)} > ${buildTermString(rhs)})"
      case LessThan(lhs, rhs) => s"(${buildTermString(lhs)} < ${buildTermString(rhs)})"
      case LessThanOrEqual(lhs, rhs) => s"(${buildTermString(lhs)} <= ${buildTermString(rhs)})"
      case GreaterThanOrEqual(lhs, rhs) => s"(${buildTermString(lhs)} >= ${buildTermString(rhs)})"
      case In(field, value) => s"(${buildFieldPath(field)} in ${addParam(value.asJava)})"
      case Like(field, StringTerm(value)) => s"(${buildFieldPath(field)} like ${addParam(value)})"
    }
  }

  private[this] def buildTermString(term: ConditionalTerm)(implicit param: mutable.Map[String, Any]): String = {
    term match {
      case expression: ValueTerm => buildExpressionValueString(expression)
      case expression: MathematicalOperator => buildMathmaticalExpressionString(expression)
    }
  }

  private[this] def buildExpressionValueString(valueTerm: ValueTerm)(implicit params: mutable.Map[String, Any]): String = {
    valueTerm match {
      case LongTerm(value) => s"${addParam(value)}"
      case DoubleTerm(value) => s"${addParam(value)}"
      case StringTerm(value) => s"${addParam(value)}"
      case BooleanTerm(value) => s"${addParam(value)}"
      case f: FieldTerm => buildFieldPath(f)
    }
  }

  private[this] def buildMathmaticalExpressionString(expression: MathematicalOperator)(implicit params: mutable.Map[String, Any]): String = {
    expression match {
      case Add(lhs, rhs) => s"(${buildTermString(lhs)} + ${buildTermString(rhs)})"
      case Subtract(lhs, rhs) => s"(${buildTermString(lhs)} - ${buildTermString(rhs)})"
      case Divide(lhs, rhs) => s"(${buildTermString(lhs)} / ${buildTermString(rhs)})"
      case Multiply(lhs, rhs) => s"(${buildTermString(lhs)} * ${buildTermString(rhs)})"
      case Mod(lhs, rhs) => s"(${buildTermString(lhs)} % ${buildTermString(rhs)})"
    }
  }

  private def addParam(value: Any)(implicit params: mutable.Map[String, Any]): String = {
    val param = s"p${params.size}"
    params += param -> value
    s":$param"
  }

  private def addAs(value: String)(implicit as: mutable.Map[String, String]): String = {
    val a = s"a${as.size}"
    as += a -> value
    a
  }
}
