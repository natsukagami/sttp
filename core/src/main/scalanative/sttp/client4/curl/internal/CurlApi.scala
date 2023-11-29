package sttp.client4.curl.internal

import sttp.client4.curl.internal.CurlCode.CurlCode
import sttp.client4.curl.internal.CurlInfo.CurlInfo
import sttp.client4.curl.internal.CurlOption.CurlOption

import scala.scalanative.runtime.Boxes
import scala.scalanative.unsafe.{Ptr, _}
import scala.scalanative.unsigned._
import sttp.client4.curl.internal.CurlMCode.CurlMCode
import scala.scalanative.runtime.Intrinsics
import scala.scalanative.posix.sys.socketOps.msghdrOps

private[client4] object CurlApi {
  type CurlHandle = Ptr[Curl]

  type MultiHandle = Ptr[CurlMulti]

  type MimeHandle = Ptr[Mime]

  type MimePartHandle = Ptr[MimePart]

  type SlistHandle = Ptr[CurlSlist]

  def init: CurlHandle = CCurl.init

  implicit class CurlHandleOps(handle: CurlHandle) {
    def mime: MimeHandle = CCurl.mimeInit(handle)

    def perform: CurlCode = CurlCode(CCurl.perform(handle))

    def cleanup(): Unit = CCurl.cleanup(handle)

    def option(option: CurlOption, parameter: String)(implicit z: Zone): CurlCode =
      setopt(handle, option, toCString(parameter))

    def option(option: CurlOption, parameter: Long)(implicit z: Zone): CurlCode =
      setopt(handle, option, parameter)

    def option(option: CurlOption, parameter: Int)(implicit z: Zone): CurlCode =
      setopt(handle, option, parameter)

    def option(option: CurlOption, parameter: Boolean)(implicit z: Zone): CurlCode =
      setopt(handle, option, if (parameter) 1L else 0L)

    def option(option: CurlOption, parameter: Ptr[_]): CurlCode =
      setopt(handle, option, parameter)

    def option[FuncPtr <: CFuncPtr](option: CurlOption, parameter: FuncPtr)(implicit z: Zone): CurlCode =
      setopt(handle, option, Boxes.boxToPtr[Byte](Boxes.unboxToCFuncPtr0(parameter)))

    def info(curlInfo: CurlInfo, parameter: Long)(implicit z: Zone): CurlCode = {
      val lPtr = alloc[Long](sizeof[Long])
      !lPtr = parameter
      getInfo(handle, curlInfo, lPtr)
    }

    def info(curlInfo: CurlInfo, parameter: String)(implicit z: Zone): CurlCode =
      getInfo(handle, curlInfo, toCString(parameter))

    def info(curlInfo: CurlInfo, parameter: Ptr[_]): CurlCode =
      getInfo(handle, curlInfo, parameter)
  }

  def multiInit: MultiHandle = CCurl.multiInit

  implicit class CurlMultiHandleOps(handle: MultiHandle) {
    def perform(implicit z: Zone) = {
      val count = alloc[CInt]()
      val code = CCurl.multiPerform(handle, count)
      (!count, CurlMCode(code))
    }

    def cleanup() = CCurl.multiCleanup(handle)

    def wakeup() = CurlMCode(CCurl.multiWakeup(handle))

    def poll(timeoutMs: Int)(implicit z: Zone) = {
      val count = alloc[CInt]()
      val code =
        CCurl.multiPoll(
          handle,
          scalanative.runtime.fromRawPtr(Intrinsics.castIntToRawPtr(0)),
          0.toUInt,
          timeoutMs,
          count
        )
      (!count, CurlMCode(code))
    }

    def infoRead(implicit z: Zone): (Option[(Ptr[Curl], CurlCode)], Int) = {
      val msgsInQueue = alloc[CInt]()
      // Assuming msg is CURLMSG_DONE, since it's the only possible message
      // https://curl.se/libcurl/c/curl_multi_info_read.html
      val v = CCurl.multiInfoRead(handle, msgsInQueue)
      if (v.toInt == 0) /* null */ { (None, !msgsInQueue) }
      else (Some((v._2, CurlCode(v._3))), !msgsInQueue)
    }

    def add(req: CurlHandle) = CurlMCode(CCurl.multiAddHandle(handle, req))

    def remove(req: CurlHandle) = CurlMCode(CCurl.multiRemoveHandle(handle, req))

  }

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: Ptr[_]): CurlCode =
    CurlCode(CCurl.setopt(handle, option.id, parameter))

  private def setopt(handle: CurlHandle, option: CurlOption, parameter: CVarArg)(implicit z: Zone): CurlCode =
    CurlCode(CCurl.setopt(handle, option.id, toCVarArgList(Seq(parameter))))

  private def getInfo(handle: CurlHandle, curlInfo: CurlInfo, parameter: Ptr[_]): CurlCode =
    CurlCode(CCurl.getInfo(handle, curlInfo.id, parameter))

  implicit class MimeHandleOps(handle: MimeHandle) {
    def free(): Unit = CCurl.mimeFree(handle)

    def addPart(): MimePartHandle = CCurl.mimeAddPart(handle)
  }

  implicit class MimePartHandleOps(handle: MimePartHandle) {
    def withName(name: String)(implicit zone: Zone): CurlCode = CCurl.mimeName(handle, toCString(name))

    def withFileName(filename: String)(implicit zone: Zone): CurlCode = CCurl.mimeFilename(handle, toCString(filename))

    def withMimeType(mimetype: String)(implicit zone: Zone): CurlCode = CCurl.mimeType(handle, toCString(mimetype))

    def withEncoding(encoding: String)(implicit zone: Zone): CurlCode = CCurl.mimeEncoder(handle, toCString(encoding))

    def withData(data: String, datasize: Long = CurlZeroTerminated)(implicit zone: Zone): CurlCode =
      CCurl.mimeData(handle, toCString(data), datasize.toUSize)

    def withFileData(filename: String)(implicit zone: Zone): CurlCode = CCurl.mimeFiledata(handle, toCString(filename))

    def withSubParts(subparts: MimePartHandle): CurlCode = CCurl.mimeSubParts(handle, subparts)

    def withHeaders(headers: Ptr[CurlSlist], takeOwnership: Int = 0): CurlCode =
      CCurl.mimeHeaders(handle, headers, takeOwnership)
  }

  implicit class SlistHandleOps(handle: SlistHandle) {
    def append(string: String)(implicit z: Zone): Ptr[CurlSlist] =
      CCurl.slistAppend(handle, toCString(string)(z))

    def free(): Unit =
      CCurl.slistFree(handle)
  }
}
