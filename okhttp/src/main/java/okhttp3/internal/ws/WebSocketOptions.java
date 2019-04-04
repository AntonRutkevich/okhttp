package okhttp3.internal.ws;

import java.io.IOException;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;

import okhttp3.Response;

final class WebSocketOptions {
  private static final String HEADER_WEB_SOCKET_EXTENSION = "Sec-WebSocket-Extensions";
  private static final String EXTENSION_PERMESSAGE_DEFLATE = "permessage-deflate";
  private static final WebSocketOptions NO_COMPRESSION = new WebSocketOptions(false, false);
  private static final WebSocketOptions COMPRESSION_NO_TAKEOVER = new WebSocketOptions(true, false);
  private static final WebSocketOptions COMPRESSION_WITH_TAKEOVER =
      new WebSocketOptions(true, true);

  final boolean compressionEnabled;
  final boolean contextTakeover;

  WebSocketOptions(boolean compressionEnabled, boolean contextTakeover) {
    this.compressionEnabled = compressionEnabled;
    this.contextTakeover = contextTakeover;
  }

  static WebSocketOptions parseServerResponse(Response response) throws IOException {
    // No extension header - server does not support permessage-deflate.
    String header = response.header(HEADER_WEB_SOCKET_EXTENSION);
    if (header == null) {
      return NO_COMPRESSION;
    }

    // Server is free to return empty header to indicate no compression
    // See end of https://tools.ietf.org/html/rfc7692#section-5 chapter
    if (header.isEmpty()) {
      return NO_COMPRESSION;
    }

    String[] extensionSplit = header.split(", ");
    if (extensionSplit.length == 0) {
      throw new ProtocolException(
          HEADER_WEB_SOCKET_EXTENSION + " malformed: '" + header + "'");
    }

    String extension = extensionSplit[0];
    if (extension.isEmpty()) {
      // Additional or fallback extensions are not supported currently.
      // See https://tools.ietf.org/html/rfc7692#section-5.2 for details.
      throw new ProtocolException(
          HEADER_WEB_SOCKET_EXTENSION + " malformed: '" + header + "'");
    }

    String[] tokensSplit = extension.split("; ");

    String[] tokens = new String[tokensSplit.length];
    for (int i = 0; i < tokensSplit.length; ++i) {
      tokens[i] = tokensSplit[i].replace(";", "");
    }

    if (tokens.length == 0) {
      throw new ProtocolException(
          "Extension could not be parsed: '" + extension + "'");
    }

    String extensionName = tokens[0];
    if (!extensionName.equals(EXTENSION_PERMESSAGE_DEFLATE)) {
      // Client MUST fail for extension it did not ask for
      throw new ProtocolException(
          "Extension not supported: '" + extensionName + "'");
    }

    List<Option> options = new ArrayList<>(tokens.length - 1);
    // Skip extension name
    for (int i = 1; i < tokensSplit.length; ++i) {
      options.add(Option.parse(tokens[i]));
    }
    if (options.isEmpty()) {
      // Server did not force client_no_context_takeover,
      // so client can use compression with context takeover.
      return COMPRESSION_WITH_TAKEOVER;
    }

    if (new HashSet<>(options).size() != options.size()) {
      // Client MUST fail for duplicate options.
      throw new ProtocolException(
          "Duplicate options found in '" + extension + "'");
    }

    boolean clientNoContextTakeover =
        options.contains(Option.CLIENT_NO_CONTEXT_TAKEOVER);

    if (clientNoContextTakeover) {
      return COMPRESSION_NO_TAKEOVER;
    } else {
      return COMPRESSION_WITH_TAKEOVER;
    }
  }
}

enum Option {
  CLIENT_NO_CONTEXT_TAKEOVER("client_no_context_takeover"),
  SERVER_NO_CONTEXT_TAKEOVER("server_no_context_takeover"),
  CLIENT_MAX_WINDOW_BITS("client_max_window_bits"),
  SERVER_MAX_WINDOW_BITS("server_max_window_bits");

  private static final int MIN_MAX_WINDOW_BITS = 8;
  private static final int MAX_MAX_WINDOW_BITS = 15;
  private static final int SUPPORTED_CLIENT_MAX_WINDOW_BITS = 15;

  private final String id;

  Option(String id) {
    this.id = id;
  }

  static Option parse(String optionString) throws ProtocolException {
    Option option;
    if (optionString.equals(CLIENT_NO_CONTEXT_TAKEOVER.id)) {
      option = CLIENT_NO_CONTEXT_TAKEOVER;
    } else if (optionString.equals(SERVER_NO_CONTEXT_TAKEOVER.id)) {
      option = SERVER_NO_CONTEXT_TAKEOVER;
    } else if (optionString.startsWith(CLIENT_MAX_WINDOW_BITS.id)) {
      verifyMaxWindowBits(CLIENT_MAX_WINDOW_BITS, optionString);
      option = CLIENT_MAX_WINDOW_BITS;
    } else if (optionString.startsWith(SERVER_MAX_WINDOW_BITS.id)) {
      verifyMaxWindowBits(SERVER_MAX_WINDOW_BITS, optionString);
      option = SERVER_MAX_WINDOW_BITS;
    } else {
      throw new ProtocolException("Unknown option: '" + optionString + "'");
    }
    return option;
  }

  private static void verifyMaxWindowBits(Option option, String optionString)
      throws ProtocolException {
    int maxWindowBits;
    try {
      // We should support quoted parameters, eg "15"
      maxWindowBits = Integer.parseInt(
          optionString.substring(option.id.length() + 1).replace("\"", ""));
    } catch (Exception e) {
      throw new ProtocolException(
          "Failed to parse max_window_bits value from '" + optionString + "'");
    }

    if (option == CLIENT_MAX_WINDOW_BITS
        && maxWindowBits != SUPPORTED_CLIENT_MAX_WINDOW_BITS) {
      throw new ProtocolException(
          "Invalid option value. " + SUPPORTED_CLIENT_MAX_WINDOW_BITS
              + "is the only supported value for '" + CLIENT_MAX_WINDOW_BITS.id + "'. "
              + "Actual: " + maxWindowBits);
    }

    if (option == SERVER_MAX_WINDOW_BITS
        && (maxWindowBits < MIN_MAX_WINDOW_BITS || MAX_MAX_WINDOW_BITS < maxWindowBits)) {
      throw new ProtocolException(
          "Invalid option value. '"
              + SERVER_MAX_WINDOW_BITS.id + "' must be in [" + MIN_MAX_WINDOW_BITS + ","
              + MAX_MAX_WINDOW_BITS + "]. Actual: " + maxWindowBits);
    }
  }
}
