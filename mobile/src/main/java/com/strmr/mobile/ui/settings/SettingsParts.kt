package com.strmr.mobile.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.strmr.mobile.ui.shell.LocalNavigator
import com.strmr.mobile.ui.shell.PageScaffold
import com.strmr.mobile.ui.shell.Route

/** A settings page's frame: the top bar, then grouped sections in a scrolling column, centred on tablets. */
@Composable
fun SettingsPage(title: String, showBack: Boolean = true, large: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    PageScaffold(title, showBack = showBack, large = large) { padding ->
        Column(
            Modifier.padding(padding).fillMaxSize().verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                Modifier.widthIn(max = 640.dp).fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                content = content,
            )
        }
    }
}

/** An inset group of rows with an optional header and footer, as Android's own Settings. */
@Composable
fun SettingsSection(header: String? = null, footer: String? = null, rows: @Composable ColumnScope.() -> Unit) {
    if (header != null) {
        Text(
            header, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
        )
    } else {
        Spacer(Modifier.height(16.dp))
    }
    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(content = rows)
    }
    footer?.let {
        Text(
            it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp),
        )
    }
}

private val rowColors @Composable get() = ListItemDefaults.colors(containerColor = Color.Transparent)

/** A row; with [onClick] it is tappable. */
@Composable
fun SettingsRow(
    title: String,
    supporting: String? = null,
    icon: ImageVector? = null,
    trailing: (@Composable () -> Unit)? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = supporting?.let { { Text(it, maxLines = 3, overflow = TextOverflow.Ellipsis) } },
        leadingContent = icon?.let { { Icon(it, null) } },
        trailingContent = trailing,
        colors = rowColors,
        modifier = Modifier.then(if (onClick != null) Modifier.clickable(enabled = enabled, onClick = onClick) else Modifier),
    )
}

/** A row naming its value at the end. */
@Composable
fun ValueRow(title: String, value: String, supporting: String? = null, icon: ImageVector? = null, onClick: (() -> Unit)? = null) {
    SettingsRow(title, supporting, icon, trailing = {
        Text(
            value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 180.dp),
        )
    }, onClick = onClick)
}

/** A row opening a settings sub page. */
@Composable
fun PageRow(title: String, key: String, supporting: String? = null, icon: ImageVector? = null) {
    val navigator = LocalNavigator.current
    SettingsRow(title, supporting, icon, trailing = { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }) {
        navigator.push(Route.SettingsPage(key, title))
    }
}

/** An on / off row; the whole row toggles. */
@Composable
fun SwitchRow(title: String, checked: Boolean, supporting: String? = null, enabled: Boolean = true, onChange: (Boolean) -> Unit) {
    SettingsRow(title, supporting, trailing = { Switch(checked, onChange, enabled = enabled) }, enabled = enabled) { onChange(!checked) }
}

/**
 * A row whose picker offers "Default (server value)" and then [choices]; [selected] is the
 * device's choice, null for Default. [currentName] names a choice outside the list.
 */
@Composable
fun OverrideRow(
    title: String,
    choices: List<String>,
    selected: Int?,
    defaultName: String?,
    supporting: String? = null,
    currentName: String? = null,
    onPick: (Int?) -> Unit,
) {
    val defaultLabel = "Default" + (defaultName?.let { " ($it)" } ?: "")
    PickerRow(
        title, listOf(defaultLabel) + choices, selected?.let { it + 1 } ?: 0, supporting,
        value = selected?.let { choices[it] } ?: currentName ?: defaultLabel,
    ) { onPick(if (it == 0) null else it - 1) }
}

/** A row showing its current choice, opening a dialog of [choices]. */
@Composable
fun PickerRow(title: String, choices: List<String>, selected: Int, supporting: String? = null, value: String? = null, onPick: (Int) -> Unit) {
    var picking by remember { mutableStateOf(false) }
    ValueRow(title, value ?: choices.getOrElse(selected) { "" }, supporting) { picking = true }
    if (picking) ChoiceDialog(title, choices, selected, onDismiss = { picking = false }) { onPick(it); picking = false }
}

/** A single-choice dialog: radio rows, closing on a pick. */
@Composable
fun ChoiceDialog(title: String, choices: List<String>, selected: Int, onDismiss: () -> Unit, onPick: (Int) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            LazyColumn(Modifier.heightIn(max = 420.dp), contentPadding = PaddingValues(vertical = 4.dp)) {
                itemsIndexed(choices) { i, choice ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 48.dp)
                            .selectable(selected = i == selected, role = Role.RadioButton) { onPick(i) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Text(choice, Modifier.padding(start = 16.dp), style = MaterialTheme.typography.bodyLarge)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** A thin rule between rows of a section. */
@Composable
fun RowDivider() = HorizontalDivider(Modifier.padding(start = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

fun onOff(value: Boolean) = if (value) "On" else "Off"
