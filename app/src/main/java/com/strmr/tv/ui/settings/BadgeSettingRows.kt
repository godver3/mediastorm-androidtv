package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope

/** Settings › Card Badges: each Default (the server's value, shown) or this device's own choice. */
fun LazyListScope.badgeSettingRows() {
    BadgeSettings.Option.entries.forEach { option ->
        item(key = "badge.${option.key}") {
            ChoiceRow(
                option.title, option.detail,
                choices = listOf("Off", "On"),
                selected = BadgeSettings.override(option)?.let { if (it) 1 else 0 },
                defaultName = if (BadgeSettings.serverValue(option)) "On" else "Off",
            ) { i -> BadgeSettings.set(option, i?.let { it == 1 }) }
        }
    }
}
