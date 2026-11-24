package org.mishnatyosef.pickup;

/** תשובת HTTP: קוד וגוף. */
final class HttpResult {
    final int code; final String body;
    HttpResult(int code, String body) { this.code = code; this.body = body == null ? "" : body; }
}
