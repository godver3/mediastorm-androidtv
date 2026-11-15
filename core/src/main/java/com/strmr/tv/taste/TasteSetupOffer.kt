package com.strmr.tv.taste

import kotlinx.coroutines.CancellationException

/** Offer once per captured session context. A failed optional request never
 * interrupts Home; manual setup can retry independently. */
class TasteSetupOffer {
    private var attempted = false
    suspend fun check(gateway: TasteSetupGateway, visible: () -> Boolean, show: (TasteSetupReply) -> Unit) {
        if (attempted || !visible() || !gateway.isCurrent()) return
        attempted = true
        try {
            val reply = gateway.read()
            if (!gateway.isCurrent()) return
            if (!visible()) { attempted = false; return }
            if (reply.status == "new" && reply.available && reply.mediaTypes.isNotEmpty() && reply.mediaTypes.all { it in setOf("movie", "series") }) show(reply)
        } catch (error: CancellationException) { attempted = false; throw error
        } catch (_: Exception) { /* Optional setup must not block normal viewing. */ }
    }
}
