package ir.amirab.downloader.downloaditem.http

import java.util.Locale

/** Whether an HTTP validator can safely identify bytes across a rotated download URL. */
internal fun isStrongHttpETag(value: String?): Boolean {
    val tag = value?.trim() ?: return false
    return tag.length >= 3 && tag.first() == '"' && tag.last() == '"' &&
        !tag.startsWith("W/", ignoreCase = true)
}

/**
 * A URL fragment is never sent to the server. For any other link change we only
 * preserve staged bytes when a strong server validator and known length exist.
 * The caller must still revalidate both against the new response before resuming.
 */
internal fun canReuseHttpPartialAfterLinkChange(
    previousUrl: String,
    nextUrl: String,
    savedETag: String?,
    savedContentLength: Long,
): Boolean {
    if (previousUrl.substringBefore('#') == nextUrl.substringBefore('#')) return true
    return savedContentLength > 0 && isStrongHttpETag(savedETag) &&
        sameHttpResourceLocation(previousUrl, nextUrl)
}

/**
 * Ignore known expiring signature parameters, but retain non-credential query
 * selectors. Rotating a signature is not proof that two payloads are identical:
 * canReuseHttpPartialAfterLinkChange additionally requires a strong validator.
 */
internal fun sameHttpResourceLocation(firstUrl: String, secondUrl: String): Boolean {
    fun isEphemeralCredentialParameter(name: String): Boolean {
        val key = name.lowercase(Locale.ROOT)
        return key.startsWith("x-amz-") || key.startsWith("x-goog-") ||
            key in setOf(
                "token", "access_token", "signature", "sig", "expires",
                "expiry", "expiration", "policy", "key-pair-id", "googleaccessid",
            )
    }

    fun resourceIdentity(url: String): Pair<String, List<String>> {
        val withoutFragment = url.substringBefore('#')
        val withoutQuery = withoutFragment.substringBefore('?')
        val schemeSeparator = withoutQuery.indexOf("://")
        val location = if (schemeSeparator < 0) {
            withoutQuery
        } else {
            val scheme = withoutQuery.substring(0, schemeSeparator).lowercase(Locale.ROOT)
            val authorityAndPath = withoutQuery.substring(schemeSeparator + 3)
            val pathStart = authorityAndPath.indexOf('/')
            val authority = if (pathStart < 0) authorityAndPath else authorityAndPath.substring(0, pathStart)
            val path = if (pathStart < 0) "" else authorityAndPath.substring(pathStart)
            "$scheme://${authority.lowercase(Locale.ROOT)}$path"
        }
        val persistentQuery = withoutFragment.substringAfter('?', "")
            .split('&')
            .filter { parameter ->
                parameter.isNotEmpty() &&
                    !isEphemeralCredentialParameter(parameter.substringBefore('='))
            }
            .sorted()
        return location to persistentQuery
    }

    return resourceIdentity(firstUrl) == resourceIdentity(secondUrl)
}
