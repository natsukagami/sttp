package sttp.client4.curl

import sttp.client4._
import sttp.client4.wrappers.FollowRedirectsBackend
import sttp.client4.monad.IdMonad
import sttp.monad.TryMonad
import gears.async.Async

import scala.util.Try
import sttp.monad.MonadError
import sttp.client4.curl.internal.CurlCode.CurlCode
import sttp.client4.curl.internal.CurlMCode
import sttp.client4.curl.internal.CurlApi.{multiInit, CurlHandle, CurlMultiHandleOps}
import scala.scalanative.unsafe.Zone
import gears.async.Future.Promise
import scala.util.Success
import scala.util.Random
import scala.collection.mutable.HashMap

// Curl supports redirects, but it doesn't store the history, so using FollowRedirectsBackend is more convenient

private class CurlBackend(verbose: Boolean) extends AbstractSyncCurlBackend(IdMonad, verbose) with SyncBackend {}

object CurlBackend {
  def apply(verbose: Boolean = false): SyncBackend = FollowRedirectsBackend(new CurlBackend(verbose))
}

private class CurlTryBackend(verbose: Boolean) extends AbstractSyncCurlBackend(TryMonad, verbose) with Backend[Try] {}

object CurlTryBackend {
  def apply(verbose: Boolean = false): Backend[Try] = FollowRedirectsBackend(new CurlTryBackend(verbose))
}

object AsyncFnMonad extends MonadError[[T] =>> Async ?=> T] {
  override protected def handleWrappedError[T](rt: (Async) ?=> T)(
      h: PartialFunction[Throwable, (Async) ?=> T]
  ): (Async) ?=> T =
    try rt
    catch {
      case (e: Throwable) => h(e)
    }
  override def ensure[T](f: (Async) ?=> T, e: => (Async) ?=> Unit): (Async) ?=> T =
    try f
    finally e
  override def unit[T](t: T): (Async) ?=> T = t
  override def error[T](t: Throwable): (Async) ?=> T = _ ?=> throw t
  override def map[T, T2](fa: (Async) ?=> T)(f: T => T2): (Async) ?=> T2 = f(fa)
  override def flatMap[T, T2](fa: (Async) ?=> T)(f: T => (Async) ?=> T2): (Async) ?=> T2 = f(fa)

}

object CurlGearsBackend {
  def apply(verbose: Boolean = false, runners: Int = 1): Backend[[T] =>> Async ?=> T] = {
    val curl = new CurlGearsBackend(verbose, runners)
    FollowRedirectsBackend(curl)
  }

  val maxCurlQueue = 1024

  private[curl] class HandlePromise(val handle: CurlHandle) extends Promise[CurlCode]
}

private class CurlGearsBackend(verbose: Boolean, runnersCount: Int)
    extends AbstractCurlBackend(AsyncFnMonad, verbose)
    with Backend[[T] =>> Async ?=> T] {

  import CurlGearsBackend._
  @volatile var stopped = false

  val queue = java.util.concurrent.ConcurrentLinkedQueue[HandlePromise]()

  override def performCurl(c: CurlHandle): (Async) ?=> CurlCode = async ?=> {
    val promise = HandlePromise(c)
    queue.add(promise)
    runners(Random.between(0, runnersCount)).curlMulti.wakeup()
    promise.future.value
  }

  override def close() = {
    stopped = true
    runners.foreach(_.close())
  }

  private val runners = (0 to runnersCount).map(_ => Runner())

  private class Runner {
    val curlMulti = multiInit
    implicit val zone: Zone = Zone.open()
    val callbacks = new HashMap[CurlHandle, HandlePromise](maxCurlQueue, HashMap.defaultLoadFactor)

    def close() = {
      curlMulti.wakeup()
      th.join()
      zone.close()
    }

    private val th = Thread.ofPlatform().start { () =>
      try loop()
      finally curlMulti.cleanup()
    }

    @scala.annotation.tailrec
    private def loop(): Unit = {
      if (stopped) { return }
      val (remains, performCode) = curlMulti.perform
      if (performCode != CurlMCode.Ok)
        throw Exception(s"Curl perform exception: $performCode")
      println(s"Loop performed, remains = $remains")
      dispatch()
      if (queue.isEmpty()) {
        val (events, code) = curlMulti.poll(10 * 1000 /* 10 seconds polling */ )
        if (code != CurlMCode.Ok)
          throw Exception(s"Curl perform exception: $code")
        println(s"Loop polled, events = $events")
      }
      val queued = enqueue(maxCurlQueue - remains)
      println(s"Loop queued $queued")
      loop()
    }

    private def enqueue(max: Int): Int = {
      var queued = 0
      while (queued < max) {
        val p = queue.poll()
        if (p == null) return queued
        callbacks.put(p.handle, p)
        curlMulti.add(p.handle)
        queued += 1
      }
      queued
    }

    @scala.annotation.tailrec
    private def dispatch(): Unit =
      curlMulti.infoRead._1 match
        case None => ()
        case Some((handle, status)) =>
          val p = callbacks.remove(handle).get
          curlMulti.remove(handle)
          p.complete(Success(status))
          dispatch()
  }
}
