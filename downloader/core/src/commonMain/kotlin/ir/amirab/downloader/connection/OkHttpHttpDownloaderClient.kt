package ir.amirab.downloader.connection

import ir.amirab.downloader.connection.proxy.*
import ir.amirab.downloader.connection.response.HttpResponseInfo
import ir.amirab.downloader.downloaditem.http.IHttpBasedDownloadCredentials
import ir.amirab.downloader.downloaditem.http.IHttpDownloadCredentials
import ir.amirab.downloader.downloaditem.http.HttpDownloadCredentials
import ir.amirab.downloader.utils.await
import okhttp3.*
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector

private const val ORIGINAL_RETRY_AFTER_HEADER =
    "X-Personal-Download-Manager-Original-Retry-After"

class OkHttpHttpDownloaderClient(
    private val okHttpClient: OkHttpClient,
    private val customUserAgentProvider: UserAgentProvider,
    private val proxyStrategyProvider: ProxyStrategyProvider,
    private val systemProxySelectorProvider: SystemProxySelectorProvider,
    private val autoConfigurableProxyProvider: AutoConfigurableProxyProvider,
) : HttpDownloaderClient() {
    // Job retry persistence is authoritative: an invisible OkHttp network retry
    // must not consume more requests than the user-visible retry budget tracks.
    private val singleAttemptClient = okHttpClient.newBuilder()
        .retryOnConnectionFailure(false)
        .addNetworkInterceptor { chain ->
            val response = chain.proceed(chain.request())
            if (response.code == 503 && response.header("Retry-After")?.trim() == "0") {
                response.newBuilder()
                    .header(ORIGINAL_RETRY_AFTER_HEADER, "0")
                    .header("Retry-After", "1")
                    .build()
            } else response
        }
        .build()

    // Cookie-bearing/authenticated HLS requests must never redirect with
    // caller-supplied credentials or inherited ambient cookies.
    private val noRedirectClient = singleAttemptClient.newBuilder()
        .cookieJar(CookieJar.NO_COOKIES)
        .followRedirects(false)
        .followSslRedirects(false)
        .build()

    private fun newCall(
        downloadCredentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
        extraBuilder: Request.Builder.() -> Unit,
    ): Call {
        val rangeHeader = start?.let {
            createRangeHeader(start, end)
        }
        val requestClient = if (
            downloadCredentials is HttpDownloadCredentials && !downloadCredentials.followRedirects
        ) noRedirectClient else singleAttemptClient
        return requestClient
            .applyProxy(downloadCredentials)
            .newCall(
                Request.Builder()
                    .url(downloadCredentials.link)
                    .apply {
                        // we don't to add something that we sure that it will be overridden later
                        if (downloadCredentials.userAgent == null) {
                            // only add default user agent if we don't specify it
                            val customUserAgent = customUserAgentProvider.getUserAgent()
                                ?: getDefaultUserAgent()
                            header("User-Agent", customUserAgent)
                        }
                        downloadCredentials.headers
                            ?.filter {
                                //OkHttp handles this header and if we override it,
                                //makes redirected links to have this "Host" instead of their own!, and cause error
                                !it.key.equals("Host", true)
                            }
                            ?.forEach { (k, v) ->
                                header(k, v)
                            }
                        // Keep identity after caller headers to protect byte
                        // ranges from transparent content decompression, while
                        // avoiding two temporary maps per HTTP connection.
                        header("accept-encoding", "identity")
                        val username = downloadCredentials.username
                        val password = downloadCredentials.password
                        if (username?.isNotBlank() == true && password?.isNotBlank() == true) {
                            header("Authorization", Credentials.basic(username, password))
                        }
                        downloadCredentials.userAgent?.let { userAgent ->
                            header("User-Agent", userAgent)
                        }
                    }
                    .apply(extraBuilder)
                    .apply {
                        if (rangeHeader != null) {
                            header(rangeHeader.first, rangeHeader.second)
                        }
                    }
                    .build()
            )
    }

    private fun OkHttpClient.applyProxy(
        downloadCredentials: IHttpBasedDownloadCredentials,
    ): OkHttpClient {
        return when (
            val strategy = proxyStrategyProvider.getProxyStrategyFor(downloadCredentials.link)
        ) {
            ProxyStrategy.Direct -> return this
            ProxyStrategy.UseSystem -> {
                withSystemProxySelector(
                    systemProxySelectorProvider.getSystemProxySelector()
                        ?: ProxySelector.getDefault()
                )
            }

            is ProxyStrategy.ByScript -> {
                val proxySelector = autoConfigurableProxyProvider.getAutoConfigurableProxy(strategy.scriptPath)
                if (proxySelector != null) {
                    newBuilder()
                        .proxySelector(proxySelector)
                        .build()
                } else {
                    this
                }
            }

            is ProxyStrategy.ManualProxy -> {
                val proxy = strategy.proxy
                return newBuilder()
                    .proxy(
                        Proxy(
                            when (proxy.type) {
                                ProxyType.HTTP -> Proxy.Type.HTTP
                                ProxyType.SOCKS -> Proxy.Type.SOCKS
                            },
                            InetSocketAddress(proxy.host, proxy.port)
                        )
                    ).let {
                        if (proxy.username != null && proxy.type == ProxyType.HTTP) {
                            it.proxyAuthenticator { _, r ->
                                val credentials = Credentials.basic(
                                    proxy.username,
                                    proxy.password.orEmpty()
                                )
                                r.request
                                    .newBuilder()
                                    .header("Proxy-Authorization", credentials)
                                    .build()
                            }
                        } else {
                            it
                        }
                    }.build()
            }
        }
    }


    override suspend fun actualHead(
        credentials: IHttpDownloadCredentials,
        start: Long?,
        end: Long?,
    ): HttpResponseInfo {
        newCall(
            downloadCredentials = credentials,
            start = start,
            end = end,
            extraBuilder = {
//                head()
            }
        ).await().use { response ->
//            println(response.headers)
            return createFileInfo(response)
        }
    }

    private fun createFileInfo(response: Response): HttpResponseInfo {
        val responseHeaders = response.headers.associate { (key, value) ->
            key.lowercase() to value
        }.toMutableMap()
        responseHeaders.remove(ORIGINAL_RETRY_AFTER_HEADER.lowercase())?.let {
            responseHeaders["retry-after"] = it
        }
        return HttpResponseInfo(
            statusCode = response.code,
            message = response.message,
            requestUrl = response.request.url.toString(),
            requestHeaders = response.request.headers.associate { (key, value) ->
                key.lowercase() to value
            },
            responseHeaders = responseHeaders,
        )
    }

    override suspend fun actualConnect(
        credentials: IHttpBasedDownloadCredentials,
        start: Long?,
        end: Long?,
    ): Connection<HttpResponseInfo> {
        val call = newCall(
            downloadCredentials = credentials,
            start = start,
            end = end,
            extraBuilder = {
                get()
            }
        )
        val response = call.await()
        val body = runCatching {
            requireNotNull(response.body) {
                "body is null"
            }
        }.onFailure {
            call.cancel()
            response.close()
        }.getOrThrow()
        return Connection(
            source = body.source(),
            contentLength = body.contentLength(),
            responseInfo = createFileInfo(response),
            cancelTransport = { call.cancel() }
        )
    }
}

/**
 * Reuse an OkHttp client when the effective system proxy selector has not
 * changed. A new client per HTTP range request otherwise creates unnecessary
 * builders and connection metadata for 16 active downloads.
 *
 * Selector identity (not equality) ensures live proxy configuration changes
 * still get a rebuilt client and all inherited dispatcher/pool settings.
 */
internal fun OkHttpClient.withSystemProxySelector(selector: ProxySelector): OkHttpClient =
    if (proxySelector === selector) this else newBuilder().proxySelector(selector).build()
