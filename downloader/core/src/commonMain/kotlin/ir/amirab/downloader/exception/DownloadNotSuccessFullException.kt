package ir.amirab.downloader.exception

import java.io.IOException

class UnSuccessfulResponseException(
    val code: Int,
    val msg: String,
    val retryAfterHeader: String? = null,
) : IOException(
    "$code | $msg"
)
