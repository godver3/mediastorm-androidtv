package com.strmr.mobile.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.strmr.tv.api.DetailsTarget
import com.strmr.tv.home.CustomList
import com.strmr.tv.home.WatchlistItem
import com.strmr.tv.library.CustomListsStore
import kotlinx.coroutines.launch

/**
 * Add to List… (the iPhone app's Manage Lists sheet): each of the profile's lists, checked
 * where it holds the title. Tapping a list adds or removes the title in place; New List…
 * creates one with the title in it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToListSheet(target: DetailsTarget, onDismiss: () -> Unit) {
    val scope = rememberCoroutineScope()
    var memberships by remember { mutableStateOf<Map<String, WatchlistItem>?>(null) }
    var busy by remember { mutableStateOf(emptySet<String>()) }
    var error by remember { mutableStateOf<String?>(null) }
    var naming by remember { mutableStateOf(false) }
    LaunchedEffect(target.titleId) {
        CustomListsStore.refresh()
        memberships = CustomListsStore.memberships(target)
    }

    fun toggle(list: CustomList) {
        if (list.id in busy) return
        busy = busy + list.id
        scope.launch {
            try {
                val item = memberships?.get(list.id)
                if (item != null) {
                    CustomListsStore.remove(item, list.id)
                    memberships = memberships.orEmpty() - list.id
                } else {
                    CustomListsStore.add(target, list.id)
                    memberships = CustomListsStore.memberships(target)
                }
                error = null
            } catch (e: Exception) {
                error = e.message ?: "Couldn't update the list."
            } finally {
                busy = busy - list.id
            }
        }
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState()) {
        Column(Modifier.navigationBarsPadding()) {
            SheetHeader("Add to List", target.name, null)
            error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium,
                     modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp))
            }
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            LazyColumn(Modifier.fillMaxWidth()) {
                val lists = CustomListsStore.userLists
                if (memberships == null) {
                    item { Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
                } else {
                    items(lists, key = { it.id }) { list ->
                        val member = memberships?.containsKey(list.id) == true
                        ListItem(
                            headlineContent = { Text(list.name) },
                            leadingContent = {
                                Icon(
                                    if (member) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
                                    contentDescription = if (member) "In list" else "Not in list",
                                    tint = if (member) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            trailingContent = {
                                if (list.id in busy) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                                else list.itemCount?.let { Text(it.toString()) }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier.clickable(enabled = list.id !in busy) { toggle(list) },
                        )
                    }
                }
                item {
                    ListItem(
                        headlineContent = { Text("New List…") },
                        leadingContent = { Icon(Icons.Outlined.Add, contentDescription = null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable { naming = true },
                    )
                }
            }
        }
    }
    if (naming) {
        NewListDialog(target, onDismiss = { naming = false }) { name ->
            scope.launch {
                try {
                    val list = CustomListsStore.create(name)
                    CustomListsStore.add(target, list.id)
                    onDismiss()
                } catch (e: Exception) {
                    error = e.message ?: "Couldn't create the list."
                }
            }
        }
    }
}

/** "New List": a name, checked against the reserved and taken names before it is created. */
@Composable
private fun NewListDialog(target: DetailsTarget, onDismiss: () -> Unit, onCreate: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    val problem = if (name.isBlank()) null else CustomListsStore.nameProblem(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New List") },
        text = {
            Column {
                Text("The new list will start with ${target.name}.")
                OutlinedTextField(
                    name, { name = it }, label = { Text("Name") }, singleLine = true,
                    isError = problem != null, supportingText = problem?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onDismiss(); onCreate(name.trim()) }, enabled = name.isNotBlank() && problem == null) { Text("Create and Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
