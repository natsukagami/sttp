package sttp.client4.testing

import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.duration.FiniteDuration

object Platform {

  def delayedFuture[T](delay: FiniteDuration)(result: => T)(implicit ec: ExecutionContext): Future[T] =
    Future {
      Thread.sleep(delay.toMillis)
      result
    }
}
