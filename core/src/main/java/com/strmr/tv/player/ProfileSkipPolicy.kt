package com.strmr.tv.player

/** Credits with a following episode use UpNext's cancellable countdown. */
internal object ProfileSkipPolicy {
    fun enabled(type: String, intro: Boolean, recap: Boolean, credits: Boolean, hasNext: Boolean): Boolean =
        when (type) {
            "intro" -> intro
            "recap" -> recap
            "outro" -> credits && !hasNext
            else -> false
        }
}
