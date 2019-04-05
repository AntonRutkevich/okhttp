package okhttp3.internal.ws

import okio.Buffer
import okio.InflaterSource
import java.io.Closeable
import java.io.EOFException
import java.io.IOException
import java.util.zip.Inflater

class MessageInflater : Closeable {
  private val source = Buffer()
  private val inflater = Inflater(true /* omit zlib header */)
  private val inflaterSource = InflaterSource(source, inflater)

  private var totalBytesToInflate = 0L

  @Throws(IOException::class)
  fun inflate(deflated: Buffer): Buffer {
    source.writeAll(deflated)
    totalBytesToInflate += source.size

    return Buffer().apply {
      // We cannot read all, as the source does not close.
      // Instead, we ensure that all bytes from source have been processed by inflater.
      while (true) {
        inflaterSource.read(this, Long.MAX_VALUE)
        if (inflater.totalIn.toLong() == totalBytesToInflate) {
          break
        }
      }
    }
  }

  @Throws(IOException::class)
  override fun close() = inflaterSource.close()
}
