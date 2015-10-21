package com.convergencelabs.server.util

import scala.concurrent.Future
import scala.concurrent.ExecutionContext
import scala.reflect.ClassTag
import scala.concurrent.Promise
import scala.util.Success
import scala.util.Failure
import scala.util.Try
import scala.language.implicitConversions

package object concurrent {
  implicit def toAskFuture(f: Future[_]) = new AskFuture(f)
  
  class AskFuture[T](val future: Future[T]) extends Future[T] {

    private[this] object InternalCallbackExecutor extends ExecutionContext {
      override def execute(r: Runnable): Unit = r.run()

      override def reportFailure(t: Throwable): Unit = throw new IllegalStateException("problem in scala.concurrent internal callback", t)
    }

    private[this] implicit val ec = InternalCallbackExecutor

    def isCompleted: Boolean = future.isCompleted
    def onComplete[U](f: Try[T] => U)(implicit executor: scala.concurrent.ExecutionContext): Unit = future onComplete f

    def value: Option[scala.util.Try[T]] = future.value

    def ready(atMost: scala.concurrent.duration.Duration)(implicit permit: scala.concurrent.CanAwait): this.type = this

    def result(atMost: scala.concurrent.duration.Duration)(implicit permit: scala.concurrent.CanAwait): T = future.result(atMost)

    def mapResponse[S](implicit tag: ClassTag[S]): Future[S] = {
      val c = tag.runtimeClass

      val p = Promise[S]
      future onComplete {
        case Success(x) if c.isInstance(x) => {
          p.success(c.cast(x).asInstanceOf[S])
        }
        case Success(Error(code, reason)) => {
          p.failure(new ErrorException(code, reason))
        }
        case Success(x) => {
          val message = s"Wanted ${tag.runtimeClass.getName} but got ${x.getClass.getName}"
          p.failure(new UnexpectedResponseException(message))
        }
        case Failure(cause) => {
          p.failure(cause)
        }
      }

      p.future
    }
  }

  case class ErrorException(code: String, message: String) extends Exception(message) {
    def this() = {
      this("unknown", "An unkown error occured")
    }
  }

  class UnexpectedResponseException(message: String) extends Exception(message)
}

