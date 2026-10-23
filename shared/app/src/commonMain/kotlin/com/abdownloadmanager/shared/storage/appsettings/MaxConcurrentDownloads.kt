package com.abdownloadmanager.shared.storage.appsettings

const val MIN_MAX_CONCURRENT_DOWNLOADS = 0

fun requireSupportedMaxConcurrentDownloads(value: Int): Int {
    require(value >= MIN_MAX_CONCURRENT_DOWNLOADS) {
        "Maximum concurrent downloads must be zero (unlimited) or a positive integer."
    }
    return value
}
