package com.strmr.mobile.downloads

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.mediaHeaders
import com.strmr.tv.api.streamUrl
import com.strmr.tv.app.AppLog
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * One download's HTTP transfer: `/api/video/stream` for the item's stream path (the raw file,
 * `transmux=0`, as the iPhone and Expo apps download it), appended to `<id>.part` from where
 * it stopped with a Range request ([RangeResume]), renamed into place when complete. The URL
 * is built per attempt, so it follows the route in use (home address, away address or the
 * tunnel). Cancelling the coroutine stops it, leaving the partial file for later.
 */
class DownloadTransfer(
    private val item: DownloadItem,
    /** Bytes on disk so far and the total when known; called at most twice a second. */
    private val onProgress: (written: Long, total: Long?) -> Unit,
) {
    sealed interface Outcome {
        data class Completed(val bytes: Long) : Outcome
        data class Failed(val message: String, val retryable: Boolean) : Outcome
    }

    private class HttpFailure(val code: Int) : IOException("The server answered $code.")

    suspend fun run(): Outcome = withContext(Dispatchers.IO) {
        val part = DownloadStorage.partFile(item.id)
        try {
            var refreshed = false
            var attempts = 0
            while (true) {
                attempts++
                try {
                    val bytes = attempt(part)
                    val target = DownloadStorage.file(item.fileName)
                    target.delete()
                    if (!part.renameTo(target)) throw IOException("The download couldn't be saved.")
                    return@withContext Outcome.Completed(bytes)
                } catch (e: HttpFailure) {
                    // An expired session: refresh once and ask again.
                    if (e.code == 401 && !refreshed) {
                        refreshed = true
                        if (ApiClient.refreshSession() == ApiClient.RefreshOutcome.REFRESHED) continue
                    }
                    if (e.code == 416 && attempts < 3) continue // the partial was dropped; ask for the whole file
                    return@withContext Outcome.Failed(e.message ?: "Download failed", RangeResume.retryable(e.code))
                }
            }
            @Suppress("UNREACHABLE_CODE")
            Outcome.Failed("Download failed", true)
        } catch (e: IOException) {
            currentCoroutineContext().ensureActive()
            AppLog.w("Downloads", "${item.displayTitle}: ${e.message}")
            Outcome.Failed(e.message ?: "Connection lost", retryable = true)
        }
    }

    /** One request, appending to (or restarting) the partial file; returns the file's size. */
    private suspend fun attempt(part: File): Long {
        val existing = if (part.isFile) part.length() else 0L
        val request = Request.Builder().url(ApiClient.streamUrl(item.streamPath)).apply {
            ApiClient.mediaHeaders.forEach { (k, v) -> header(k, v) }
            RangeResume.header(existing)?.let { header("Range", it) }
        }.build()
        val call = client.newCall(request)
        val job = currentCoroutineContext().job
        val cancel = job.invokeOnCompletion { call.cancel() }
        try {
            call.execute().use { response ->
                val body = response.body ?: throw IOException("The server sent nothing.")
                val decision = RangeResume.decide(existing, response.code, response.header("Content-Range"), body.contentLength())
                val (offset, total) = when (decision) {
                    is RangeResume.Decision.Append -> decision.offset to decision.total
                    is RangeResume.Decision.Restart -> 0L to decision.total
                    RangeResume.Decision.Complete -> return existing
                    RangeResume.Decision.Discard -> {
                        part.delete()
                        throw HttpFailure(416)
                    }
                    is RangeResume.Decision.Fail -> throw HttpFailure(decision.code)
                }
                if (offset == 0L && existing > 0) AppLog.i("Downloads", "${item.displayTitle}: server sent the whole file; restarting")
                else if (offset > 0) AppLog.i("Downloads", "${item.displayTitle}: resuming at $offset of ${total ?: "?"}")
                onProgress(offset, total)
                var written = offset
                var lastReport = System.nanoTime()
                FileOutputStream(part, offset > 0).use { out ->
                    val source = body.byteStream()
                    val buffer = ByteArray(256 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val n = source.read(buffer)
                        if (n < 0) break
                        out.write(buffer, 0, n)
                        written += n
                        val now = System.nanoTime()
                        if (now - lastReport > 500_000_000) {
                            lastReport = now
                            onProgress(written, total)
                        }
                    }
                }
                if (total != null && written < total) throw IOException("The connection closed early.")
                onProgress(written, total ?: written)
                return written
            }
        } finally {
            cancel.dispose()
        }
    }

    companion object {
        /** No whole-call timeout (files take hours); a stalled read fails after a minute. */
        private val client: OkHttpClient by lazy {
            ApiClient.http.newBuilder().readTimeout(60, TimeUnit.SECONDS).callTimeout(0, TimeUnit.SECONDS).build()
        }
    }
}
