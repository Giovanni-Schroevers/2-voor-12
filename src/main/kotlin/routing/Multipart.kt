package com.example.routing

import com.example.AvatarStorage
import io.ktor.http.content.PartData
import io.ktor.http.content.forEachPart
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveMultipart

/** Text fields collected from a multipart form, plus the stored path of an uploaded avatar (if any). */
data class AvatarForm(val fields: Map<String, String>, val avatarPath: String?) {
    fun field(name: String): String? = fields[name]
}

/**
 * Reads a `multipart/form-data` body, collecting text fields and streaming an "avatar" file part
 * (if present) into [storage]. This lets registration and profile updates carry the image alongside
 * the rest of the body in a single request. Throws [com.example.InvalidAvatarException] for a bad image.
 */
suspend fun ApplicationCall.receiveAvatarForm(storage: AvatarStorage): AvatarForm {
    val fields = mutableMapOf<String, String>()
    var avatarPath: String? = null

    receiveMultipart().forEachPart { part ->
        when (part) {
            is PartData.FormItem -> part.name?.let { fields[it] = part.value }
            is PartData.FileItem -> if (part.name == "avatar") avatarPath = storage.save(part)
            else -> {}
        }
        part.dispose()
    }

    return AvatarForm(fields, avatarPath)
}
