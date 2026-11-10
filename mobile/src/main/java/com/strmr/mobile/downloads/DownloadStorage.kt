package com.strmr.mobile.downloads

import android.os.StatFs
import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.Artwork
import com.strmr.tv.api.ServerArtwork
import com.strmr.tv.app.App
import java.io.File
import java.util.concurrent.TimeUnit
import okhttp3.Request

/**
 * Where downloads live: app-specific storage (`files/downloads`, removed with the app and
 * never in backups), the records in `files/downloads.json`, a transfer's partial file as
 * `<id>.part` and the saved poster as `<id>.jpg` beside the files.
 */
object DownloadStorage {
    val directory: File by lazy { File(App.context.filesDir, "downloads").apply { mkdirs() } }
    val recordsFile: File get() = File(App.context.filesDir, "downloads.json")

    fun file(fileName: String) = File(directory, fileName)
    fun partFile(id: String) = File(directory, "$id.part")
    fun posterFile(id: String) = File(directory, "$id.jpg")

    fun fileExists(fileName: String) = file(fileName).isFile

    /** The saved poster, if there is one. */
    fun poster(id: String): File? = posterFile(id).takeIf { it.isFile }

    /** Removes the file, its partial transfer and its poster. */
    fun removeFiles(id: String, fileName: String) {
        listOf(file(fileName), partFile(id), posterFile(id)).forEach { it.delete() }
    }

    /** Free space on the downloads' volume. */
    val availableBytes: Long get() = runCatching { StatFs(directory.path).availableBytes }.getOrDefault(0)
    val totalBytes: Long get() = runCatching { StatFs(directory.path).totalBytes }.getOrDefault(0)

    /** Bytes on disk in the downloads folder (files, partial transfers and posters). */
    fun usedBytes(): Long = directory.listFiles()?.sumOf { it.length() } ?: 0

    /** Room for a download of `bytes`, with a 10% margin (0: unknown size, allowed). */
    fun hasRoom(bytes: Long, free: Long = availableBytes): Boolean = bytes <= 0 || free >= bytes * 1.1

    /** Saves the title's poster beside the file, so the list has art without the server. Blocking. */
    fun savePoster(raw: String?, id: String) {
        val url = Artwork.url(raw, "w342") ?: return
        val client = ApiClient.http.newBuilder().addInterceptor(ServerArtwork.interceptor).build()
        runCatching {
            client.newCall(Request.Builder().url(url).build()).apply { timeout().timeout(30, TimeUnit.SECONDS) }.execute().use { r ->
                val bytes = r.body?.bytes()
                if (r.isSuccessful && bytes != null && bytes.isNotEmpty()) posterFile(id).writeBytes(bytes)
            }
        }
    }
}
