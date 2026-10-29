package ir.amirab.downloader.aria2

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class Aria2EngineAdapterTest {
    @Test
    fun addIsIdempotentAndPersistsOnlyTaskToGidMapping() {
        val root = Files.createTempDirectory("aria2-adapter")
        val rpc = FakeRpc()
        val storePath = root.resolve("bindings.json")
        val store = Aria2BindingStore(storePath)
        val adapter = Aria2EngineAdapter.restore(rpc, store)
        val request = Aria2AddRequest(41, "https://example.invalid/file.bin", root, "file.bin")

        assertEquals(adapter.add(request), adapter.add(request))
        assertEquals(1, rpc.addCalls)
        val persisted = Files.readString(storePath)
        assertFalse(persisted.contains("example.invalid"))
        assertFalse(persisted.contains("token:"))
        assertEquals(listOf(41L), Aria2BindingStore(storePath).load().map { it.hyperGetTaskId })
    }

    @Test
    fun statusIsMonotonicPerTaskAndDoesNotExposeRawErrorMessage() {
        val root = Files.createTempDirectory("aria2-status")
        val rpc = FakeRpc()
        val adapter = Aria2EngineAdapter.restore(rpc, Aria2BindingStore(root.resolve("bindings.json")))
        adapter.add(Aria2AddRequest(7, "https://example.invalid/a", root, "a.bin"))
        rpc.status = rpc.status.copy(completedLength = 900, totalLength = 1_000, downloadSpeed = 12)
        adapter.status(7)
        rpc.status = rpc.status.copy(
            state = Aria2TransferState.ERROR,
            completedLength = 800,
            totalLength = 900,
            errorCode = "3",
            errorMessage = "https://user:password@example.invalid token:secret",
        )

        val snapshot = adapter.status(7)
        assertEquals(900, snapshot.progress.completedLength)
        assertEquals(1_000, snapshot.progress.totalLength)
        assertEquals("3", snapshot.errorCode)
        assertTrue(snapshot.hasErrorMessage)
        assertFalse(snapshot.toString().contains("password"))
    }

    @Test
    fun removeTreatsStaleGidAsSuccessAndClearsDurableMapping() {
        val root = Files.createTempDirectory("aria2-stale")
        val store = Aria2BindingStore(root.resolve("bindings.json"))
        store.save(listOf(Aria2TaskBinding(2, FakeRpc.GID)))
        val rpc = FakeRpc().apply { removeFailure = Aria2RpcRemoteException(1) }
        val adapter = Aria2EngineAdapter.restore(rpc, store)

        assertTrue(adapter.remove(2))
        assertFalse(adapter.remove(2))
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun removeUsesResultStoreForCompletedTask() {
        val root = Files.createTempDirectory("aria2-complete")
        val rpc = FakeRpc().apply {
            status = status.copy(state = Aria2TransferState.COMPLETE)
        }
        val adapter = Aria2EngineAdapter.restore(rpc, Aria2BindingStore(root.resolve("bindings.json")))
        adapter.add(Aria2AddRequest(21, "https://example.invalid/complete", root, "complete.bin"))

        assertTrue(adapter.remove(21))
        assertEquals(0, rpc.removeCalls)
        assertEquals(1, rpc.removeResultCalls)
        assertTrue(adapter.bindings().isEmpty())
    }

    @Test
    fun removeHandlesCompletionRaceAfterActiveStatus() {
        val root = Files.createTempDirectory("aria2-remove-race")
        val rpc = FakeRpc().apply { removeFailure = Aria2RpcRemoteException(1) }
        val adapter = Aria2EngineAdapter.restore(rpc, Aria2BindingStore(root.resolve("bindings.json")))
        adapter.add(Aria2AddRequest(22, "https://example.invalid/race", root, "race.bin"))

        assertTrue(adapter.remove(22))
        assertEquals(1, rpc.removeCalls)
        assertEquals(1, rpc.removeResultCalls)
        assertTrue(adapter.bindings().isEmpty())
    }

    @Test
    fun reconcileRemovesOnlyExplicitlyStaleMappings() {
        val root = Files.createTempDirectory("aria2-restore")
        val store = Aria2BindingStore(root.resolve("bindings.json"))
        store.save(listOf(Aria2TaskBinding(3, FakeRpc.GID)))
        val rpc = FakeRpc().apply { tellFailure = Aria2RpcRemoteException(1) }
        val adapter = Aria2EngineAdapter.restore(rpc, store)

        assertTrue(adapter.reconcileRestored().isEmpty())
        assertTrue(store.load().isEmpty())
    }

    @Test
    fun rejectsDestinationCollisionBeforeCallingAria2() {
        val root = Files.createTempDirectory("aria2-collision")
        Files.writeString(root.resolve("taken.bin"), "user data")
        val rpc = FakeRpc()
        val adapter = Aria2EngineAdapter.restore(rpc, Aria2BindingStore(root.resolve("bindings.json")))

        assertFailsWith<IllegalArgumentException> {
            adapter.add(Aria2AddRequest(8, "https://example.invalid/a", root, "taken.bin"))
        }
        assertEquals(0, rpc.addCalls)
        assertEquals("user data", Files.readString(root.resolve("taken.bin")))
    }

    @Test
    fun routerKeepsFlagOffAndSpecializedSubsystemsOutOfAria2() {
        assertEquals(
            TransferEngineSelection.CURRENT_OR_SPECIALIZED,
            Aria2EngineRouter(Aria2EngineFeatureFlag { null }).select(Aria2Workload.HTTP),
        )
        val enabled = Aria2EngineRouter(Aria2EngineFeatureFlag { "true" })
        assertEquals(TransferEngineSelection.ARIA2, enabled.select(Aria2Workload.HTTP))
        assertEquals(TransferEngineSelection.ARIA2, enabled.select(Aria2Workload.TORRENT))
        assertEquals(TransferEngineSelection.CURRENT_OR_SPECIALIZED, enabled.select(Aria2Workload.HLS))
        assertEquals(
            TransferEngineSelection.CURRENT_OR_SPECIALIZED,
            enabled.select(Aria2Workload.BROWSER_NATIVE_MESSAGING),
        )
    }

    @Test
    fun bootstrapDoesNothingWhenFeatureFlagIsOff() {
        // The controller is intentionally not constructible without paths here:
        // the null result proves the flag is checked before lifecycle startup.
        val root = Files.createTempDirectory("aria2-bootstrap")
        val controller = Aria2ProcessController(
            Aria2ProcessConfig(root.resolve("missing"), root, root),
            rpcFactory = { _, _ -> FakeRpc() },
        )
        assertNull(
            Aria2EngineBootstrap.tryStart(
                Aria2EngineFeatureFlag { null },
                controller,
                Aria2BindingStore(root.resolve("bindings.json")),
            )
        )
    }

    private class FakeRpc : Aria2RpcApi {
        var addCalls = 0
        var removeCalls = 0
        var removeResultCalls = 0
        var removeFailure: Aria2RpcRemoteException? = null
        var tellFailure: Aria2RpcRemoteException? = null
        var status = Aria2Status(GID, Aria2TransferState.ACTIVE, 0, 0, 0, null, null)

        override fun getVersion() = Aria2Version("1.37.0", listOf("BitTorrent"))
        override fun addUri(uri: String, options: Map<String, String>): String {
            addCalls++
            return GID
        }

        override fun tellStatus(gid: String): Aria2Status {
            tellFailure?.let { throw it }
            return status
        }

        override fun pause(gid: String) = gid
        override fun unpause(gid: String) = gid
        override fun remove(gid: String): String {
            removeCalls++
            removeFailure?.let { throw it }
            return gid
        }

        override fun removeDownloadResult(gid: String) {
            removeResultCalls++
        }

        override fun shutdown() = Unit

        companion object { const val GID = "0123456789abcdef" }
    }
}
