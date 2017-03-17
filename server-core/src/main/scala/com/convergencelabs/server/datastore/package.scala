package com.convergencelabs.server

package object datastore {

  case class DuplicateValueExcpetion(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
  
  case class EntityNotFoundException(message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
  
  case class InvalidValueExcpetion(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
  
  case class UnauthorizedException(field: String, message: String = "", cause: Throwable = null)
    extends Exception(message, cause)
}
