package okhttp3.internal.ws

import okio.Buffer
import okio.ByteString
import okio.DeflaterSink
import java.io.Closeable
import java.io.IOException
import java.util.zip.Deflater

class MessageDeflater(private val contextTakeover: Boolean) : Closeable {
  private val sink = Buffer()
  private val deflater = Deflater(Deflater.BEST_COMPRESSION, true /* omit zlib header */)
  private val deflaterSink = DeflaterSink(sink, deflater)
  private val source = Buffer()

  @Throws(IOException::class)
  fun deflate(sourceByteString: ByteString): Buffer {
    if (!contextTakeover) {
      deflater.reset()
    }

    source.write(sourceByteString)
    source.readAll(deflaterSink)
    deflaterSink.flush()

    return sink
  }

  @Throws(IOException::class)
  override fun close() = deflaterSink.close()
}
