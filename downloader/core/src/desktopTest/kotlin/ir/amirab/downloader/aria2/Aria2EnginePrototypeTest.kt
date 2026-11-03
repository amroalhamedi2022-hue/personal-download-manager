package ir.amirab.downloader.aria2

import java.security.SecureRandom
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive

class Aria2EnginePrototypeTest {
    @Test
    fun registryKeepsHyperGetIdentityIndependentFromAria2Gid() {
        val registry = Aria2TaskRegistry()
        registry.bind(Aria2TaskBinding(41, "ABCDEF0123456789"))

        assertEquals(41, registry.bindingForGid("abcdef0123456789")?.hyperGetTaskId)
        assertEquals("abcdef0123456789", registry.bindingForTask(41)?.aria2Gid)
    }

    @Test
    fun registryRejectsDuplicateTaskOrGidOwnership() {
        val registry = Aria2TaskRegistry(listOf(Aria2TaskBinding(1, "1111111111111111")))

        assertFailsWith<IllegalArgumentException> {
            registry.bind(Aria2TaskBinding(1, "2222222222222222"))
        }
        assertFailsWith<IllegalArgumentException> {
            registry.bind(Aria2TaskBinding(2, "1111111111111111"))
        }
    }

    @Test
    fun removalClearsBothLookupDirections() {
        val registry = Aria2TaskRegistry(listOf(Aria2TaskBinding(7, "7777777777777777")))

        assertEquals(7, registry.removeTask(7)?.hyperGetTaskId)
        assertEquals(null, registry.bindingForTask(7))
        assertEquals(null, registry.bindingForGid("7777777777777777"))
    }

    @Test
    fun restoredSnapshotIsStableAndSorted() {
        val registry = Aria2TaskRegistry(
            listOf(Aria2TaskBinding(9, "9999999999999999"), Aria2TaskBinding(3, "3333333333333333"))
        )

        assertEquals(listOf(3L, 9L), registry.snapshot().map { it.hyperGetTaskId })
        assertEquals(
            registry.snapshot(),
            Json.decodeFromString<List<Aria2TaskBinding>>(Json.encodeToString(registry.snapshot())),
        )
    }

    @Test
    fun progressNeverMovesBackwardsWhenRpcSamplesArriveLate() {
        val progress = Aria2MonotonicProgress()
        progress.accept(Aria2ProgressSample(Aria2TransferState.ACTIVE, 800, 1_000, 100))
        val accepted = progress.accept(Aria2ProgressSample(Aria2TransferState.ACTIVE, 700, 900, 80))

        assertEquals(800, accepted.completedLength)
        assertEquals(1_000, accepted.totalLength)
        assertEquals(80, accepted.downloadSpeed)
    }

    @Test
    fun terminalProgressCannotBeReactivatedByALateRpcSample() {
        val progress = Aria2MonotonicProgress()
        progress.accept(Aria2ProgressSample(Aria2TransferState.COMPLETE, 1_000, 1_000, 0))

        val accepted = progress.accept(
            Aria2ProgressSample(Aria2TransferState.ACTIVE, 900, 1_000, 250)
        )

        assertEquals(Aria2TransferState.COMPLETE, accepted.state)
        assertEquals(1_000, accepted.completedLength)
        assertEquals(0, accepted.downloadSpeed)
    }

    @Test
    fun allDocumentedAria2StatusesMapExplicitly() {
        assertEquals(Aria2TransferState.WAITING, aria2TransferState("waiting"))
        assertEquals(Aria2TransferState.ACTIVE, aria2TransferState("active"))
        assertEquals(Aria2TransferState.PAUSED, aria2TransferState("paused"))
        assertEquals(Aria2TransferState.COMPLETE, aria2TransferState("complete"))
        assertEquals(Aria2TransferState.ERROR, aria2TransferState("error"))
        assertEquals(Aria2TransferState.REMOVED, aria2TransferState("removed"))
        assertFailsWith<IllegalStateException> { aria2TransferState("mystery") }
    }

    @Test
    fun featureFlagIsOffUnlessExplicitlyEnabled() {
        assertFalse(Aria2EngineFeatureFlag { null }.isEnabled())
        assertFalse(Aria2EngineFeatureFlag { "false" }.isEnabled())
        assertTrue(Aria2EngineFeatureFlag { "TRUE" }.isEnabled())
    }

    @Test
    fun sessionSecretsAreRandomAndNeverRendered() {
        val first = Aria2SessionSecret.generate(SecureRandom())
        val second = Aria2SessionSecret.generate(SecureRandom())

        assertNotEquals(first.rpcToken(), second.rpcToken())
        assertEquals(70, first.rpcToken().length)
        assertFalse(first.toString().contains(first.rpcToken().removePrefix("token:")))
    }

    @Test
    fun rpcFactoryAuthenticatesEveryRequestButRedactsLogs() {
        val secret = Aria2SessionSecret.generate()
        val request = Aria2RpcRequestFactory.create(
            secret = secret,
            id = 12,
            method = "aria2.addUri",
            params = listOf(JsonPrimitive("https://example.invalid/file.bin")),
        )
        val wire = Json.encodeToString(request)

        assertTrue(wire.contains("token:"))
        assertTrue(wire.contains("aria2.addUri"))
        assertFalse(request.toString().contains("token:"))
        assertFalse(request.toString().contains("example.invalid"))
    }

    @Test
    fun rpcFactoryRejectsUnscopedMethods() {
        assertFailsWith<IllegalArgumentException> {
            Aria2RpcRequestFactory.create(Aria2SessionSecret.generate(), 1, "evil.method")
        }
    }
}
