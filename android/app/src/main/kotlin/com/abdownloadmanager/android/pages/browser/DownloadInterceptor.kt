package com.abdownloadmanager.android.pages.browser

import android.webkit.CookieManager
import com.abdownloadmanager.android.ui.widget.WebViewState
import com.abdownloadmanager.shared.pages.adddownload.AddDownloadCredentialsInUiProps
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.util.HttpUrlUtils

typealias ABDMWebRequestId = String

data class ABDMWebRequest(
    val url: String,
    val headers: Map<String, String>,
    val page: String?,
) {
    val id: ABDMWebRequestId = url
}

interface RequestInterceptor {
    fun interceptRequest(tabId: String, request: ABDMWebRequest)
}

class DownloadInterceptor(
    private val onNewDownload: (newDownloads: List<AddDownloadCredentialsInUiProps>) -> Unit,
) : RequestInterceptor {
    private val requests = BrowserRequestCache<ABDMWebRequest>()

    fun onDownloadStart(
        url: String?,
        userAgent: String?,
        page: String?,
        tab: ABDMBrowserTab,
    ) {
        if (url == null) {
            return
        }
        if (!HttpUrlUtils.isValidUrl(url)) {
            return
        }
        val webRequest = getWebRequestOrDefault(
            url = url,
            userAgent = userAgent,
            page = page,
            webViewState = tab.tabState,
            tabId = tab.tabId,
        )
        onNewDownload(
            listOf(
                AddDownloadCredentialsInUiProps(
                    HttpDownloadCredentials(
                        link = webRequest.url,
                        headers = webRequest.headers,
                        downloadPage = webRequest.page,
                    ),
                    AddDownloadCredentialsInUiProps.Configs()
                )
            )
        )
    }

    override fun interceptRequest(tabId: String, request: ABDMWebRequest) {
        requests.put(tabId, request.url, request.copy(headers = request.headers.toMap()))
    }

    fun clearTab(tabId: String) = requests.clearTab(tabId)

    private fun getWebRequestOrDefault(
        url: String,
        userAgent: String?,
        page: String?,
        webViewState: WebViewState,
        tabId: String,
    ): ABDMWebRequest {
        var request = requests.take(tabId, url)
        if (request == null) {
            request = ABDMWebRequest(
                url = url,
                headers = emptyMap(),
                page = getPageUrl(webViewState) ?: page,
            )
        }
        return request
            .withUserAgent(userAgent)
            .withCookieManagerCookies()
    }

    private fun ABDMWebRequest.withUserAgent(userAgent: String?): ABDMWebRequest {
        val request = this
        if (userAgent == null) {
            return request
        }
        val userAgentKey = "User-Agent"
        if (request.headers.keys.any { it.equals(userAgentKey, ignoreCase = true) }) {
            return request
        }
        return request.copy(
            headers = request.headers.plus(
                userAgentKey to userAgent
            )
        )
    }

    private fun ABDMWebRequest.withCookieManagerCookies(): ABDMWebRequest {
        val request = this
        val cookieFromCookieManager =
            CookieManager.getInstance().getCookie(url)?.takeIf { it.isNotBlank() } ?: return request
        val cookieKey = "Cookie"
        // Prefer the actual intercepted Cookie header; never append duplicate cookies.
        if (request.headers.any { (key, value) -> key.equals(cookieKey, true) && value.isNotBlank() }) {
            return request
        }
        return request.copy(headers = request.headers.filterKeys { !it.equals(cookieKey, true) } +
            (cookieKey to cookieFromCookieManager))
    }

    private fun getPageUrl(state: WebViewState): String? {
        return state.lastLoadedUrl
    }

}
