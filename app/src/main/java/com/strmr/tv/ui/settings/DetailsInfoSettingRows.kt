package com.strmr.tv.ui.settings

import androidx.compose.foundation.lazy.LazyListScope
import com.strmr.tv.ui.details.DetailsInfo

/** Settings › Details Page: the info line under the details actions, each Default (the server's value) or this device's choice. */
fun LazyListScope.detailsInfoSettingRows() {
    DetailsInfo.Option.entries.forEach { option ->
        item(key = option.key) {
            ChoiceRow(
                option.title, option.detail,
                choices = listOf("Off", "On"),
                selected = DetailsInfo.override(option)?.let { if (it) 1 else 0 },
                defaultName = DetailsInfo.serverValue(option)?.let { if (it) "On" else "Off" } ?: "Off",
            ) { i -> DetailsInfo.set(option, i?.let { it == 1 }) }
        }
    }
}
