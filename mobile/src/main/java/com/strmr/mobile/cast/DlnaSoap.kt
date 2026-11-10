package com.strmr.mobile.cast

import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/** A UPnP service of a renderer: its type URN and control URL. */
data class DlnaService(val type: String, val controlUrl: String)

/** UPnP control over SOAP (`POST` to a service's control URL), and the formats it speaks. */
object DlnaSoap {
    /**
     * LG webOS takes well over ten seconds to answer a Stop once wedged, and Play on a large
     * file can take as long while the renderer opens it (the Expo bridge allows 30 s too).
     */
    const val TIMEOUT_MS = 30_000L
    const val USER_AGENT = "MediaStorm/1.0 UPnP/1.0 DLNADOC/1.50"

    /** No pooled sockets: renderers drop idle keep-alive connections without telling anyone. */
    val http: OkHttpClient by lazy {
        OkHttpClient.Builder().connectTimeout(5, TimeUnit.SECONDS).retryOnConnectionFailure(true)
            .connectionPool(okhttp3.ConnectionPool(0, 1, TimeUnit.SECONDS)).build()
    }

    /** The SOAP body for [action] on a service of [serviceType], arguments in order. */
    fun envelope(serviceType: String, action: String, arguments: List<Pair<String, String>>): String = buildString {
        append("<?xml version=\"1.0\" encoding=\"utf-8\"?>")
        append("<s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" ")
        append("s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body>")
        append("<u:$action xmlns:u=\"${escape(serviceType)}\">")
        for ((name, value) in arguments) append("<$name>${escape(value)}</$name>")
        append("</u:$action></s:Body></s:Envelope>")
    }

    /**
     * A reply's `<ActionResponse>` element; for a fault (`500` with `<UPnPError>`) a [CastException]
     * naming its code and description.
     */
    fun response(action: String, status: Int, body: String): XmlNode {
        val tree = XmlNode.parse(body)
        if (status != 200) {
            val error = tree?.descendants("UPnPError")?.firstOrNull()
            val code = error?.childText("errorCode") ?: "HTTP $status"
            val detail = error?.childText("errorDescription").orEmpty()
            throw CastException("The TV rejected $action ($code${if (detail.isEmpty()) "" else ": $detail"}).")
        }
        return tree?.descendants("${action}Response")?.firstOrNull() ?: XmlNode("${action}Response", "", emptyList())
    }

    /** Calls [action] and returns its response element. */
    suspend fun call(
        service: DlnaService, action: String, arguments: List<Pair<String, String>>, timeoutMs: Long = TIMEOUT_MS,
    ): XmlNode = withContext(Dispatchers.IO) {
        val request = Request.Builder().url(service.controlUrl)
            .post(envelope(service.type, action, arguments).toRequestBody("text/xml; charset=\"utf-8\"".toMediaType()))
            .header("SOAPACTION", "\"${service.type}#$action\"")
            .header("User-Agent", USER_AGENT)
            .build()
        val call = http.newBuilder().callTimeout(timeoutMs, TimeUnit.MILLISECONDS).build().newCall(request)
        call.execute().use { response(action, it.code, it.body?.string().orEmpty()) }
    }

    fun escape(value: String): String = value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace("\"", "&quot;").replace("'", "&apos;")

    /** UPnP clock (`H+:MM:SS[.F]`) to seconds; `NOT_IMPLEMENTED` or empty reads as unknown. */
    fun seconds(clock: String?): Double? {
        val parts = clock?.trim()?.substringBefore('.')?.split(':')?.map { it.toDoubleOrNull() ?: return null } ?: return null
        if (parts.size != 3) return null
        return parts[0] * 3600 + parts[1] * 60 + parts[2]
    }

    fun clock(seconds: Double): String {
        val whole = seconds.toLong().coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", whole / 3600, whole % 3600 / 60, whole % 60)
    }
}
