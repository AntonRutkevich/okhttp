/*
 * Copyright (C) 2012 Square, Inc.
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

import okhttp3.Response
import java.io.IOException

data class WebSocketOptions(
    @JvmField val compressionEnabled: Boolean,
    @JvmField val contextTakeover: Boolean
) {
  companion object {
    private const val OPTION_CLIENT_NO_CONTEXT_TAKEOVER = "client_no_context_takeover"
    private const val OPTION_CLIENT_MAX_WINDOW_BITS = "client_max_window_bits"
    private const val SUPPORTED_CLIENT_MAX_WINDOW_BITS = 15

    private val DEFAULT = WebSocketOptions(
        compressionEnabled = false,
        contextTakeover = false
    )

    @Throws(IOException::class)
    @JvmStatic
    fun parseServerResponse(response: Response): WebSocketOptions =
        response.header("Sec-WebSocket-Extensions")?.let { extensionHeader ->
          if ("permessage-deflate" in extensionHeader) {
            val options = extensionHeader.split(" ").map { it.replace(";", "") }

            // TODO: proper extension parsing
            val clientNoContextTakeover = parseClientNoContextTakeover(options)
            val clientMaxWindowBits = parseClientMaxWindowBits(options)

            if (clientMaxWindowBits != null
                && clientMaxWindowBits != SUPPORTED_CLIENT_MAX_WINDOW_BITS) {
              throw IOException(
                  "'$OPTION_CLIENT_MAX_WINDOW_BITS' of $clientMaxWindowBits is not supported. " +
                  "Use $SUPPORTED_CLIENT_MAX_WINDOW_BITS or '$OPTION_CLIENT_NO_CONTEXT_TAKEOVER' option")
            }

            WebSocketOptions(
                compressionEnabled = true,
                contextTakeover = !clientNoContextTakeover
            )
          } else {
            DEFAULT
          }
        } ?: DEFAULT

    private fun parseClientNoContextTakeover(options: List<String>): Boolean = options
        .firstOrNull { it == OPTION_CLIENT_NO_CONTEXT_TAKEOVER } != null

    private fun parseClientMaxWindowBits(options: List<String>): Int? = options
        .firstOrNull { it.contains(OPTION_CLIENT_MAX_WINDOW_BITS) }
        ?.let { option ->
          option.split("=").getOrNull(1)?.toIntOrNull()
        }
  }
}
