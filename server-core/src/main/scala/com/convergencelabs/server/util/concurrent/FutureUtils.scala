package com.convergencelabs.server.util.concurrent

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Success
import scala.util.Try
import scala.concurrent.ExecutionContext

object FutureUtils {
  def tryToFuture[A](t: => Try[A]): Future[A] = {
    t match {
      case Success(s) => Future.successful(s)
      case Failure(fail) => Future.failed(fail)
    }
  }

  def seqFutures[T, U](items: TraversableOnce[T])(yourfunction: T => Future[U])(implicit ec: ExecutionContext): Future[List[U]] = {
    items.foldLeft(Future.successful[List[U]](Nil)) {
      (f, item) =>
        f.flatMap {
          x => yourfunction(item).map(_ :: x)
        }
    } map (_.reverse)
  }
}
