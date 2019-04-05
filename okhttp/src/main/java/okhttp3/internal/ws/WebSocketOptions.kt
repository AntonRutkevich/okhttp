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
        response.header("Sec-WebSocket-Extensions")
            ?.takeIf { it.contains("permessage-deflate") }
            ?.let { extensionHeader ->
              val options = extensionHeader.split(" ").map { it.replace(";", "") }

              val clientNoContextTakeover = parseClientNoContextTakeover(options)
              val clientMaxWindowBits = parseClientMaxWindowBits(options)

              if (!clientNoContextTakeover) {
                if (clientMaxWindowBits != null
                    && clientMaxWindowBits != SUPPORTED_CLIENT_MAX_WINDOW_BITS) {
                  throw IOException(
                      "'$OPTION_CLIENT_MAX_WINDOW_BITS' of $clientMaxWindowBits is not supported. " +
                      "Use $SUPPORTED_CLIENT_MAX_WINDOW_BITS or '$OPTION_CLIENT_NO_CONTEXT_TAKEOVER' option")
                }
              }

              WebSocketOptions(
                  compressionEnabled = true,
                  contextTakeover = !clientNoContextTakeover
              )
            }
        ?: DEFAULT

    private fun parseClientNoContextTakeover(options: List<String>): Boolean = options
        .firstOrNull { it == OPTION_CLIENT_NO_CONTEXT_TAKEOVER } != null

    private fun parseClientMaxWindowBits(options: List<String>): Int? = options
        .firstOrNull { it.contains(OPTION_CLIENT_MAX_WINDOW_BITS) }
        ?.let { option ->
          option.split("=").getOrNull(1)?.toIntOrNull()
        }
  }
}
