package com.convergencelabs.server

package object datastore {

  sealed trait CreateResult[+T]
  case class CreateSuccess[T](result: T) extends CreateResult[T]
  case object DuplicateValue extends CreateResult[Nothing]

  sealed trait DeleteResult
  case object DeleteSuccess extends DeleteResult

  sealed trait UpdateResult
  case object UpdateSuccess extends UpdateResult
  case object NotFound extends UpdateResult with DeleteResult
}
