package com.convergencelabs.server

package object datastore {

  sealed trait DatastoreExcpetion

  case class DuplicateValueException(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
    with DatastoreExcpetion

  case class EntityNotFoundException(message: String = "", entityId: Option[Any] = None)
    extends Exception(message)
    with DatastoreExcpetion

  case class InvalidValueExcpetion(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
    with DatastoreExcpetion

  case class DatabaseCommandException(query: String, params: Map[_, _], message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
    with DatastoreExcpetion

  case class MultipleValuesException()
    extends Exception("The query unepxcectedly returned multiple results")
    with DatastoreExcpetion
}
