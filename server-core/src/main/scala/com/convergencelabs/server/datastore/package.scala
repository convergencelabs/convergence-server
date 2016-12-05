package com.convergencelabs.server

package object datastore {

  sealed trait CreateOrUpdateResult[+t]
  
  sealed trait CreateResult[+T]
  case class CreateSuccess[T](result: T) extends CreateResult[T] with CreateOrUpdateResult[T]
  case object DuplicateValue extends CreateResult[Nothing]

  sealed trait DeleteResult
  case object DeleteSuccess extends DeleteResult

  sealed trait UpdateResult
  case object UpdateSuccess extends UpdateResult with CreateOrUpdateResult[Nothing]
  case object InvalidValue extends UpdateResult with CreateResult[Nothing] with CreateOrUpdateResult[Nothing]
  case object NotFound extends UpdateResult with DeleteResult
  
  
}
