package ir.amirab.util

import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption


fun File.toUpUntil(
    condition: (File) -> Boolean
): File? {
    var file: File? = this
    while (true) {
        if (file == null) {
            return null
        }
        if (condition(file)) {
            return file
        }
        file = file.parentFile
    }
}

fun File.tryAtomicMove(destination: File) {
    fun replaceWithoutDeletingTarget() {
        // The source and destination are siblings, so a replace move is the safest available
        // fallback. Never delete the committed target before this succeeds.
        Files.move(
            toPath(),
            destination.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
        )
    }
    try {
        Files.move(
            toPath(),
            destination.toPath(),
            StandardCopyOption.ATOMIC_MOVE,
            StandardCopyOption.REPLACE_EXISTING,
        )
    } catch (_: AtomicMoveNotSupportedException) {
        replaceWithoutDeletingTarget()
    } catch (_: UnsupportedOperationException) {
        replaceWithoutDeletingTarget()
    }
}
