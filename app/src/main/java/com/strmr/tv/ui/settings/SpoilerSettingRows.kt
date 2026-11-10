package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope

/** Settings › Spoilers: each Default (the server's value, shown) or this device's own choice. */
fun LazyListScope.spoilerSettingRows() {
    SpoilerSettings.Option.entries.forEach { option ->
        item(key = option.key) {
            ChoiceRow(
                option.title, option.detail,
                choices = listOf("Off", "On"),
                selected = SpoilerSettings.override(option)?.let { if (it) 1 else 0 },
                defaultName = SpoilerSettings.serverValue(option)?.let { if (it) "On" else "Off" },
            ) { i -> SpoilerSettings.set(option, i?.let { it == 1 }) }
        }
    }
}
