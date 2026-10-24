package com.strmr.tv.api

import android.net.Uri
import com.strmr.tv.BuildConfig
import com.strmr.tv.app.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Debug builds only: `/api/sports/…` answers read from a folder of JSON files instead of the
 * server, so every sport's views can be checked on screen out of season (as the Expo app's
 * `*.local` preview routes do). A request maps to `<folder>/<path after /api/sports/>.json`
 * (`game/401873010.json`, `scoreboard.json`; the query is ignored) and goes to the server when
 * there is no such file. The folder is `sports-fixtures` in the app's own files: copy one in with
 * `adb push <folder> /data/local/tmp/sports-fixtures` then
 * `adb shell run-as com.strmr.tv cp -r /data/local/tmp/sports-fixtures files/`; remove it with
 * `adb shell run-as com.strmr.tv rm -r files/sports-fixtures`. The tvOS repo's
 * `scripts/sports-fixtures.py` builds a folder from the Expo fixtures.
 */
object SportsFixtures {
    private const val PREFIX = "/api/sports/"

    suspend fun response(path: String): String? {
        if (!BuildConfig.DEBUG || !path.startsWith(PREFIX)) return null
        val relative = Uri.decode(path.removePrefix(PREFIX)).takeIf { it.isNotEmpty() && ".." !in it } ?: return null
        return withContext(Dispatchers.IO) {
            File(File(App.context.filesDir, "sports-fixtures"), "$relative.json").takeIf { it.isFile }?.readText()
        }
    }
}
