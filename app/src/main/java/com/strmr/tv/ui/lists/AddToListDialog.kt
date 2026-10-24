package com.strmr.tv.ui.lists

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Button
import androidx.tv.material3.Icon
import androidx.tv.material3.ListItem
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Text
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.CustomList
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.library.CustomListsStore
import com.strmr.tv.ui.AppIcons
import com.strmr.tv.ui.onboarding.FormField
import kotlinx.coroutines.launch

/**
 * "Manage Lists…": each of the profile's lists with a check on those that already hold the
 * title. Selecting a list adds or removes the title in place; "New List…" creates one with
 * the title in it. Shown over everything while [CustomListsStore.picking] is set.
 */
@Composable
fun AddToListDialog(target: DetailsTarget) {
    val scope = rememberCoroutineScope()
    /** The matching stored item per list ID; null while loading. */
    var memberships by remember { mutableStateOf<Map<String, WatchlistItem>?>(null) }
    var busy by remember { mutableStateOf(setOf<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    val close = { CustomListsStore.picking = null }
    LaunchedEffect(target) {
        CustomListsStore.refresh()
        memberships = CustomListsStore.memberships(target)
        // The rows exist a frame after the memberships arrive.
        withFrameNanos { }
        runCatching { first.requestFocus() }
    }

    // Repeat presses on a list with a change in flight are ignored (disabling the focused row would drop focus).
    fun toggle(list: CustomList) {
        if (list.id in busy) return
        busy = busy + list.id
        scope.launch {
            try {
                val stored = memberships?.get(list.id)
                if (stored != null) {
                    CustomListsStore.remove(stored, list.id)
                    memberships = memberships.orEmpty() - list.id
                } else {
                    CustomListsStore.add(target, list.id)
                    memberships = CustomListsStore.memberships(target)
                }
                error = null
            } catch (e: Exception) {
                error = e.message
            } finally {
                busy = busy - list.id
            }
        }
    }

    Dialog(onDismissRequest = close, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(480.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text("Manage Lists", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(target.name, fontSize = 15.sp, color = Color.White.copy(alpha = 0.6f))
                error?.let { Text(it, fontSize = 14.sp, color = Color(0xFFFF6B6B)) }
                val loaded = memberships
                if (loaded == null) {
                    CircularProgressIndicator(Modifier.padding(16.dp).size(24.dp).align(Alignment.CenterHorizontally), color = Color.White, strokeWidth = 2.dp)
                }
                LazyColumn(Modifier.heightIn(max = 360.dp).padding(top = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (loaded != null) items(CustomListsStore.userLists, key = { it.id }) { list ->
                        val member = loaded[list.id] != null
                        ListItem(
                            selected = false, onClick = { toggle(list) },
                            modifier = if (list == CustomListsStore.userLists.first()) Modifier.focusRequester(first) else Modifier,
                            leadingContent = { Icon(if (member) Icons.Filled.CheckCircle else AppIcons.Circle, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            headlineContent = { Text(list.name) },
                            trailingContent = {
                                if (list.id in busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                else list.itemCount?.let { Text("$it") }
                            },
                        )
                    }
                    item {
                        ListItem(
                            selected = false, onClick = { naming = true },
                            modifier = if (loaded != null && CustomListsStore.userLists.isEmpty()) Modifier.focusRequester(first) else Modifier,
                            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp)) },
                            headlineContent = { Text("New List…") },
                        )
                    }
                }
            }
        }
    }

    if (naming) {
        ListNameDialog(
            title = "New List",
            message = "The new list will start with ${target.name}.",
            confirm = "Create and Add",
            onDismiss = { naming = false },
        ) { name ->
            val list = CustomListsStore.create(name)
            CustomListsStore.add(target, list.id)
            memberships = CustomListsStore.memberships(target)
        }
    }
}

/**
 * A list name prompt (new or rename). The name is checked against the profile's lists
 * before [onSubmit] runs; its failure is shown in place and keeps the dialog open.
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
    val field = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { field.requestFocus() } }
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
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)), contentAlignment = Alignment.Center) {
            Column(
                Modifier.width(460.dp).background(Color(0xFF1C1C1E), RoundedCornerShape(20.dp)).padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                message?.let { Text(it, color = Color.White.copy(alpha = 0.7f)) }
                FormField(name, { name = it; problem = null }, "Name", imeAction = ImeAction.Done, onSubmit = ::submit, modifier = Modifier.focusRequester(field))
                problem?.let { Text(it, fontSize = 14.sp, color = Color(0xFFFF6B6B)) }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End)) {
                    OutlinedButton(onClick = onDismiss) { Text("Cancel") }
                    Button(onClick = ::submit) { Text(if (working) "Saving…" else confirm) }
                }
            }
        }
    }
}
