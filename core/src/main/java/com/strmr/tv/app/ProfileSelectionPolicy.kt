package com.strmr.tv.app

import com.strmr.tv.api.Profile

/**
 * Which profile opens at launch without the picker.
 *
 * "Use this profile automatically" is the household's choice for this device and is
 * only saved after the profile's PIN was entered, so it opens without the PIN again.
 * A home screen row link carries no such consent and only opens PIN-free profiles.
 * IDs not in the signed-in account's profile list (stale or foreign) never open.
 */
internal object ProfileSelectionPolicy {
    fun startup(profiles: List<Profile>, automaticId: String?, linkedId: String?): Profile? =
        profiles.firstOrNull { it.id == linkedId && !it.requiresPin }
            ?: profiles.firstOrNull { it.id == automaticId }
            ?: profiles.singleOrNull()?.takeIf { !it.requiresPin }
}
