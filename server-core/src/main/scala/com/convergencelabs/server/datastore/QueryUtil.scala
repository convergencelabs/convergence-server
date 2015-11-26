package com.convergencelabs.server.datastore

import com.orientechnologies.orient.core.record.impl.ODocument
import java.util.{ List => JavaList }
import scala.collection.JavaConversions._
import scala.collection.JavaConverters._
import grizzled.slf4j.Logging

// FIXME should this be a base class?

object QueryUtil extends Logging {
  def buildPagedQuery(baseQuery: String, limit: Option[Int], offset: Option[Int]): String = {
    val limitOffsetString = (limit, offset) match {
      case (None, None) => ""
      case (Some(lim), None) => s" LIMIT $lim"
      case (None, Some(off)) => s" SKIP $off"
      case (Some(lim), Some(off)) => s" SKIP $off LIMIT $lim"
    }

    baseQuery + limitOffsetString
  }
  
  def generateMultipleRecordsError(methodName: String): String = {
    s"$methodName returned more than one element, when only one was expected."
  }
  
  def enforceSingleResult[T](list: JavaList[T]): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => Some(first)
      case first :: rest =>
        logger.error("Only exepected one domain config, but more than one returned.")
        None
      case Nil => None
    }
  }
  
  def mapSingleResult[T,L](list: JavaList[L])(m: L => T): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => Some(m(first))
      case first :: rest =>
        logger.error("Only exepected one domain config, but more than one returned.")
        None
      case Nil => None
    }
  }
  
  // FIXME not sure this is the right name
  def flatMapSingleResult[T,L](list: JavaList[L])(m: L => Option[T]): Option[T] = {
    list.asScala.toList match {
      case first :: Nil => m(first)
      case first :: rest =>
        logger.error("Only exepected one domain config, but more than one returned.")
        None
      case Nil => None
    }
  }
}

object SortOrder extends Enumeration {
  val Ascending = Value("ASC")
  val Descending = Value("DESC")
}