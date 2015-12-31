package com.convergencelabs.server.datastore.domain

object OrientPathUtil {
  def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder()
    pathBuilder.append(mapper.ModelOperationMapper.Fields.Data)
    path.foreach { p =>
      p match {
        case p: Int    => pathBuilder.append(s"[$p]")
        case p: String => pathBuilder.append(s".$p")
      }
    }
    pathBuilder.toString()
  }

  def appendToPath(path: String, property: String): String = {
    s"$path.$property"
  }
}