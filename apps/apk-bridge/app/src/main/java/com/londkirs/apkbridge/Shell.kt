package com.londkirs.apkbridge

/** Single-quotes a value for the device shell, so spaces, quotes or `;` in it stay plain text. */
fun shellQuote(value: String): String = "'" + value.replace("'", "'\\''") + "'"
