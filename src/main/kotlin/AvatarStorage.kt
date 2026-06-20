package com.example

import io.ktor.http.ContentType
import io.ktor.http.content.PartData
import io.ktor.util.cio.writeChannel
import io.ktor.utils.io.copyAndClose
import java.io.File
import java.util.UUID

/** Raised when an uploaded avatar is missing or of an unsupported type; routes turn this into a 400. */
class InvalidAvatarException(message: String) : Exception(message)

/**
 * Stores avatar images on the local filesystem under [baseDir] and exposes them at the
 * "avatars/<file>" relative path, which is also the URL they are served from (see static
 * route in [configureApp]). Filenames are random UUIDs so uploads can never collide or be
 * influenced by the (untrusted) client filename.
 */
class AvatarStorage(private val baseDir: File) {
    init {
        baseDir.mkdirs()
    }

    /** Allowed image content types mapped to the on-disk extension we give them. */
    private val extensions = listOf(
        ContentType.Image.PNG to "png",
        ContentType.Image.JPEG to "jpg",
        ContentType("image", "webp") to "webp",
    )

    /**
     * Streams the uploaded file to disk under a freshly generated name and returns the
     * relative path to store in the database. Throws [InvalidAvatarException] for an
     * unsupported or missing content type.
     */
    suspend fun save(part: PartData.FileItem): String {
        val contentType = part.contentType
            ?: throw InvalidAvatarException("Avatar is missing a content type")
        val extension = extensions.firstOrNull { contentType.match(it.first) }?.second
            ?: throw InvalidAvatarException("Unsupported avatar type: $contentType")

        val fileName = "${UUID.randomUUID()}.$extension"
        part.provider().copyAndClose(File(baseDir, fileName).writeChannel())

        return "$RELATIVE_PREFIX/$fileName"
    }

    /** Deletes the file behind a stored relative path; no-op if blank. The name is taken on its own to block path traversal. */
    fun delete(relativePath: String?) {
        if (relativePath.isNullOrBlank()) return
        File(baseDir, File(relativePath).name).delete()
    }

    companion object {
        /** Both the DB path prefix and the public URL segment the files are served from. */
        const val RELATIVE_PREFIX = "avatars"
    }
}
