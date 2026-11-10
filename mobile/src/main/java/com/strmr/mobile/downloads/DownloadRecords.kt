package com.strmr.mobile.downloads

import com.strmr.tv.api.ApiJson
import com.strmr.tv.app.AppLog
import java.io.File
import kotlinx.serialization.builtins.ListSerializer

/** The download records on disk (`files/downloads.json`), written atomically. */
object DownloadRecords {
    private val serializer = ListSerializer(DownloadItem.serializer())
    private val lock = Any()

    fun load(): List<DownloadItem> = synchronized(lock) {
        val file = DownloadStorage.recordsFile
        if (!file.isFile) return emptyList()
        runCatching { ApiJson.decodeFromString(serializer, file.readText()) }
            .onFailure { AppLog.w("Downloads", "couldn't read the records: ${it.message}") }
            .getOrDefault(emptyList())
    }

    fun save(items: List<DownloadItem>) = synchronized(lock) {
        val file = DownloadStorage.recordsFile
        val temp = File(file.parentFile, file.name + ".tmp")
        runCatching {
            temp.writeText(ApiJson.encodeToString(serializer, items))
            if (!temp.renameTo(file)) {
                file.delete()
                temp.renameTo(file)
            }
        }.onFailure { AppLog.w("Downloads", "couldn't save the records: ${it.message}") }
    }
}
