package okhttp3.internal.ws

import okio.Buffer
import okio.DeflaterSink
import java.io.Closeable
import java.io.IOException
import java.util.zip.Deflater

class MessageDeflater(private val contextTakeover: Boolean) : Closeable {
  private val sink = Buffer()
  private val deflater = Deflater(Deflater.BEST_COMPRESSION, true /* omit zlib header */)
  private val deflaterSink = DeflaterSink(sink, deflater)

  @Throws(IOException::class)
  fun deflate(source: Buffer): Buffer {
    if (!contextTakeover) {
      deflater.reset()
    }

    source.readAll(deflaterSink)
    deflaterSink.flush()

    return sink
  }

  @Throws(IOException::class)
  override fun close() {
    deflaterSink.close()
  }
}
