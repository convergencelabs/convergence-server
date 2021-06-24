package com.convergencelabs.convergence.server.util

import scala.util.{Success, Try}

object TryUtils {
  def toTry[T, U](o: Option[T])(f: T => Try[Unit]): Try[Unit] = {
    o.map(f).getOrElse(Success(()))
  }

  def unsafeToTry[T, U](o: Option[T])(f: T => Unit): Try[Unit] = {
    o.map(v => Try(f(v))).getOrElse(Success(()))
  }
}
