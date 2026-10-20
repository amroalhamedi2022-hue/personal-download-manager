package ir.amirab.downloader.db

import ir.amirab.util.tryAtomicMove
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path.Companion.toOkioPath
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class StoredFileReadException(
    val storageFile: File,
    cause: Throwable,
) : IOException(
    "Stored data at '${storageFile.absolutePath}' is unreadable; the existing file was not replaced.",
    cause,
)

class TransactionalFileSaver(
    val json: Json,
    private val moveFile: (File, File) -> Unit = { source, destination ->
        source.tryAtomicMove(destination)
    },
) {
    /** Kept for compatibility: this is the uncommitted staging file, not the recovery copy. */
    fun getBakFile(file: File) = File("$file.tmp")
    fun getRecoveryFile(file: File) = File("$file.bak")

    fun delete(file: File) {
        listOf(
            file,
            getBakFile(file),
            getRecoveryFile(file),
            File("${getRecoveryFile(file)}.tmp"),
        ).forEach { Files.deleteIfExists(it.toPath()) }
    }

    inline fun <reified T> writeObject(file: File, t: T) {
        val text = json.encodeToString(t)
        writeText(file, text)
    }

    fun <T> writeObject(file: File, t: T, kSerializer: KSerializer<T>) {
        val text = json.encodeToString(kSerializer, t)
        writeText(file, text)
    }

    fun writeText(file: File, text: String) {
        val stagingFile = getBakFile(file)
        FileSystem.SYSTEM.write(stagingFile.toOkioPath()) {
            writeUtf8(text)
            flush()
        }
        check(readTextStrict(stagingFile) == text) {
            "Staged data verification failed for '${file.absolutePath}'."
        }

        if (file.exists()) {
            val currentText = readTextStrict(file)
            if (runCatching { json.parseToJsonElement(currentText) }.isSuccess) {
                stageRecoveryCopy(file)
            } else {
                quarantine(file)
            }
        }
        moveFile(stagingFile, file)
    }

    fun readText(file: File): String? {
        if (!file.exists()) return null
        return readTextStrict(file)
    }

    inline fun <reified T> readObject(file: File): T? {
        return readDecoded(file) { text -> json.decodeFromString<T>(text) }
    }

    fun <T> readObject(file: File, serializer: KSerializer<T>): T? {
        return readDecoded(file) { text -> json.decodeFromString(serializer, text) }
    }

    @PublishedApi
    internal fun <T> readDecoded(file: File, decode: (String) -> T): T? {
        if (file.exists()) {
            val primary = runCatching {
                val text = readTextStrict(file)
                text to decode(text)
            }
            primary.getOrNull()?.let { return it.second }

            val primaryFailure = requireNotNull(primary.exceptionOrNull())
            val recovery = readRecovery(file, decode)
            recovery.getOrNull()?.let { (text, value) ->
                try {
                    quarantine(file)
                    restoreRecoveredText(file, text)
                } catch (restoreFailure: Throwable) {
                    restoreFailure.addSuppressed(primaryFailure)
                    throw StoredFileReadException(file, restoreFailure)
                }
                return value
            }
            recovery.exceptionOrNull()?.let(primaryFailure::addSuppressed)
            throw StoredFileReadException(file, primaryFailure)
        }

        val recoveryFile = getRecoveryFile(file)
        if (!recoveryFile.exists()) return null
        val recovery = readRecovery(file, decode)
        val (text, value) = recovery.getOrElse { failure ->
            throw StoredFileReadException(file, failure)
        }
        try {
            restoreRecoveredText(file, text)
        } catch (restoreFailure: Throwable) {
            throw StoredFileReadException(file, restoreFailure)
        }
        return value
    }

    private fun readTextStrict(file: File): String =
        FileSystem.SYSTEM.read(file.toOkioPath()) { readUtf8() }

    private fun <T> readRecovery(
        file: File,
        decode: (String) -> T,
    ): Result<Pair<String, T>> = runCatching {
        val text = readTextStrict(getRecoveryFile(file))
        text to decode(text)
    }

    private fun stageRecoveryCopy(file: File) {
        val recoveryFile = getRecoveryFile(file)
        val recoveryStagingFile = File("$recoveryFile.tmp")
        Files.copy(
            file.toPath(),
            recoveryStagingFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
        check(readTextStrict(file) == readTextStrict(recoveryStagingFile)) {
            "Recovery data verification failed for '${file.absolutePath}'."
        }
        moveFile(recoveryStagingFile, recoveryFile)
    }

    private fun restoreRecoveredText(file: File, text: String) {
        val stagingFile = getBakFile(file)
        FileSystem.SYSTEM.write(stagingFile.toOkioPath()) {
            writeUtf8(text)
            flush()
        }
        check(readTextStrict(stagingFile) == text) {
            "Recovered data verification failed for '${file.absolutePath}'."
        }
        moveFile(stagingFile, file)
    }

    private fun quarantine(file: File): File {
        var index = 0
        var quarantineFile: File
        do {
            val suffix = if (index == 0) "" else ".$index"
            quarantineFile = File("$file.corrupt-${System.currentTimeMillis()}$suffix")
            index++
        } while (quarantineFile.exists())
        moveFile(file, quarantineFile)
        return quarantineFile
    }
}
