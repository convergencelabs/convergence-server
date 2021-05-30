package com.convergencelabs.convergence.server.util

import scala.util.{Failure, Success, Try}

object EitherUtils {
  def tryToEither[T](t: Try[T]): Either[Throwable, T] = {
    t match {
      case Failure(exception) => Left(exception)
      case Success(value) =>Right(value)
    }
  }
}
