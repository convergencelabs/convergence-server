package com.convergencelabs.server

package object datastore {

  case class DuplicateValueException(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)

  case class EntityNotFoundException(message: String = "", entityId: Option[Any] = None)
    extends Exception(message)
  
  case class InvalidValueExcpetion(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
}
