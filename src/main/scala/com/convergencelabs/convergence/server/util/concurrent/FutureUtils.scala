/*
 * Copyright (c) 2019 - Convergence Labs, Inc.
 *
 * This file is part of the Convergence Server, which is released under
 * the terms of the GNU General Public License version 3 (GPLv3). A copy
 * of the GPLv3 should have been provided along with this file, typically
 * located in the "LICENSE" file, which is part of this source code package.
 * Alternatively, see <https://www.gnu.org/licenses/gpl-3.0.html> for the
 * full text of the GPLv3 license, if it was not provided.
 */

package com.convergencelabs.convergence.server.util.concurrent

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object FutureUtils {
  def tryToFuture[A](t: => Try[A]): Future[A] = {
    t match {
      case Success(s) => Future.successful(s)
      case Failure(fail) => Future.failed(fail)
    }
  }

  def lift[T](f: Future[T])(implicit ec: ExecutionContext): Future[Try[T]] =
    f map { Success(_) } recover { case e => Failure(e) }

  def lift[T](fs: Seq[Future[T]])(implicit ec: ExecutionContext): Seq[Future[Try[T]]] =
    fs map { lift(_) }

  implicit class RichSeqFuture[+T](val fs: Seq[Future[T]]) extends AnyVal {
    def onComplete[U](f: Seq[Try[T]] => U)(implicit ec: ExecutionContext): Unit = {
      Future.sequence(lift(fs)) onComplete {
        case Success(s) => f(s)
        case Failure(e) => throw e // will never happen, because of the Try lifting
      }
    }
  }
}
