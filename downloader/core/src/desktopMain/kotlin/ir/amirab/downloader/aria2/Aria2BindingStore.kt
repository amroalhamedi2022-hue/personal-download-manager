package ir.amirab.downloader.aria2

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Persists only the replaceable HyperGet-task-to-aria2-GID relationship.
 * Session secrets, source URLs and RPC payloads must never enter this file.
 */
class Aria2BindingStore(
    path: Path,
    private val replaceFile: (Path, Path) -> Unit = ::replaceAtomically,
) {
    private val path = path.toAbsolutePath().normalize()
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = false
    }

    @Synchronized
    fun load(): List<Aria2TaskBinding> {
        if (Files.notExists(path)) return emptyList()
        require(Files.isRegularFile(path)) { "aria2 binding store is not a regular file: $path" }

        val document = json.decodeFromString<BindingDocument>(Files.readString(path, StandardCharsets.UTF_8))
        require(document.schemaVersion == CURRENT_SCHEMA_VERSION) {
            "Unsupported aria2 binding schema ${document.schemaVersion}."
        }

        // Construction validates both directions and normalizes GIDs. Returning
        // its snapshot also makes recovery order deterministic.
        return Aria2TaskRegistry(document.bindings).snapshot()
    }

    @Synchronized
    fun save(bindings: Iterable<Aria2TaskBinding>) {
        // Validate the complete candidate before creating or touching any file.
        val stableBindings = Aria2TaskRegistry(bindings).snapshot()
        val bytes = json.encodeToString(
            BindingDocument(schemaVersion = CURRENT_SCHEMA_VERSION, bindings = stableBindings)
        ).toByteArray(StandardCharsets.UTF_8)

        val parent = requireNotNull(path.parent) { "aria2 binding store must have a parent directory." }
        Files.createDirectories(parent)
        val temporary = Files.createTempFile(parent, ".${path.fileName}.", ".tmp")
        try {
            restrictToOwner(temporary)
            FileChannel.open(
                temporary,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING,
            ).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }

            replaceFile(temporary, path)
            restrictToOwner(path)
            forceDirectoryBestEffort(parent)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    @Serializable
    private data class BindingDocument(
        val schemaVersion: Int,
        val bindings: List<Aria2TaskBinding>,
    )

    private companion object {
        const val CURRENT_SCHEMA_VERSION = 1
        val OWNER_ONLY_PERMISSIONS = setOf(
            PosixFilePermission.OWNER_READ,
            PosixFilePermission.OWNER_WRITE,
        )

        fun replaceAtomically(source: Path, destination: Path) {
            try {
                Files.move(
                    source,
                    destination,
                    StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING,
                )
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING)
            }
        }

        fun restrictToOwner(file: Path) {
            try {
                Files.setPosixFilePermissions(file, OWNER_ONLY_PERMISSIONS)
            } catch (_: UnsupportedOperationException) {
                // Windows/non-POSIX providers retain their inherited ACL. The
                // file contains identifiers only, never credentials or URLs.
            } catch (_: IOException) {
                // Permission hardening is best effort on providers that expose
                // POSIX attributes but reject changing them.
            } catch (_: SecurityException) {
                // A restrictive security manager may deny attribute changes.
            }
        }

        fun forceDirectoryBestEffort(directory: Path) {
            try {
                FileChannel.open(directory, StandardOpenOption.READ).use { it.force(true) }
            } catch (_: IOException) {
                // Windows normally cannot open a directory as a FileChannel.
            } catch (_: UnsupportedOperationException) {
                // Directory fsync is unavailable on some file-system providers.
            } catch (_: SecurityException) {
                // The file itself was already forced before the atomic replace.
            }
        }
    }
}
