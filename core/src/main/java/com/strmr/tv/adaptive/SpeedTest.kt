package com.strmr.tv.adaptive

import com.strmr.tv.api.ApiClient
import com.strmr.tv.api.mediaHeaders
import com.strmr.tv.app.AppLog
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext
import okhttp3.Request

/** One download from `/api/debug/speed-test`, timed as Expo's `testBackendConnectionSpeed` times it. */
data class SpeedFlow(val receivedBytes: Long, val elapsedMs: Double, val setupMs: Double, val transferMs: Double)

/** Several flows run together, Expo's `testBackendConnectionSpeedAggregate`. */
data class SpeedSample(
    val receivedBytes: Long,
    val elapsedMs: Double,
    val setupMs: Double,
    val transferMs: Double,
    val speedMbps: Double,
    val parallelStreams: Int,
    val sampleId: Long,
)

/** The speed test maths, pure for tests. */
object SpeedMath {
    /** Megabits per second over `ms` (at least 1 ms). */
    fun mbps(bytes: Long, ms: Double): Double = bytes * 8 / (ms.coerceAtLeast(1.0) / 1000) / 1_000_000

    /** Bytes per flow: the remainder goes one each to the first flows. */
    fun split(totalBytes: Long, streams: Int): List<Long> {
        val total = totalBytes.coerceAtLeast(1)
        val n = streams.coerceIn(1, 8)
        return List(n) { i -> total / n + if (i < total % n) 1 else 0 }
    }

    /**
     * The aggregate: the earliest flow's setup is taken off the whole elapsed time, and the speed
     * is every byte over what remains.
     */
    fun aggregate(flows: List<SpeedFlow>, elapsedMs: Double, sampleId: Long): SpeedSample {
        val elapsed = elapsedMs.coerceAtLeast(1.0)
        val setup = flows.minOf { it.setupMs }
        val transfer = (elapsed - setup).coerceAtLeast(1.0)
        val bytes = flows.sumOf { it.receivedBytes }
        return SpeedSample(bytes, elapsed, setup, transfer, mbps(bytes, transfer), flows.size, sampleId)
    }
}

/** Downloads from the server's speed-test endpoint over this app's route (the iroh proxy included). */
object SpeedTest {
    /** Total `bytes` split over `streams` parallel downloads. Throws on a failed or short download. */
    suspend fun aggregate(bytes: Long, streams: Int = 4): SpeedSample = coroutineScope {
        val sampleId = System.currentTimeMillis()
        val parts = SpeedMath.split(bytes, streams)
        val started = System.nanoTime()
        val flows = parts.mapIndexed { i, n -> async { flow(n, sampleId, i + 1, parts.size) } }.awaitAll()
        val sample = SpeedMath.aggregate(flows, (System.nanoTime() - started) / 1e6, sampleId)
        if (sample.receivedBytes != parts.sum()) throw IOException("Speed test received ${sample.receivedBytes} bytes; expected ${parts.sum()}")
        AppLog.i("Adaptive", "speed test sample bytes=${sample.receivedBytes} streams=${sample.parallelStreams} " +
            "elapsedMs=${sample.elapsedMs.toLong()} setupMs=${sample.setupMs.toLong()} transferMs=${sample.transferMs.toLong()} " +
            "mbps=${"%.1f".format(sample.speedMbps)}")
        sample
    }

    private suspend fun flow(bytes: Long, sampleId: Long, flow: Int, streams: Int): SpeedFlow = withContext(Dispatchers.IO) {
        val url = ApiClient.url("/api/debug/speed-test", mapOf(
            "bytes" to bytes.toString(), "_" to System.currentTimeMillis().toString(),
            "sampleId" to sampleId.toString(), "flow" to flow.toString(), "streams" to streams.toString(),
        ))
        val request = Request.Builder().url(url).apply {
            ApiClient.mediaHeaders.forEach { (k, v) -> header(k, v) }
            header("Accept", "application/octet-stream")
            header("Cache-Control", "no-store")
        }.build()
        val call = ApiClient.http.newCall(request).apply { timeout().timeout(120, TimeUnit.SECONDS) }
        val handle = currentCoroutineContext().job.invokeOnCompletion { call.cancel() }
        val started = System.nanoTime()
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) throw IOException("Speed test failed: HTTP ${response.code}")
                val responseAt = System.nanoTime()
                var received = 0L
                val buffer = ByteArray(64 * 1024)
                response.body?.byteStream()?.use { input ->
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        received += n
                        currentCoroutineContext().ensureActive()
                    }
                }
                val done = System.nanoTime()
                val transfer = ((done - responseAt) / 1e6).coerceAtLeast(1.0)
                if (received != bytes) throw IOException("Speed test received $received bytes; expected $bytes")
                SpeedFlow(received, ((done - started) / 1e6).coerceAtLeast(1.0), (responseAt - started) / 1e6, transfer)
            }
        } finally {
            handle.dispose()
        }
    }
}
