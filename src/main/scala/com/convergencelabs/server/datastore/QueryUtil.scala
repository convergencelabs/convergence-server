package com.convergencelabs.server.datastore

object QueryUtil {
  // FIXE abstract this to a utility method
  def buildPagedQuery(baseQuery: String, limit: Option[Int], offset: Option[Int]): String = {
    val limitOffsetString = (limit, offset) match {
      case (None, None) => ""
      case (Some(_), None) => " LIMIT :limit"
      case (None, Some(_)) => " SKIP :offset"
      case (Some(_), Some(_)) => " SKIP :offset LIMIT :limit"
    }

    baseQuery + limitOffsetString
  }
}

object SortOrder extends Enumeration {
  val Ascending = Value("ASC")
  val Descending = Value("DESC")
}