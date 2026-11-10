package com.strmr.mobile.ui.library

import androidx.compose.runtime.Composable
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.Route
import com.strmr.tv.api.LibraryEntry

/**
 * Opens a library title that has no details page: a show's episodes, else plays the file
 * (choosing a copy, resume or start over). Placeholder: always opens the library page.
 */
@Composable
fun rememberLibraryOpener(): (LibraryEntry) -> Unit {
    val navigator = LocalNavigator.current
    return { entry -> navigator.push(Route.LibraryShow(entry)) }
}
