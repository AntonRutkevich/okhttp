/*
 * Copyright (C) 2019 Square, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package okhttp3.internal.ws

import okio.Buffer
import okio.ByteString.Companion.decodeHex
import okio.InflaterSource
import java.io.Closeable
import java.io.IOException
import java.lang.Exception
import java.util.zip.Inflater
import kotlin.math.min

private const val OCTETS_TO_ADD_BEFORE_INFLATION = 0x0000ffff
private val ZERO_BYTE = "00".decodeHex()

class MessageInflater : Closeable {
  private val source = Buffer()
  private val inflater = Inflater(true /* omit zlib header */)
  private val inflaterSource = InflaterSource(source, inflater)

  private var totalBytesToInflate = 0L

  /**
   * Inflates bytes from [buffer] as described in
   * [rfc7692#section-7.2.2](https://tools.ietf.org/html/rfc7692#section-7.2.2).
   * and writes inflated data back to it.
   */
  @Throws(IOException::class)
  fun inflate(buffer: Buffer): Buffer {
    require(source.size == 0L)

    println("START READING BUFFER")
    println("totalBytesToInflate = $totalBytesToInflate")
    println("payload_size = ${buffer.size}")

    val sizeToLog = min(8, buffer.size)
    val start = Buffer()
    buffer.copyTo(start, 0, sizeToLog)
    val startBuf = start.readByteString().hex()

    val end = Buffer()
    buffer.copyTo(end, buffer.size - sizeToLog, sizeToLog)
    val endBuf = end.readByteString().hex()

    println("Buffer start-end: $startBuf...$endBuf")

    source.writeAll(buffer)
    println("source_size = ${source.size}")

    //    if (source.endsWith00()) {
    println("adding 4 octets")
    source.writeInt(OCTETS_TO_ADD_BEFORE_INFLATION)
    //    } else {
    //      println("adding no octets")
    //source.writeByte(0x00)
    //source.writeInt(OCTETS_TO_ADD_BEFORE_INFLATION)
    //    }

    println("source_size = ${source.size}")

    totalBytesToInflate += source.size

    println("totalBytesToInflate with source = $totalBytesToInflate")

    return buffer.apply {
      // We cannot read all, as the source does not close.
      // Instead, we ensure that all bytes from source have been processed by inflater.
      try {
        while (true) {
          println("1 inflater.bytesRead = ${inflater.bytesRead}")
          println("1 inflater.remaining = ${inflater.remaining}")
          println("1 source.size = ${source.size}")
          println("1 source.buffer.size = ${source.buffer.size}")
          val read = inflaterSource.read(this, Long.MAX_VALUE)
          println("2 inflater.bytesRead after reading $read bytes and tbti of $totalBytesToInflate = ${inflater.bytesRead}")
          println("2 inflater.remaining after reading = ${inflater.remaining}")
          println("2 source.size after reading = ${source.size}")
          println("2 source.buffer.size after reading = ${source.buffer.size}")
          if (inflater.bytesRead == totalBytesToInflate || (inflater.bytesRead == totalBytesToInflate - 4)) {
            println("2 END READING BUFFER")
            break
          }
        }
      } catch (e: Exception) {
        println("3 inflater.bytesRead in catch = ${inflater.bytesRead}")
        println("3 source.size in catch = ${source.size}")

        println("3 buffer in catch ${this.snapshot().utf8()}")
        println("Buffer start-end in catch: $startBuf...$endBuf")
        throw e
      }
    }
  }

  @Throws(IOException::class)
  override fun close() = inflaterSource.close()

  private fun Buffer.endsWith00(): Boolean = rangeEquals(
    size - 1, ZERO_BYTE)
}
