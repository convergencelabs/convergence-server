/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is subject to the terms and conditions defined in
 * file 'LICENSE', which is part of this source code package.
 */

package com.convergencelabs.server.datastore.domain

import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.collection.mutable.{ Map => ScalaMutableMap }

import com.convergencelabs.server.datastore.OrientDBUtil
import com.convergencelabs.server.datastore.domain.schema.DomainSchema
import com.convergencelabs.server.domain.model.query.Ast.Add
import com.convergencelabs.server.domain.model.query.Ast.And
import com.convergencelabs.server.domain.model.query.Ast.Ascending
import com.convergencelabs.server.domain.model.query.Ast.BooleanTerm
import com.convergencelabs.server.domain.model.query.Ast.ConditionalExpression
import com.convergencelabs.server.domain.model.query.Ast.ConditionalTerm
import com.convergencelabs.server.domain.model.query.Ast.Descending
import com.convergencelabs.server.domain.model.query.Ast.Divide
import com.convergencelabs.server.domain.model.query.Ast.DoubleTerm
import com.convergencelabs.server.domain.model.query.Ast.Equals
import com.convergencelabs.server.domain.model.query.Ast.FieldTerm
import com.convergencelabs.server.domain.model.query.Ast.GreaterThan
import com.convergencelabs.server.domain.model.query.Ast.GreaterThanOrEqual
import com.convergencelabs.server.domain.model.query.Ast.In
import com.convergencelabs.server.domain.model.query.Ast.IndexPathElement
import com.convergencelabs.server.domain.model.query.Ast.LessThan
import com.convergencelabs.server.domain.model.query.Ast.LessThanOrEqual
import com.convergencelabs.server.domain.model.query.Ast.Like
import com.convergencelabs.server.domain.model.query.Ast.LogicalExpression
import com.convergencelabs.server.domain.model.query.Ast.LongTerm
import com.convergencelabs.server.domain.model.query.Ast.MathematicalOperator
import com.convergencelabs.server.domain.model.query.Ast.Mod
import com.convergencelabs.server.domain.model.query.Ast.Multiply
import com.convergencelabs.server.domain.model.query.Ast.Not
import com.convergencelabs.server.domain.model.query.Ast.NotEquals
import com.convergencelabs.server.domain.model.query.Ast.Or
import com.convergencelabs.server.domain.model.query.Ast.PropertyPathElement
import com.convergencelabs.server.domain.model.query.Ast.SelectStatement
import com.convergencelabs.server.domain.model.query.Ast.StringTerm
import com.convergencelabs.server.domain.model.query.Ast.Subtract
import com.convergencelabs.server.domain.model.query.Ast.ValueTerm
import com.convergencelabs.server.domain.model.query.Ast.WhereExpression
import com.convergencelabs.server.domain.DomainUserId

case class ModelQueryParameters(query: String, params: Map[String, Any], as: Map[String, String])

object ModelQueryBuilder {

  def queryModels(select: SelectStatement, userId: Option[DomainUserId]): ModelQueryParameters = {
    // TODO use a let to query for the user first.
    implicit val params = ScalaMutableMap[String, Any]()
    implicit val as = ScalaMutableMap[String, String]()
    
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

    val selectString = this.buildSelectString(select, projectionString)
    val whereString = this.buildWhereString(select)
    val permissionString = this.buildPermissionsString(userId)
    val orderString: String = this.buildOrderString(select)

    val queryString = s"${selectString}${whereString}${permissionString}${orderString}"

    ModelQueryParameters(OrientDBUtil.buildPagedQuery(queryString, select.limit, select.offset), params.toMap, as.toMap)
  }

  def countModels(select: SelectStatement, userId: Option[DomainUserId]): ModelQueryParameters = {
    // TODO use a let to query for the user first.
    implicit val params = ScalaMutableMap[String, Any]()

    val projectionString = "count(*) as count "
    val selectString = this.buildSelectString(select, projectionString)
    val whereString = this.buildWhereString(select)
    val permissionString = this.buildPermissionsString(userId)
    val orderString: String = this.buildOrderString(select)

    val queryString = s"${selectString}${whereString}${permissionString}${orderString}"

    ModelQueryParameters(OrientDBUtil.buildPagedQuery(queryString, None, None), params.toMap, Map())
  }

  private[this] def buildSelectString(select: SelectStatement, projectionString: String)(
    implicit
    params: ScalaMutableMap[String, Any]): String = {

    s"SELECT ${projectionString}FROM Model WHERE ${DomainSchema.Classes.Model.Fields.Collection}.${DomainSchema.Classes.Collection.Fields.Id} = ${addParam(select.collection)}"
  }

  private[this] def buildWhereString(select: SelectStatement)(implicit params: ScalaMutableMap[String, Any]): String = {
    (select.where map { where =>
      s" AND ${buildExpressionString(where)}"
    }) getOrElse ("")
  }

  private[this] def buildPermissionsString(userId: Option[DomainUserId])(implicit params: ScalaMutableMap[String, Any]): String = {
    userId.map { uid =>
      val usernameParam = addParam(uid.username)
      val userTypeParam = addParam(uid.userType.toString.toLowerCase)
      s""" AND ((overridePermissions = true AND ((userPermissions CONTAINS ((user.username = $usernameParam AND user.userType = $userTypeParam AND permissions.read = true))) OR
                    (not(userPermissions CONTAINS (user.username = $usernameParam AND user.userType = $userTypeParam )) AND worldPermissions.read = true))) OR 
	               (overridePermissions = false AND ((collection.userPermissions CONTAINS ((user.username = $usernameParam AND user.userType = $userTypeParam AND permissions.read = true))) OR
                    (NOT(collection.userPermissions CONTAINS (user.username = $usernameParam AND user.userType = $userTypeParam )) AND collection.worldPermissions.read = true))))"""

    }.getOrElse("")
  }

  private[this] def buildOrderString(select: SelectStatement): String = {
    if (select.orderBy.isEmpty) {
      ""
    } else {
      " ORDER BY " + (select.orderBy map { orderBy =>
        val ascendingParam = orderBy.direction map {
          case Ascending  => "ASC"
          case Descending => "DESC"
        } getOrElse ("ASC")
        s"${buildFieldPath(orderBy.field)} ${ascendingParam}"
      }).mkString(", ")
    }
  }

  private[this] def buildFieldPath(field: FieldTerm): String = {
    s"${buildProjectionPath(field)}.value"
  }

  private[this] def buildProjectionPath(field: FieldTerm): String = {
    val sb = new StringBuilder()
    sb.append("data.children.`")
    sb.append(field.field.property)
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

  private[this] def buildExpressionString(where: WhereExpression)(implicit params: ScalaMutableMap[String, Any]): String = {
    where match {
      case expression: LogicalExpression     => buildLogicalExpressionString(expression)
      case expression: ConditionalExpression => buildConditionalExpressionString(expression)
    }
  }

  private[this] def buildLogicalExpressionString(expression: LogicalExpression)(implicit params: ScalaMutableMap[String, Any]): String = {
    expression match {
      case And(lhs, rhs)   => s"(${buildExpressionString(lhs)} and ${buildExpressionString(rhs)})"
      case Or(lhs, rhs)    => s"(${buildExpressionString(lhs)} or ${buildExpressionString(rhs)})"
      case Not(expression) => s"not(${buildExpressionString(expression)})"
    }
  }

  private[this] def buildConditionalExpressionString(expression: ConditionalExpression)(implicit params: ScalaMutableMap[String, Any]): String = {
    expression match {
      case Equals(lhs, rhs)               => s"(${buildTermString(lhs)} = ${buildTermString(rhs)})"
      case NotEquals(lhs, rhs)            => s"(${buildTermString(lhs)} != ${buildTermString(rhs)})"
      case GreaterThan(lhs, rhs)          => s"(${buildTermString(lhs)} > ${buildTermString(rhs)})"
      case LessThan(lhs, rhs)             => s"(${buildTermString(lhs)} < ${buildTermString(rhs)})"
      case LessThanOrEqual(lhs, rhs)      => s"(${buildTermString(lhs)} <= ${buildTermString(rhs)})"
      case GreaterThanOrEqual(lhs, rhs)   => s"(${buildTermString(lhs)} >= ${buildTermString(rhs)})"
      case In(field, value)               => s"(${buildFieldPath(field)} in ${addParam(value.asJava)})"
      case Like(field, StringTerm(value)) => s"(${buildFieldPath(field)} like ${addParam(value)})"
    }
  }

  private[this] def buildTermString(term: ConditionalTerm)(implicit param: ScalaMutableMap[String, Any]): String = {
    term match {
      case expression: ValueTerm            => buildExpressionValueString(expression)
      case expression: MathematicalOperator => buildMathmaticalExpressionString(expression)
    }
  }

  private[this] def buildExpressionValueString(valueTerm: ValueTerm)(implicit params: ScalaMutableMap[String, Any]): String = {
    valueTerm match {
      case LongTerm(value)    => s"${addParam(value)}"
      case DoubleTerm(value)  => s"${addParam(value)}"
      case StringTerm(value)  => s"${addParam(value)}"
      case BooleanTerm(value) => s"${addParam(value)}"
      case f: FieldTerm       => buildFieldPath(f)
    }
  }

  private[this] def buildMathmaticalExpressionString(expression: MathematicalOperator)(implicit params: ScalaMutableMap[String, Any]): String = {
    expression match {
      case Add(lhs, rhs)      => s"(${buildTermString(lhs)} + ${buildTermString(rhs)})"
      case Subtract(lhs, rhs) => s"(${buildTermString(lhs)} - ${buildTermString(rhs)})"
      case Divide(lhs, rhs)   => s"(${buildTermString(lhs)} / ${buildTermString(rhs)})"
      case Multiply(lhs, rhs) => s"(${buildTermString(lhs)} * ${buildTermString(rhs)})"
      case Mod(lhs, rhs)      => s"(${buildTermString(lhs)} % ${buildTermString(rhs)})"
    }
  }

  private def addParam(value: Any)(implicit params: ScalaMutableMap[String, Any]): String = {
    val param = s"p${params.size}"
    params += param -> value
    s":$param"
  }

  private def addAs(value: String)(implicit as: ScalaMutableMap[String, String]): String = {
    val a = s"a${as.size}"
    as += a -> value
    a
  }
}
