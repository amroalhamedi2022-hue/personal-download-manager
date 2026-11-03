package ir.amirab.downloader.aria2

import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.PosixFilePermission
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Aria2BindingStoreTest {
    @Test
    fun roundTripIsSchemaVersionedMinimalAndDeterministic() {
        val directory = Files.createTempDirectory("aria2-bindings-roundtrip-")
        val path = directory.resolve("nested").resolve("bindings.json")
        val store = Aria2BindingStore(path)

        store.save(
            listOf(
                Aria2TaskBinding(9, "ABCDEF0123456789"),
                Aria2TaskBinding(3, "3333333333333333"),
            )
        )

        assertEquals(listOf(3L, 9L), store.load().map { it.hyperGetTaskId })
        assertEquals("abcdef0123456789", store.load().last().aria2Gid)
        val wire = Files.readString(path, StandardCharsets.UTF_8)
        assertTrue(wire.contains("\"schemaVersion\":1"))
        assertTrue(wire.contains("\"bindings\""))
        assertFalse(wire.contains("token:"))
        assertFalse(wire.contains("http://"))
        assertFalse(wire.contains("https://"))

        val firstBytes = Files.readAllBytes(path)
        store.save(store.load().reversed())
        assertTrue(firstBytes.contentEquals(Files.readAllBytes(path)))
    }

    @Test
    fun malformedAndUnsupportedDocumentsAreRejectedWithoutMutation() {
        val directory = Files.createTempDirectory("aria2-bindings-invalid-")
        val path = directory.resolve("bindings.json")
        val store = Aria2BindingStore(path)

        for (wire in listOf("{broken", "{\"schemaVersion\":2,\"bindings\":[]}")) {
            Files.writeString(path, wire, StandardCharsets.UTF_8)
            val before = Files.readAllBytes(path)
            assertFails { store.load() }
            assertTrue(before.contentEquals(Files.readAllBytes(path)))
        }
    }

    @Test
    fun duplicateTaskOrGidOnDiskIsRejected() {
        val directory = Files.createTempDirectory("aria2-bindings-duplicates-")
        val path = directory.resolve("bindings.json")
        val store = Aria2BindingStore(path)
        val documents = listOf(
            """{"schemaVersion":1,"bindings":[{"hyperGetTaskId":1,"aria2Gid":"1111111111111111"},{"hyperGetTaskId":1,"aria2Gid":"2222222222222222"}]}""",
            """{"schemaVersion":1,"bindings":[{"hyperGetTaskId":1,"aria2Gid":"1111111111111111"},{"hyperGetTaskId":2,"aria2Gid":"1111111111111111"}]}""",
        )

        documents.forEach { wire ->
            Files.writeString(path, wire, StandardCharsets.UTF_8)
            assertFails { store.load() }
        }
    }

    @Test
    fun invalidCandidateNeverChangesLastValidSnapshot() {
        val directory = Files.createTempDirectory("aria2-bindings-invalid-save-")
        val path = directory.resolve("bindings.json")
        val store = Aria2BindingStore(path)
        store.save(listOf(Aria2TaskBinding(1, "1111111111111111")))
        val before = Files.readAllBytes(path)

        assertFails {
            store.save(
                listOf(
                    Aria2TaskBinding(1, "1111111111111111"),
                    Aria2TaskBinding(1, "2222222222222222"),
                )
            )
        }

        assertTrue(before.contentEquals(Files.readAllBytes(path)))
        assertEquals(listOf(1L), store.load().map { it.hyperGetTaskId })
    }

    @Test
    fun failedAtomicReplacementPreservesLastValidSnapshotAndCleansTemp() {
        val directory = Files.createTempDirectory("aria2-bindings-atomic-failure-")
        val path = directory.resolve("bindings.json")
        Aria2BindingStore(path).save(listOf(Aria2TaskBinding(1, "1111111111111111")))
        val before = Files.readAllBytes(path)
        val failingStore = Aria2BindingStore(path) { _, _ -> throw IOException("injected replace failure") }

        assertFails {
            failingStore.save(listOf(Aria2TaskBinding(2, "2222222222222222")))
        }

        assertTrue(before.contentEquals(Files.readAllBytes(path)))
        Files.list(directory).use { entries ->
            assertEquals(listOf(path), entries.toList())
        }
    }

    @Test
    fun pathFailureDoesNotOverwriteTheBlockingFile() {
        val directory = Files.createTempDirectory("aria2-bindings-path-failure-")
        val blockingParent = directory.resolve("not-a-directory")
        val sentinel = "keep-me"
        Files.writeString(blockingParent, sentinel, StandardCharsets.UTF_8)

        assertFails {
            Aria2BindingStore(blockingParent.resolve("bindings.json"))
                .save(listOf(Aria2TaskBinding(1, "1111111111111111")))
        }

        assertEquals(sentinel, Files.readString(blockingParent, StandardCharsets.UTF_8))
    }

    @Test
    fun savedFileIsOwnerOnlyWhenPosixPermissionsAreAvailable() {
        val directory = Files.createTempDirectory("aria2-bindings-permissions-")
        val path = directory.resolve("bindings.json")
        Aria2BindingStore(path).save(emptyList())

        val view = Files.getFileAttributeView(path, java.nio.file.attribute.PosixFileAttributeView::class.java)
        if (view != null) {
            assertEquals(
                setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
                Files.getPosixFilePermissions(path),
            )
        }
    }

    @Test
    fun missingStoreRestoresAsEmpty() {
        val path = Files.createTempDirectory("aria2-bindings-missing-").resolve("bindings.json")
        assertEquals(emptyList(), Aria2BindingStore(path).load())
    }
}
