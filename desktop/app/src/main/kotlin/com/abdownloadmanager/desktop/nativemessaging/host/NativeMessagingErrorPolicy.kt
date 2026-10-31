package com.abdownloadmanager.desktop.nativemessaging.host

/** Browser-facing errors must never contain URLs, headers, credentials or exception text. */
internal data class SafeBrowserError(val errorType: String, val message: String)

internal fun safeBrowserError(cause: Throwable): SafeBrowserError =
    if (cause is IllegalArgumentException) {
        SafeBrowserError("INVALID_REQUEST", "HyperGet could not process the browser request.")
    } else {
        SafeBrowserError("REQUEST_FAILED", "HyperGet could not complete the browser request.")
    }

/** Preserve the browser's correlation ID for an error after a decoded request. */
internal fun browserReplyId(requestId: String?, newId: () -> String): String =
    requestId ?: newId()
