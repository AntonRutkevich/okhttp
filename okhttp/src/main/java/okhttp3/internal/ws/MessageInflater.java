package okhttp3.internal.ws;

import java.io.Closeable;
import java.io.IOException;
import java.util.zip.Inflater;

import okio.Buffer;
import okio.InflaterSource;

class MessageInflater implements Closeable {
  private static final int OCTETS_TO_ADD_BEFORE_INFLATION = 0x0000ffff;

  private final Buffer source = new Buffer();
  private final Inflater inflater = new Inflater(true /* omit zlib header */);
  private final InflaterSource inflaterSource = new InflaterSource(source, inflater);

  private long totalBytesToInflate = 0L;

  Buffer inflate(Buffer buffer) throws IOException {
    if (source.size() != 0) {
      throw new IllegalStateException("source is not empty on write attempt");
    }

    source.writeAll(buffer);
    source.writeInt(OCTETS_TO_ADD_BEFORE_INFLATION);

    totalBytesToInflate += source.size();

    // We cannot read all, as the source does not close.
    // Instead, we ensure that all bytes from source have been processed by inflater.
    while (true) {
      inflaterSource.read(buffer, Long.MAX_VALUE);
      if (inflater.getBytesRead() == totalBytesToInflate) {
        break;
      }
    }
    return buffer;
  }

  @Override public void close() throws IOException {
    inflaterSource.close();
  }
}
