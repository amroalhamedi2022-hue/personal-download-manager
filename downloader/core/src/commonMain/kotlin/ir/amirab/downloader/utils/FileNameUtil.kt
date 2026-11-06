package ir.amirab.downloader.utils

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.isActive
import java.io.File

object FileNameUtil {
    /**
     * make sure to validate name before using this function
     */
    fun getExtensionOrNull(filename: String): String? {
        // A leading dot alone denotes a hidden file, not an extension.
        // A trailing dot is likewise not a usable extension.
        val lastDot = filename.lastIndexOf('.')
        return filename.substring(lastDot + 1)
            .takeIf { lastDot > 0 && it.isNotEmpty() }
    }

    fun numberedIfExists(filename: File): Flow<File> {
        return flow {
            if (!filename.exists()) {
                emit(filename)
            }
            // Hidden names such as ".env" have no extension; File.extension
            // would incorrectly split them into an empty stem and "env".
            val ext = getExtensionOrNull(filename.name)?.let { ".$it" }.orEmpty()
            val name = filename.name.removeSuffix(ext)
            var counter = 1
            while (currentCoroutineContext().isActive) {
                val candidateName = "${name}_${counter}${ext}"
                // File("report.zip") has no parentFile. Relative paths are
                // valid inputs and must not fail before the first collision.
                val newFile = filename.parentFile?.resolve(candidateName)
                    ?: File(candidateName)
                if (!newFile.exists()) {
                    emit(newFile)
                }
                counter++
            }
        }
    }

    fun replaceExtension(filename: String, newExtension: String, appendIfNotExists: Boolean = true): String {
        val ext = getExtensionOrNull(filename) ?: if (appendIfNotExists) {
            return "$filename.$newExtension"
        } else {
            return filename
        }
        val filenameWithoutExtension = filename.dropLast(ext.length)
        return "$filenameWithoutExtension$newExtension"
    }
}
