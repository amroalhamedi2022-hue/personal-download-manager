package com.abdownloadmanager.desktop.utils.singleInstance.service

// Error categories are intentionally coarse: exception messages can contain
// URLs, paths, credentials, and remote server responses. Never send those to
// the CLI or CI logs. Reject malformed/unexpected mapper values.
private val safeFailureTypePattern = Regex("[A-Za-z_$][A-Za-z0-9_.$]{0,95}")

internal fun safeFailureType(raw: String?): String =
    raw?.takeIf(safeFailureTypePattern::matches) ?: "Unclassified"
