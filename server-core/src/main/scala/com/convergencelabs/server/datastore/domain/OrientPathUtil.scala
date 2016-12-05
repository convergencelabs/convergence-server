package com.convergencelabs.server.datastore.domain

object OrientPathUtil {
  def toOrientPath(path: List[Any]): String = {
    val pathBuilder = new StringBuilder()
    pathBuilder.append(ModelStore.Fields.Data)
    path.foreach { p =>
      p match {
        case p: Int => pathBuilder.append(s"[$p]")
        case p: BigInt => pathBuilder.append(s"[$p]") // FIXME json4 is is giving us BigInts.
        case p: String => pathBuilder.append(s".${p}")
      }
    }
    pathBuilder.toString()
  }

  def escape(text: String): String = {
    s"`${text}`"
  }

  def appendToPath(path: String, property: String): String = {
    s"$path.${property}"
  }
}
