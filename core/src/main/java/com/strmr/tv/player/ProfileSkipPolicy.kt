package com.strmr.tv.player

/**
 * Which segments jump ahead by themselves. Credits never do: with a following
 * episode they get UpNext's cancellable countdown, and on a final episode or a
 * movie they play out.
 */
internal object ProfileSkipPolicy {
    fun enabled(type: String, intro: Boolean, recap: Boolean): Boolean =
        when (type) {
            "intro" -> intro
            "recap" -> recap
            else -> false
        }
}
