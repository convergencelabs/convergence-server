package com.convergencelabs.server.datastore

import java.util.{ List => JavaList }
import grizzled.slf4j.Logging
import scala.collection.JavaConverters._

object QueryUtil extends Logging {
  private[this] val MultipleElementsMessage = "Only exepected one element in the result list, but more than one returned."

  def buildPagedQuery(baseQuery: String, limit: Option[Int], offset: Option[Int]): String = {
    val limitOffsetString = (limit, offset) match {
      case (None, None) => ""
      case (Some(lim), None) => s" LIMIT $lim"
      case (None, Some(off)) => s" SKIP $off"
      case (Some(lim), Some(off)) => s" SKIP $off LIMIT $lim"
    }

    baseQuery + limitOffsetString
  }

  def enforceSingletonResultList[T](list: JavaList[T]): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => Some(first)
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        None
      case Nil => None
    }
  }

  def mapSingletonList[T, L](list: JavaList[L])(m: L => T): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => Some(m(first))
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        None
      case Nil => None
    }
  }

  def mapSingletonListToOption[T, L](list: JavaList[L])(m: L => Option[T]): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => m(first)
      case first :: rest =>
        logger.error(MultipleElementsMessage)
        None
      case Nil => None
    }
  }
}
