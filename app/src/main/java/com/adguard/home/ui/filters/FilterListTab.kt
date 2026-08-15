package com.adguard.home.ui.filters

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adguard.home.domain.model.FilterItem
import com.adguard.home.ui.components.middleEllipsize
import java.text.NumberFormat
import java.util.Locale

@Composable
fun FilterListTab(
    whitelist: Boolean,
    items: List<FilterItem>,
    isCheckingUpdates: Boolean,
    updateIntervalHours: Int?,
    onSetUpdateInterval: (Int) -> Unit,
    onToggle: (FilterItem, Boolean) -> Unit,
    onAddFilter: (String, String) -> Unit,
    onEditFilter: (String, String, String) -> Unit,
    onDeleteFilter: (FilterItem) -> Unit,
    onCheckForUpdates: () -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showIntervalMenu by remember { mutableStateOf(false) }
    var filterToEdit by remember { mutableStateOf<FilterItem?>(null) }
    var filterToDelete by remember { mutableStateOf<FilterItem?>(null) }

    val numberFormat = NumberFormat.getNumberInstance(Locale.getDefault())
    val totalRules = items.filter { it.isEnabled }.sumOf { it.rulesCount }
    val enabledCount = items.count { it.isEnabled }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Aggregate Summary Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (whitelist) "Allowlists Overview" else "Blocklists Overview",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$enabledCount of ${items.size} enabled • ${numberFormat.format(totalRules)} active rules",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = onCheckForUpdates,
                                enabled = !isCheckingUpdates
                            ) {
                                if (isCheckingUpdates) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Default.Sync, contentDescription = "Check for updates", tint = MaterialTheme.colorScheme.primary)
                                }
                            }

                            Box {
                                IconButton(onClick = { showIntervalMenu = true }) {
                                    Icon(Icons.Default.MoreVert, contentDescription = "Auto-update interval")
                                }
                                DropdownMenu(
                                    expanded = showIntervalMenu,
                                    onDismissRequest = { showIntervalMenu = false }
                                ) {
                                    Text(
                                        text = "Auto-update interval:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                    UPDATE_INTERVALS.forEach { (label, hours) ->
                                        val isSelected = updateIntervalHours == hours
                                        DropdownMenuItem(
                                            text = { Text(if (isSelected) "✓ $label" else "   $label") },
                                            onClick = {
                                                showIntervalMenu = false
                                                onSetUpdateInterval(hours)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (items.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 64.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.outlineVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = if (whitelist) "No allowlists configured" else "No blocklists configured",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Button(onClick = { showAddDialog = true }) {
                                Text(if (whitelist) "Add allowlist" else "Add blocklist")
                            }
                        }
                    }
                }
            } else {
                items(items, key = { it.url }) { filter ->
                    FilterCardItem(
                        filter = filter,
                        numberFormat = numberFormat,
                        onToggle = { enabled -> onToggle(filter, enabled) },
                        onEdit = { filterToEdit = filter },
                        onDelete = { filterToDelete = filter }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        // FAB to add new list
        FloatingActionButton(
            onClick = { showAddDialog = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp),
            containerColor = MaterialTheme.colorScheme.primary
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add List")
        }
    }

    if (showAddDialog) {
        FilterEditDialog(
            title = if (whitelist) "Add Allowlist" else "Add Blocklist",
            initialName = "",
            initialUrl = "",
            onDismiss = { showAddDialog = false },
            onConfirm = { name, url ->
                showAddDialog = false
                onAddFilter(name, url)
            }
        )
    }

    filterToEdit?.let { filter ->
        FilterEditDialog(
            title = "Edit Filter List",
            initialName = filter.name,
            initialUrl = filter.url,
            onDismiss = { filterToEdit = null },
            onConfirm = { name, url ->
                val originalUrl = filter.url
                filterToEdit = null
                onEditFilter(originalUrl, name, url)
            }
        )
    }

    filterToDelete?.let { filter ->
        AlertDialog(
            onDismissRequest = { filterToDelete = null },
            title = { Text("Delete filter list?") },
            text = { Text("Are you sure you want to delete \"${filter.name}\"?") },
            confirmButton = {
                Button(
                    onClick = {
                        val item = filter
                        filterToDelete = null
                        onDeleteFilter(item)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { filterToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FilterCardItem(
    filter: FilterItem,
    numberFormat: NumberFormat,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var showAbsoluteTimestamp by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f)
            ) {
                // Line 1: Name
                Text(
                    text = filter.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                // Line 2: Source URL (middle-ellipsized)
                Text(
                    text = middleEllipsize(filter.url, 42),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Line 3: Rule count • updated time
                val timeLabel = if (showAbsoluteTimestamp && filter.lastUpdatedRaw != null) {
                    filter.lastUpdatedRaw.take(19).replace('T', ' ')
                } else {
                    filter.lastUpdatedRelative
                }

                Text(
                    text = "${numberFormat.format(filter.rulesCount)} rules • $timeLabel",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.combinedClickable(
                        onClick = { showAbsoluteTimestamp = !showAbsoluteTimestamp },
                        onLongClick = { showAbsoluteTimestamp = !showAbsoluteTimestamp }
                    )
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            // Switch
            Switch(
                checked = filter.isEnabled,
                onCheckedChange = onToggle
            )

            // Overflow Menu
            Box {
                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = "List Options")
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuExpanded = false
                            onEdit()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Copy URL") },
                        onClick = {
                            menuExpanded = false
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Filter URL", filter.url))
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Open URL in browser") },
                        onClick = {
                            menuExpanded = false
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(filter.url))
                            context.startActivity(intent)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterEditDialog(
    title: String,
    initialName: String,
    initialUrl: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String, url: String) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var url by remember { mutableStateOf(initialUrl) }
    var errorText by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("List Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = url,
                    onValueChange = {
                        url = it.trim()
                        errorText = null
                    },
                    label = { Text("URL") },
                    placeholder = { Text("https://example.com/filter.txt") },
                    singleLine = true,
                    isError = errorText != null,
                    supportingText = errorText?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanUrl = url.trim()
                    if (cleanUrl.isBlank() || !cleanUrl.startsWith("http")) {
                        errorText = "Please enter a valid HTTP/HTTPS URL"
                    } else if (name.isBlank()) {
                        errorText = "Please enter a name"
                    } else {
                        onConfirm(name.trim(), cleanUrl)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
