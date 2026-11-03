package ir.amirab.downloader.aria2

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.nio.charset.StandardCharsets
import java.time.Duration
import java.util.Collections
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class Aria2RpcClientTest {
    private val servers = mutableListOf<HttpServer>()

    @AfterTest
    fun stopServers() {
        servers.forEach { it.stop(0) }
    }

    @Test
    fun serializesAuthenticatedMethodsAndParsesTypedResults() {
        val requests = Collections.synchronizedList(mutableListOf<JsonObject>())
        val secret = Aria2SessionSecret.generate()
        val server = server { exchange, request ->
            requests += request
            val method = request.getValue("method").jsonPrimitive.content
            val params = request.getValue("params").jsonArray
            assertEquals(secret.rpcToken(), params.first().jsonPrimitive.content)
            val result: JsonElement = when (method) {
                "aria2.getVersion" -> JsonObject(
                    mapOf(
                        "version" to JsonPrimitive("1.37.0"),
                        "enabledFeatures" to JsonArray(listOf(JsonPrimitive("HTTPS"), JsonPrimitive("BitTorrent"))),
                    )
                )
                "aria2.addUri" -> JsonPrimitive("ABCDEF0123456789")
                "aria2.tellStatus" -> JsonObject(
                    mapOf(
                        "gid" to JsonPrimitive("abcdef0123456789"),
                        "status" to JsonPrimitive("active"),
                        "completedLength" to JsonPrimitive("1024"),
                        "totalLength" to JsonPrimitive("4096"),
                        "downloadSpeed" to JsonPrimitive("512"),
                        "errorCode" to JsonPrimitive(""),
                        "errorMessage" to JsonPrimitive("sensitive remote text"),
                    )
                )
                "aria2.pause", "aria2.unpause", "aria2.remove" ->
                    JsonPrimitive("abcdef0123456789")
                "aria2.removeDownloadResult", "aria2.shutdown" -> JsonPrimitive("OK")
                else -> error("unexpected method")
            }
            exchange.respond(request.response(result = result))
        }
        val client = client(server, secret)

        assertEquals("1.37.0", client.getVersion().version)
        assertEquals(listOf("HTTPS", "BitTorrent"), client.getVersion().enabledFeatures)
        assertEquals(
            "abcdef0123456789",
            client.addUri("https://example.invalid/private?token=secret", mapOf("dir" to "C:/Downloads")),
        )
        val status = client.tellStatus("ABCDEF0123456789")
        assertEquals(Aria2TransferState.ACTIVE, status.state)
        assertEquals(1024, status.completedLength)
        assertEquals(4096, status.totalLength)
        assertEquals(512, status.downloadSpeed)
        assertFalse(status.toString().contains("sensitive remote text"))
        assertEquals("abcdef0123456789", client.pause(status.gid))
        assertEquals("abcdef0123456789", client.unpause(status.gid))
        assertEquals("abcdef0123456789", client.remove(status.gid))
        client.removeDownloadResult(status.gid)
        client.shutdown()

        assertEquals(
            listOf(
                "aria2.getVersion", "aria2.getVersion", "aria2.addUri", "aria2.tellStatus",
                "aria2.pause", "aria2.unpause", "aria2.remove", "aria2.removeDownloadResult",
                "aria2.shutdown",
            ),
            requests.map { it.getValue("method").jsonPrimitive.content },
        )
        val addParams = requests[2].getValue("params").jsonArray
        assertEquals("https://example.invalid/private?token=secret", addParams[1].jsonArray.single().jsonPrimitive.content)
        assertEquals("C:/Downloads", addParams[2].jsonObject.getValue("dir").jsonPrimitive.content)
    }

    @Test
    fun rejectsMalformedJsonMismatchedIdsAndRemoteAuthenticationWithoutLeakingContent() {
        val secret = Aria2SessionSecret.generate()
        val sensitive = "https://user:password@example.invalid/file"
        val malformedServer = rawServer { "not-json $sensitive ${secret.rpcToken()}" }
        val malformed = assertFailsWith<Aria2RpcProtocolException> { client(malformedServer, secret).getVersion() }
        assertFalse(malformed.stackTraceToString().contains(sensitive))
        assertFalse(malformed.stackTraceToString().contains(secret.rpcToken()))

        val wrongIdServer = server { exchange, request ->
            exchange.respond("""{"jsonrpc":"2.0","id":999,"result":{"version":"1.37.0","enabledFeatures":[]}}""")
        }
        assertFailsWith<Aria2RpcProtocolException> { client(wrongIdServer, secret).getVersion() }

        val expected = secret.rpcToken()
        val authServer = server { exchange, request ->
            val supplied = request.getValue("params").jsonArray.first().jsonPrimitive.content
            val response = if (supplied == expected) {
                request.response(result = JsonObject(mapOf("version" to JsonPrimitive("1.37.0"), "enabledFeatures" to JsonArray(emptyList()))))
            } else {
                request.response(errorCode = 1, errorMessage = "Unauthorized $sensitive $supplied")
            }
            exchange.respond(response)
        }
        val remote = assertFailsWith<Aria2RpcRemoteException> {
            client(authServer, Aria2SessionSecret.generate()).getVersion()
        }
        assertEquals(1, remote.code)
        assertFalse(remote.stackTraceToString().contains(sensitive))
        assertFalse(remote.stackTraceToString().contains("token:"))
    }

    @Test
    fun acceptsOnlyLiteralIpv4LoopbackJsonRpcEndpoint() {
        val secret = Aria2SessionSecret.generate()
        listOf(
            "http://localhost:6800/jsonrpc",
            "http://0.0.0.0:6800/jsonrpc",
            "http://[::1]:6800/jsonrpc",
            "https://127.0.0.1:6800/jsonrpc",
            "http://127.0.0.1:6800/other",
            "http://127.0.0.1/jsonrpc",
            "http://user:password@127.0.0.1:6800/jsonrpc",
        ).forEach { value ->
            assertFailsWith<IllegalArgumentException>(value) {
                Aria2HttpRpcClient(URI(value), secret)
            }
        }
    }

    @Test
    fun rejectsInvalidStatusNumbersAndNeverEchoesResponseBody() {
        val secret = Aria2SessionSecret.generate()
        val server = server { exchange, request ->
            exchange.respond(
                request.response(
                    result = JsonObject(
                        mapOf(
                            "gid" to JsonPrimitive("1111111111111111"),
                            "status" to JsonPrimitive("active"),
                            "completedLength" to JsonPrimitive("-1"),
                            "totalLength" to JsonPrimitive("secret-value"),
                            "downloadSpeed" to JsonPrimitive("0"),
                        )
                    )
                )
            )
        }
        val failure = assertFailsWith<Aria2RpcProtocolException> {
            client(server, secret).tellStatus("1111111111111111")
        }
        assertFalse(failure.stackTraceToString().contains("secret-value"))
    }

    private fun client(server: HttpServer, secret: Aria2SessionSecret): Aria2HttpRpcClient =
        Aria2HttpRpcClient(
            URI("http://127.0.0.1:${server.address.port}/jsonrpc"),
            secret,
            Duration.ofSeconds(2),
        )

    private fun server(handler: (HttpExchange, JsonObject) -> Unit): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/jsonrpc") { exchange ->
            val body = exchange.requestBody.use { String(it.readAllBytes(), StandardCharsets.UTF_8) }
            handler(exchange, Json.parseToJsonElement(body).jsonObject)
        }
        server.start()
        servers += server
        return server
    }

    private fun rawServer(body: () -> String): HttpServer {
        val server = HttpServer.create(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0)
        server.createContext("/jsonrpc") { exchange -> exchange.respond(body()) }
        server.start()
        servers += server
        return server
    }

    private fun JsonObject.response(
        result: JsonElement? = null,
        errorCode: Int? = null,
        errorMessage: String = "error",
    ): String {
        val fields = linkedMapOf<String, JsonElement>(
            "jsonrpc" to JsonPrimitive("2.0"),
            "id" to getValue("id"),
        )
        if (errorCode == null) {
            fields["result"] = requireNotNull(result)
        } else {
            fields["error"] = JsonObject(
                mapOf("code" to JsonPrimitive(errorCode), "message" to JsonPrimitive(errorMessage))
            )
        }
        return JsonObject(fields).toString()
    }

    private fun HttpExchange.respond(body: String) {
        val bytes = body.toByteArray(StandardCharsets.UTF_8)
        responseHeaders.set("Content-Type", "application/json")
        sendResponseHeaders(200, bytes.size.toLong())
        responseBody.use { it.write(bytes) }
    }
}
