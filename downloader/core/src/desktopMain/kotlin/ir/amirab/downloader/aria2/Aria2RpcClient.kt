package ir.amirab.downloader.aria2

import java.io.IOException
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.ProxySelector
import java.net.SocketAddress
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.atomic.AtomicLong
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

interface Aria2RpcApi {
    fun getVersion(): Aria2Version
    fun addUri(uri: String, options: Map<String, String> = emptyMap()): String
    fun tellStatus(gid: String): Aria2Status
    fun pause(gid: String): String
    fun unpause(gid: String): String
    fun remove(gid: String): String
    fun removeDownloadResult(gid: String)
    fun shutdown()
}

data class Aria2Version(
    val version: String,
    val enabledFeatures: List<String>,
)

data class Aria2Status(
    val gid: String,
    val state: Aria2TransferState,
    val completedLength: Long,
    val totalLength: Long,
    val downloadSpeed: Long,
    val errorCode: String?,
    val errorMessage: String?,
) {
    override fun toString(): String =
        "Aria2Status(gid=$gid, state=$state, completedLength=$completedLength, " +
            "totalLength=$totalLength, downloadSpeed=$downloadSpeed, errorCode=$errorCode, " +
            "errorMessage=REDACTED)"
}

sealed class Aria2RpcException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)

class Aria2RpcTransportException(cause: Throwable? = null) :
    Aria2RpcException("aria2 RPC transport failed.", cause)

class Aria2RpcProtocolException(message: String = "Invalid aria2 JSON-RPC response.") :
    Aria2RpcException(message)

class Aria2RpcRemoteException(val code: Int) :
    Aria2RpcException("aria2 RPC rejected the request (code=$code).")

/**
 * Minimal authenticated JSON-RPC client for the application-owned aria2 process.
 *
 * The endpoint is deliberately restricted to the IPv4 loopback literal. The
 * client also installs a direct-only ProxySelector so a session secret can never
 * be forwarded to an inherited desktop or CI proxy.
 */
class Aria2HttpRpcClient(
    endpoint: URI,
    private val secret: Aria2SessionSecret,
    private val timeout: Duration = Duration.ofSeconds(5),
    private val json: Json = Json { ignoreUnknownKeys = false },
    private val httpClient: HttpClient = defaultHttpClient(timeout),
) : Aria2RpcApi {
    private val endpoint = endpoint.validateLoopbackRpcEndpoint()
    private val nextId = AtomicLong(0)

    init {
        require(!timeout.isZero && !timeout.isNegative) { "RPC timeout must be positive." }
    }

    override fun getVersion(): Aria2Version {
        val value = invoke("aria2.getVersion").requiredObject()
        return Aria2Version(
            version = value.requiredString("version"),
            enabledFeatures = value["enabledFeatures"].requiredArray("enabledFeatures").map {
                it.requiredStringValue("enabledFeatures entry")
            },
        )
    }

    override fun addUri(uri: String, options: Map<String, String>): String {
        require(uri.isNotBlank()) { "Download URI cannot be blank." }
        val optionObject = JsonObject(options.mapValues { JsonPrimitive(it.value) })
        return invoke(
            "aria2.addUri",
            listOf(JsonArray(listOf(JsonPrimitive(uri))), optionObject),
        ).requiredStringValue("aria2.addUri result").requireGid()
    }

    override fun tellStatus(gid: String): Aria2Status {
        val normalizedGid = gid.requireGid()
        val keys = JsonArray(
            listOf("gid", "status", "completedLength", "totalLength", "downloadSpeed", "errorCode", "errorMessage")
                .map(::JsonPrimitive)
        )
        val value = invoke("aria2.tellStatus", listOf(JsonPrimitive(normalizedGid), keys)).requiredObject()
        val returnedGid = value.requiredString("gid").requireGid()
        if (returnedGid != normalizedGid) {
            throw Aria2RpcProtocolException("aria2 returned a mismatched GID.")
        }
        return Aria2Status(
            gid = returnedGid,
            state = try {
                aria2TransferState(value.requiredString("status"))
            } catch (_: IllegalStateException) {
                throw Aria2RpcProtocolException("aria2 returned an unknown transfer status.")
            },
            completedLength = value.requiredNonNegativeLongString("completedLength"),
            totalLength = value.requiredNonNegativeLongString("totalLength"),
            downloadSpeed = value.requiredNonNegativeLongString("downloadSpeed"),
            errorCode = value.optionalString("errorCode"),
            errorMessage = value.optionalString("errorMessage"),
        )
    }

    override fun pause(gid: String): String = gidCommand("aria2.pause", gid)

    override fun unpause(gid: String): String = gidCommand("aria2.unpause", gid)

    override fun remove(gid: String): String = gidCommand("aria2.remove", gid)

    override fun removeDownloadResult(gid: String) {
        val normalizedGid = gid.requireGid()
        val result = invoke("aria2.removeDownloadResult", listOf(JsonPrimitive(normalizedGid)))
            .requiredStringValue("aria2.removeDownloadResult result")
        if (result != "OK") {
            throw Aria2RpcProtocolException("aria2 did not acknowledge result removal.")
        }
    }

    override fun shutdown() {
        val result = invoke("aria2.shutdown").requiredStringValue("aria2.shutdown result")
        if (result != "OK") throw Aria2RpcProtocolException("aria2 did not acknowledge shutdown.")
    }

    private fun gidCommand(method: String, gid: String): String {
        val normalizedGid = gid.requireGid()
        val returned = invoke(method, listOf(JsonPrimitive(normalizedGid)))
            .requiredStringValue("$method result")
            .requireGid()
        if (returned != normalizedGid) throw Aria2RpcProtocolException("aria2 returned a mismatched GID.")
        return returned
    }

    private fun invoke(method: String, params: List<JsonElement> = emptyList()): JsonElement {
        val id = nextId.incrementAndGet()
        val rpcRequest = Aria2RpcRequestFactory.create(secret, id, method, params)
        val requestBody = json.encodeToString(rpcRequest)
        val request = HttpRequest.newBuilder(endpoint)
            .timeout(timeout)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build()
        val response = try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString())
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw Aria2RpcTransportException(e)
        } catch (e: IOException) {
            throw Aria2RpcTransportException(e)
        }
        if (response.statusCode() != 200) throw Aria2RpcTransportException()

        val root = try {
            json.parseToJsonElement(response.body()).jsonObject
        } catch (_: SerializationException) {
            throw Aria2RpcProtocolException("Malformed aria2 JSON-RPC response.")
        } catch (_: IllegalArgumentException) {
            throw Aria2RpcProtocolException("Malformed aria2 JSON-RPC response.")
        }
        if (root["jsonrpc"]?.jsonPrimitive?.contentOrNull != "2.0") {
            throw Aria2RpcProtocolException()
        }
        if (root["id"]?.jsonPrimitive?.longOrNull != id) {
            throw Aria2RpcProtocolException("aria2 returned a mismatched request ID.")
        }
        val result = root["result"]
        val error = root["error"]
        if ((result == null) == (error == null)) throw Aria2RpcProtocolException()
        if (error != null) {
            val errorObject = try {
                error.jsonObject
            } catch (_: IllegalArgumentException) {
                throw Aria2RpcProtocolException()
            }
            val code = errorObject["code"]?.jsonPrimitive?.longOrNull
                ?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }
                ?.toInt()
                ?: throw Aria2RpcProtocolException()
            throw Aria2RpcRemoteException(code)
        }
        return result!!
    }

    private companion object {
        fun defaultHttpClient(timeout: Duration): HttpClient = HttpClient.newBuilder()
            .connectTimeout(timeout)
            .followRedirects(HttpClient.Redirect.NEVER)
            .proxy(DirectOnlyProxySelector)
            .build()
    }
}

private object DirectOnlyProxySelector : ProxySelector() {
    override fun select(uri: URI?): List<Proxy> = listOf(Proxy.NO_PROXY)
    override fun connectFailed(uri: URI?, sa: SocketAddress?, ioe: IOException?) = Unit
}

private fun URI.validateLoopbackRpcEndpoint(): URI {
    require(scheme == "http") { "aria2 RPC must use plain HTTP on application-owned loopback." }
    require(host == "127.0.0.1") { "aria2 RPC must use the 127.0.0.1 IPv4 loopback literal." }
    require(port in 1..65535) { "aria2 RPC endpoint must specify a valid port." }
    require(userInfo == null && query == null && fragment == null) { "aria2 RPC endpoint cannot contain credentials or metadata." }
    require(path == "/jsonrpc") { "aria2 RPC endpoint path must be /jsonrpc." }
    return this
}

private val ARIA2_GID = Regex("^[0-9a-fA-F]{16}$")

private fun String.requireGid(): String {
    require(ARIA2_GID.matches(this)) { "aria2 GID must contain 16 hexadecimal characters." }
    return lowercase()
}

private fun JsonElement.requiredObject(): JsonObject = try {
    jsonObject
} catch (_: IllegalArgumentException) {
    throw Aria2RpcProtocolException()
}

private fun JsonObject.requiredString(name: String): String =
    this[name]?.requiredStringValue(name) ?: throw Aria2RpcProtocolException("aria2 response is missing $name.")

private fun JsonObject.optionalString(name: String): String? = this[name]?.let {
    if (it is JsonPrimitive && it.isString) it.content else throw Aria2RpcProtocolException("aria2 response has invalid $name.")
}

private fun JsonObject.requiredNonNegativeLongString(name: String): Long {
    val value = requiredString(name).toLongOrNull()
        ?: throw Aria2RpcProtocolException("aria2 response has invalid $name.")
    if (value < 0) throw Aria2RpcProtocolException("aria2 response has invalid $name.")
    return value
}

private fun JsonElement?.requiredArray(name: String): JsonArray = this as? JsonArray
    ?: throw Aria2RpcProtocolException("aria2 response has invalid $name.")

private fun JsonElement.requiredStringValue(name: String): String =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.content
        ?: throw Aria2RpcProtocolException("aria2 response has invalid $name.")
