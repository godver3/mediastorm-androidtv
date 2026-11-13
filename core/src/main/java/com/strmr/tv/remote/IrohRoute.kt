package com.strmr.tv.remote

import expo.modules.irohbridge.IrohBridgeNative
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** How the iroh link reaches the server: straight to it, through a relay, or not known yet. */
enum class IrohRoute(val label: String) {
    Direct("Direct"),
    Relay("Relay"),
    Detecting("Detecting…");

    companion object {
        /**
         * The bridge's path diagnostics, `{"selectedPath":"direct"|"relay"|"unknown",…}`. Unlike
         * the other bridge calls it carries no `"ok"` envelope, so it's parsed on its own here.
         */
        fun parse(json: String): IrohRoute {
            val obj = runCatching { Json.parseToJsonElement(json) as JsonObject }.getOrNull() ?: return Detecting
            return when (runCatching { obj["selectedPath"]?.jsonPrimitive?.contentOrNull }.getOrNull()) {
                "direct" -> Direct
                "relay" -> Relay
                else -> Detecting
            }
        }

        /** The live route, read off the main thread. */
        suspend fun current(): IrohRoute = withContext(Dispatchers.IO) {
            runCatching { parse(IrohBridgeNative.getPathDiagnostics()) }.getOrDefault(Detecting)
        }

        /** Settings polls this often while the route row is on screen (as the Expo app does). */
        const val POLL_MS = 2_000L
    }
}
