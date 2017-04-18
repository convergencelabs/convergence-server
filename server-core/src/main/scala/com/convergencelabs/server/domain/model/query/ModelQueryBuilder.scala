package com.convergencelabs.server.datastore.domain

import scala.collection.mutable.{ Map => ScalaMutableMap }
import scala.collection.JavaConverters.seqAsJavaListConverter

import ModelStore.Constants.CollectionId
import ModelStore.Fields.Collection

import com.convergencelabs.server.domain.model.query.Ast.SelectStatement
import com.convergencelabs.server.domain.model.query.Ast.WhereExpression
import com.convergencelabs.server.domain.model.query.Ast.OrderBy
import com.convergencelabs.server.domain.model.query.Ast.Ascending
import com.convergencelabs.server.domain.model.query.Ast.Descending
import com.convergencelabs.server.domain.model.query.Ast.LogicalExpression
import com.convergencelabs.server.domain.model.query.Ast.ConditionalExpression
import com.convergencelabs.server.domain.model.query.Ast.And
import com.convergencelabs.server.domain.model.query.Ast.Or
import com.convergencelabs.server.domain.model.query.Ast.Not
import com.convergencelabs.server.domain.model.query.Ast.Equals
import com.convergencelabs.server.domain.model.query.Ast.NotEquals
import com.convergencelabs.server.domain.model.query.Ast.GreaterThan
import com.convergencelabs.server.domain.model.query.Ast.LessThan
import com.convergencelabs.server.domain.model.query.Ast.LessThanOrEqual
import com.convergencelabs.server.domain.model.query.Ast.GreaterThanOrEqual
import com.convergencelabs.server.domain.model.query.Ast.In
import com.convergencelabs.server.domain.model.query.Ast.Like
import com.convergencelabs.server.domain.model.query.Ast.ConditionalTerm
import com.convergencelabs.server.domain.model.query.Ast.MathematicalOperator
import com.convergencelabs.server.domain.model.query.Ast.DoubleTerm
import com.convergencelabs.server.domain.model.query.Ast.FieldTerm
import com.convergencelabs.server.domain.model.query.Ast.LongTerm
import com.convergencelabs.server.domain.model.query.Ast.StringTerm
import com.convergencelabs.server.domain.model.query.Ast.BooleanTerm
import com.convergencelabs.server.domain.model.query.Ast.Add
import com.convergencelabs.server.domain.model.query.Ast.Subtract
import com.convergencelabs.server.domain.model.query.Ast.Divide
import com.convergencelabs.server.domain.model.query.Ast.Multiply
import com.convergencelabs.server.domain.model.query.Ast.Mod
import com.convergencelabs.server.datastore.QueryUtil
import com.convergencelabs.server.domain.model.query.Ast.ValueTerm
import com.convergencelabs.server.domain.model.query.Ast.IndexPathElement
import com.convergencelabs.server.domain.model.query.Ast.PropertyPathElement

case class ModelQueryParameters(query: String, params: Map[String, Any])

object ModelQueryBuilder {

  def queryModels(select: SelectStatement, username: Option[String]): ModelQueryParameters = {
    implicit val params = ScalaMutableMap[String, Any]()

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
            s"$fieldPath as ${term.name.getOrElse(buildFieldName(term.field))}"
        }).mkString(", "))
        sb.append(" ")
        sb.toString()
      }

    val selectString = s"SELECT ${projectionString}FROM Model WHERE ${ModelStore.Fields.Collection}.${ModelStore.Fields.Id} = ${addParam(select.collection)}"

    val whereString = (select.where map { where =>
      s" and ${buildExpressionString(where)}"
    }) getOrElse ("")

    val permissionString = username.map { usr =>
      val userParam = addParam(usr)
      s""" and ((overridePermissions == true and ((userPermissions contains (user.username = $userParam and permissions.read = true)) or
                    (not(userPermissions contains (user.username = $userParam )) and worldPermissions.read = true))) or 
	               (overridePermissions == false and ((collection.userPermissions contains (user.username = $userParam and permissions.read = true)) or
                    (not(collection.userPermissions contains (user.username = $userParam )) and collection.worldPermissions.read = true))))"""
    }.getOrElse("")

    val orderString: String = if (select.orderBy.isEmpty) {
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

    val queryString = s"${selectString}${whereString}${permissionString}${orderString}"

    ModelQueryParameters(QueryUtil.buildPagedQuery(queryString, select.limit, select.offset), params.toMap)
  }

  private[this] def buildFieldPath(field: FieldTerm): String = {
    val sb = new StringBuilder()
    sb.append("data.children.")
    sb.append(field.field.property)
    field.subpath.foreach {
      case IndexPathElement(i) =>
        sb.append(".children").append("[").append(i.toString).append("]")
      case PropertyPathElement(p) =>
        sb.append(".children").append(".").append(p)
    }

    sb.append(".value")

    sb.toString
  }

  private[this] def buildProjectionPath(field: FieldTerm): String = {
    val sb = new StringBuilder()
    sb.append("data.children.")
    sb.append(field.field.property)
    field.subpath.foreach {
      case IndexPathElement(i) =>
        sb.append(".children").append("[").append(i.toString).append("]")
      case PropertyPathElement(p) =>
        sb.append(".children").append(".").append(p)
    }

    sb.toString
  }

  private[this] def buildFieldName(field: FieldTerm): String = {
    val sb = new StringBuilder()
    sb.append(field.field.property)
    field.subpath.foreach {
      case IndexPathElement(i) =>
        sb.append("_").append(i.toString)
      case PropertyPathElement(p) =>
        sb.append("_").append(p)
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
      case Equals(lhs, rhs)                    => s"(${buildTermString(lhs)} = ${buildTermString(rhs)})"
      case NotEquals(lhs, rhs)                 => s"(${buildTermString(lhs)} != ${buildTermString(rhs)})"
      case GreaterThan(lhs, rhs)               => s"(${buildTermString(lhs)} > ${buildTermString(rhs)})"
      case LessThan(lhs, rhs)                  => s"(${buildTermString(lhs)} < ${buildTermString(rhs)})"
      case LessThanOrEqual(lhs, rhs)           => s"(${buildTermString(lhs)} <= ${buildTermString(rhs)})"
      case GreaterThanOrEqual(lhs, rhs)        => s"(${buildTermString(lhs)} >= ${buildTermString(rhs)})"
      case In(field: String, value: List[Any]) => s"(data.${field} in ${addParam(value.asJava)})"
      case Like(field: String, value: String)  => s"(data.${field} like ${addParam(value)})"
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
}
