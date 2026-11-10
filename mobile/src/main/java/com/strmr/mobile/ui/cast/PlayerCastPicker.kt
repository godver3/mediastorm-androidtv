package com.strmr.mobile.ui.cast

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.strmr.mobile.cast.CastController
import com.strmr.mobile.cast.handOff
import com.strmr.tv.player.PlayerController

/**
 * The player's Cast button (the iPhone app's CastController+Handoff and LivePlayer+Cast): the
 * picker over the video; a device chosen takes what is playing from here (a title at its
 * position, a live channel at its edge) and the player closes ([onExit]). The cast running
 * elsewhere can be opened on its remote from the picker without leaving the player.
 */
@Composable
fun PlayerCastPicker(controller: PlayerController, onExit: () -> Unit, onDismiss: () -> Unit) {
    var remote by remember { mutableStateOf(false) }
    if (remote) {
        CastRemoteSheet(onDismiss = onDismiss)
    } else {
        CastDevicePicker(
            onPick = { device ->
                onDismiss()
                CastController.handOff(controller, device, close = onExit)
            },
            onShowRemote = { remote = true },
            onDismiss = onDismiss,
        )
    }
}
