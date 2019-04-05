@file:JvmName("CompressionCorrector")

package okhttp3.internal.ws

import okio.Buffer
import okio.ByteString.Companion.decodeHex

private val EMPTY_DEFLATE_BLOCK = "000000ffff".decodeHex()
private const val LAST_OCTETS_COUNT_TO_REMOVE_AFTER_DEFLATION = 4

private const val OCTETS_TO_ADD_BEFORE_INFLATION = 0x0000ffff

/**
 * Applies post-deflate transformations described in
 * [rfc7692#section-7.2.1](https://tools.ietf.org/html/rfc7692#section-7.2.1).
 *
 * @param deflated Buffer containing deflated data
 */
fun applyPostDeflate(deflated: Buffer) = deflated.apply {
  if (endsWithEmptyDeflateBlock(this)) {
    val newSize = size - LAST_OCTETS_COUNT_TO_REMOVE_AFTER_DEFLATION
    readAndWriteUnsafe().use { cursor ->
      cursor.resizeBuffer(newSize)
    }
  } else {
    // Same as adding EMPTY_DEFLATE_BLOCK and then removing 4 bytes
    writeByte(0x00)
  }
}

/**
 * Applies pre-inflate transformations described in
 * [rfc7692#section-7.2.2](https://tools.ietf.org/html/rfc7692#section-7.2.2).
 *
 * @param buffer Buffer containing full deflated message data
 */
fun applyPreInflate(buffer: Buffer) = buffer.apply {
  writeInt(OCTETS_TO_ADD_BEFORE_INFLATION)
}

private fun endsWithEmptyDeflateBlock(buffer: Buffer): Boolean = buffer.rangeEquals(
    buffer.size - EMPTY_DEFLATE_BLOCK.size, EMPTY_DEFLATE_BLOCK)
