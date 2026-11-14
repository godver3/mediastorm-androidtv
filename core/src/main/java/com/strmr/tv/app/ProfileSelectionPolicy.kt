package com.strmr.tv.app

import com.strmr.tv.api.Profile

/** Remembering a family TV's account must never bypass an adult profile's PIN. */
internal object ProfileSelectionPolicy {
    fun canOpenAutomatically(profile: Profile): Boolean = !profile.requiresPin

    /** A stale saved ID or launcher link cannot grant access to a locked or foreign profile. */
    fun startup(profiles: List<Profile>, automaticId: String?, linkedId: String?): Profile? {
        val accessible = profiles.filter(::canOpenAutomatically)
        return accessible.firstOrNull { it.id == linkedId }
            ?: accessible.firstOrNull { it.id == automaticId }
            ?: profiles.singleOrNull()?.takeIf(::canOpenAutomatically)
    }
}
