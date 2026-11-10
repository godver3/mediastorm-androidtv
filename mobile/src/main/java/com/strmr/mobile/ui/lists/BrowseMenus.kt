package com.strmr.mobile.ui.lists

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.library.rememberLibraryOpener
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.tv.home.HomeLink
import com.strmr.tv.home.offersRandom
import com.strmr.tv.home.sorts
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.ui.lists.ListGridModel
import kotlinx.coroutines.launch

/** The list page's top bar actions: Random, the sort menu, and Rename / Delete on the profile's own lists. */
@Composable
fun BrowseActions(model: ListGridModel) {
    val navigator = LocalNavigator.current
    val openLibrary = rememberLibraryOpener()
    val scope = rememberCoroutineScope()
    var sorting by remember { mutableStateOf(false) }
    var managing by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val list = model.list

    if (list.offersRandom) {
        IconButton(onClick = {
            model.pickRandom { link ->
                when (link) {
                    is HomeLink.Details -> navigator.openDetails(link.target)
                    is HomeLink.Library -> openLibrary(link.entry)
                    else -> Unit
                }
            }
        }) {
            if (model.picking) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else Icon(Icons.Filled.Casino, "Random")
        }
    }
    if (list.sorts.isNotEmpty()) {
        Box {
            IconButton(onClick = { sorting = true }) { Icon(Icons.AutoMirrored.Filled.Sort, "Sort") }
            DropdownMenu(expanded = sorting, onDismissRequest = { sorting = false }) {
                SortMenuItems(list, model.options) { sort ->
                    sorting = false
                    model.options = model.options.choose(sort)
                }
            }
        }
    }
    if (model.manageable) {
        Box {
            IconButton(onClick = { managing = true }) { Icon(Icons.Filled.MoreVert, "List Actions") }
            DropdownMenu(expanded = managing, onDismissRequest = { managing = false }) {
                DropdownMenuItem(
                    text = { Text("Rename…") }, leadingIcon = { Icon(Icons.Filled.Edit, null) },
                    onClick = { managing = false; renaming = true },
                )
                DropdownMenuItem(
                    text = { Text("Delete List…") }, leadingIcon = { Icon(Icons.Filled.Delete, null) },
                    onClick = { managing = false; deleting = true },
                )
            }
        }
    }

    val customId = model.customId
    if (renaming && customId != null) {
        ListNameDialog("Rename List", confirm = "Rename", initial = model.title, renaming = customId, onDismiss = { renaming = false }) { name ->
            if (name != model.title) CustomListsStore.rename(customId, name)
        }
    }
    if (deleting && customId != null) {
        AlertDialog(
            onDismissRequest = { deleting = false },
            title = { Text("Delete “${model.title}”?") },
            text = { Text("The titles stay in your library; only the list goes.") },
            confirmButton = {
                TextButton(onClick = {
                    deleting = false
                    scope.launch {
                        try {
                            CustomListsStore.delete(customId)
                            navigator.pop()
                        } catch (e: Exception) {
                            model.actionError = e.message
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = false }) { Text("Cancel") } },
        )
    }
}

/**
 * A list name prompt (new or rename). The name is checked against the profile's lists before
 * [onSubmit] runs; a failure shows under the field and keeps the dialog open.
 */
@Composable
fun ListNameDialog(
    title: String,
    message: String? = null,
    confirm: String,
    initial: String = "",
    renaming: String? = null,
    onDismiss: () -> Unit,
    onSubmit: suspend (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    var problem by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    fun submit() {
        if (working) return
        CustomListsStore.nameProblem(name, renaming)?.let { problem = it; return }
        working = true
        scope.launch {
            try {
                onSubmit(name.trim())
                onDismiss()
            } catch (e: Exception) {
                problem = e.message
            } finally {
                working = false
            }
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                message?.let { Text(it) }
                OutlinedTextField(
                    name, { name = it; problem = null }, label = { Text("Name") }, singleLine = true,
                    isError = problem != null, supportingText = problem?.let { { Text(it) } },
                )
            }
        },
        confirmButton = { TextButton(onClick = ::submit, enabled = !working) { Text(if (working) "Saving…" else confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
