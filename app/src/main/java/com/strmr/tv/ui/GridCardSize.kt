package com.strmr.tv.ui

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.app.App

/**
 * How big the cards are on list grids (lists, streaming services, Watchlist, Continue Watching,
 * Hidden, a person's titles), as on tvOS. One size for every grid, so changing it on one list
 * changes them all; saved on this device.
 */
enum class GridCardSize(val label: String, val scale: Float) {
    SMALL("Small", 0.75f), MEDIUM("Medium", 1f), LARGE("Large", 1.3f), EXTRA_LARGE("Extra Large", 1.7f);

    /** The narrowest a poster column gets: the grid fits as many as that allows. */
    val posterMinWidth: Dp get() = (136 * scale).dp
}

object GridCardSizeStore {
    private val prefs by lazy { App.context.getSharedPreferences("strmr", Context.MODE_PRIVATE) }
    private const val KEY = "gridCardSize"

    var size by mutableStateOf(
        prefs.getString(KEY, null)?.let { name -> GridCardSize.entries.firstOrNull { it.name == name } } ?: GridCardSize.MEDIUM,
    )
        private set

    fun choose(value: GridCardSize) {
        size = value
        prefs.edit().putString(KEY, value.name).apply()
    }
}

/** The card size button beside a grid's controls; opens a side-panel picker. */
@Composable
fun GridCardSizeButton() {
    var picking by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { picking = true }) { Text("Size: ${GridCardSizeStore.size.label}") }
    if (picking) {
        PickerDialog(
            "Card Size", GridCardSize.entries.map { it.label }, GridCardSizeStore.size.ordinal,
            onPick = { GridCardSizeStore.choose(GridCardSize.entries[it]) }, onDismiss = { picking = false },
        )
    }
}
