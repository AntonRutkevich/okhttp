package okhttp3.internal.ws;

import java.io.Closeable;
import java.io.IOException;
import java.util.zip.Deflater;

import okio.Buffer;
import okio.ByteString;
import okio.DeflaterSink;

class MessageDeflater implements Closeable {
  private static final ByteString EMPTY_DEFLATE_BLOCK = ByteString.decodeHex("000000ffff");
  private static final int LAST_OCTETS_COUNT_TO_REMOVE_AFTER_DEFLATION = 4;

  private final Buffer sink = new Buffer();
  private final Deflater deflater = new Deflater(
      Deflater.DEFAULT_COMPRESSION, true /* omit zlib header */);
  private final DeflaterSink deflaterSink = new DeflaterSink(sink, deflater);
  private final Buffer source = new Buffer();

  private boolean contextTakeover;

  MessageDeflater(boolean contextTakeover) {
    this.contextTakeover = contextTakeover;
  }

  Buffer deflate(ByteString sourceByteString) throws IOException {
    if (!contextTakeover) {
      deflater.reset();
    }

    source.write(sourceByteString);
    source.readAll(deflaterSink);
    deflaterSink.flush();

    return applyPostDeflate(sink);
  }

  private Buffer applyPostDeflate(Buffer buffer) {
    if (endsWithEmptyDeflateBlock(buffer)) {
      long newSize = buffer.size() - LAST_OCTETS_COUNT_TO_REMOVE_AFTER_DEFLATION;

      try (Buffer.UnsafeCursor cursor = buffer.readAndWriteUnsafe()) {
        cursor.resizeBuffer(newSize);
      }
    } else {
      // Same as adding EMPTY_DEFLATE_BLOCK and then removing 4 bytes
      buffer.writeByte(0x00);
    }
    return buffer;
  }

  private boolean endsWithEmptyDeflateBlock(Buffer buffer) {
    return buffer.rangeEquals(
        buffer.size() - EMPTY_DEFLATE_BLOCK.size(), EMPTY_DEFLATE_BLOCK);
  }

  @Override public void close() throws IOException {
    deflaterSink.close();
  }
}
