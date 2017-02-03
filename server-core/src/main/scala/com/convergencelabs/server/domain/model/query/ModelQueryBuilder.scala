package com.convergencelabs.server.datastore.domain

import scala.collection.mutable.{Map => ScalaMutableMap}
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
import com.convergencelabs.server.domain.model.query.Ast.ExpressionValue
import com.convergencelabs.server.domain.model.query.Ast.MathematicalOperator
import com.convergencelabs.server.domain.model.query.Ast.DoubleExpressionValue
import com.convergencelabs.server.domain.model.query.Ast.FieldExpressionValue
import com.convergencelabs.server.domain.model.query.Ast.LongExpressionValue
import com.convergencelabs.server.domain.model.query.Ast.StringExpressionValue
import com.convergencelabs.server.domain.model.query.Ast.BooleanExpressionValue
import com.convergencelabs.server.domain.model.query.Ast.Add
import com.convergencelabs.server.domain.model.query.Ast.Subtract
import com.convergencelabs.server.domain.model.query.Ast.Divide
import com.convergencelabs.server.domain.model.query.Ast.Multiply
import com.convergencelabs.server.domain.model.query.Ast.Mod
import com.convergencelabs.server.datastore.QueryUtil

case class ModelQueryParameters(query: String, params: Map[String, Any])

object ModelQueryBuilder {

  def queryModels(select: SelectStatement): ModelQueryParameters = {
    implicit val params = ScalaMutableMap[String, Any]()

    val selectString = s"SELECT FROM Model WHERE ${ModelStore.Fields.Collection}.${ModelStore.Fields.Id} = ${addParam(select.collection)}"

    val whereString = (select.where map { where =>
      s" and ${buildExpressionString(where)}"
    }) getOrElse ("")

    val orderString: String = if (select.orderBy.isEmpty) {
      ""
    } else {
      " ORDER BY " + (select.orderBy map { orderBy =>
        val ascendingParam = orderBy.direction map {
          case Ascending  => "ASC"
          case Descending => "DESC"
        } getOrElse("ASC") 
        s"data.${orderBy.field} ${ascendingParam}"
      }).mkString(", ")
    }

    val queryString = s"${selectString}${whereString}${orderString}"

    ModelQueryParameters(QueryUtil.buildPagedQuery(queryString, select.limit, select.offset), params.toMap)
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
      case expression: ExpressionValue      => buildExpressionValueString(expression)
      case expression: MathematicalOperator => buildMathmaticalExpressionString(expression)
    }
  }

  private[this] def buildExpressionValueString(expressionValue: ExpressionValue)(implicit params: ScalaMutableMap[String, Any]): String = {
    expressionValue match {
      case LongExpressionValue(value)    => s"${addParam(value)}"
      case DoubleExpressionValue(value)  => s"${addParam(value)}"
      case StringExpressionValue(value)  => s"${addParam(value)}"
      case BooleanExpressionValue(value) => s"${addParam(value)}"
      case FieldExpressionValue(value)   => s"data.${value}"
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
    val param = s"${params.size}"
    params += s"${params.size}" -> value
    s":$param"
  }
}
