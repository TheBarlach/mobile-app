package dk.sdu.expenseshare.data.local

import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** A single piece of persisted text, e.g. one JSON file. Implementations throw [IOException]. */
interface TextStore {
    /** The stored text, or null if nothing has been written yet. */
    suspend fun read(): String?

    suspend fun write(text: String)
}

/** [TextStore] backed by a file in app-private storage. */
class FileTextStore(private val file: File) : TextStore {
    override suspend fun read(): String? = withContext(Dispatchers.IO) {
        if (file.exists()) file.readText() else null
    }

    override suspend fun write(text: String) = withContext(Dispatchers.IO) {
        val parent = file.parentFile
        if (parent != null && !parent.isDirectory && !parent.mkdirs()) {
            throw IOException("Could not create $parent")
        }
        // Write to a temporary file first so a crash mid-write never leaves a half-written file.
        val temporary = File(file.path + ".tmp")
        temporary.writeText(text)
        Files.move(
            temporary.toPath(),
            file.toPath(),
            StandardCopyOption.REPLACE_EXISTING,
            StandardCopyOption.ATOMIC_MOVE
        )
        Unit
    }
}
