package com.immortalworld.data

import android.content.ContentResolver
import android.net.Uri
import com.immortalworld.core.SaveValidator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

object DocumentFiles {
    suspend fun read(resolver: ContentResolver, uri: Uri): String = withContext(Dispatchers.IO) {
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = ByteArrayOutputStream(); val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                require(output.size() + count <= SaveValidator.MAX_BYTES) { "Save exceeds the 2 MiB limit." }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        } ?: error("The document provider could not open this file.")
        Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
    }
    suspend fun write(resolver: ContentResolver, uri: Uri, json: String) = withContext(Dispatchers.IO) {
        resolver.openOutputStream(uri, "wt")?.use { it.write(json.toByteArray(Charsets.UTF_8)); it.flush() }
            ?: error("The document provider could not write this file.")
    }
}
