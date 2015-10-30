package com.convergencelabs.server.datastore

object QueryUtil {
  // FIXE abstract this to a utility method
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
}

object SortOrder extends Enumeration {
  val Ascending = Value("ASC")
  val Descending = Value("DESC")
}