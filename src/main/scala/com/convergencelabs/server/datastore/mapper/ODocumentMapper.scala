package com.convergencelabs.server.datastore.mapper

import com.orientechnologies.orient.core.record.impl.ODocument

// scalastyle:off null
trait ODocumentMapper {
  protected[datastore] def valueOrNull[T >: Null](option: Option[T]): T = {
    option match {
      case Some(value) => value
      case None => null
    }
  }

  protected[datastore] def toOption[T >: Null](value: T): Option[T] = {
    value match {
      case value: Any => Some(value.asInstanceOf[T])
      case null => None
    }
  }

  protected[datastore] def validateDocumentClass(doc: ODocument, validClassNames: String*): Unit = {
    if (!validClassNames.contains(doc.getClassName)) {
      throw new IllegalArgumentException(s"The ODocument class must be one of '${validClassNames}': ${doc.getClassName}")
    }
  }

  protected[datastore] def mapOrNull[V >: Null, T](option: Option[T])(m: T => V): V = {
    option match {
      case Some(value) => m(value)
      case None => null
    }
  }
}
