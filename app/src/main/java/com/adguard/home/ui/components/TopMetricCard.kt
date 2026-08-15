package com.adguard.home.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.adguard.home.domain.model.TopClientItem
import com.adguard.home.domain.model.TopDomainItem
import com.adguard.home.domain.model.TopUpstreamItem
import com.adguard.home.domain.model.UpstreamResponseTimeItem
import java.text.NumberFormat
import java.util.Locale

fun middleEllipsize(text: String, maxLength: Int = 28): String {
    if (text.length <= maxLength) return text
    val partLength = (maxLength - 3) / 2
    return text.take(partLength) + "..." + text.takeLast(partLength)
}

data class TopMetricRowData(
    val title: String,
    val countText: String,
    val percentageText: String? = null,
    val progress: Float = 0f,
    val isDomain: Boolean = false
)

@Composable
fun TopMetricCard(
    title: String,
    items: List<TopMetricRowData>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    onBlockDomain: ((String) -> Unit)? = null,
    onUnblockDomain: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (items.isEmpty()) {
                Text(
                    text = "No data available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.title }) { item ->
                        TopMetricRow(
                            item = item,
                            barColor = barColor,
                            onBlockDomain = onBlockDomain,
                            onUnblockDomain = onUnblockDomain
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TopDomainsMetricCard(
    title: String,
    items: List<TopDomainItem>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    onBlockDomain: ((String) -> Unit)? = null,
    onUnblockDomain: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val rowData = items.map {
        TopMetricRowData(
            title = it.domain,
            countText = numberFormat.format(it.count),
            percentageText = "%.1f%%".format(it.percentage),
            progress = (it.percentage / 100f).coerceIn(0f, 1f),
            isDomain = true
        )
    }
    TopMetricCard(
        title = title,
        items = rowData,
        barColor = barColor,
        onBlockDomain = onBlockDomain,
        onUnblockDomain = onUnblockDomain,
        modifier = modifier
    )
}

@Composable
fun TopClientsMetricCard(
    title: String,
    items: List<TopClientItem>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val rowData = items.map {
        TopMetricRowData(
            title = it.ipOrName,
            countText = numberFormat.format(it.count),
            percentageText = "%.1f%%".format(it.percentage),
            progress = (it.percentage / 100f).coerceIn(0f, 1f),
            isDomain = false
        )
    }
    TopMetricCard(
        title = title,
        items = rowData,
        barColor = barColor,
        modifier = modifier
    )
}

@Composable
fun TopUpstreamsMetricCard(
    title: String,
    items: List<TopUpstreamItem>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val numberFormat = remember { NumberFormat.getNumberInstance(Locale.getDefault()) }
    val rowData = items.map {
        TopMetricRowData(
            title = it.address,
            countText = numberFormat.format(it.count),
            percentageText = "%.1f%%".format(it.percentage),
            progress = (it.percentage / 100f).coerceIn(0f, 1f),
            isDomain = false
        )
    }
    TopMetricCard(
        title = title,
        items = rowData,
        barColor = barColor,
        modifier = modifier
    )
}

@Composable
fun UpstreamResponseTimesCard(
    title: String,
    items: List<UpstreamResponseTimeItem>,
    barColor: Color = MaterialTheme.colorScheme.primary,
    modifier: Modifier = Modifier
) {
    val maxTime = items.maxOfOrNull { it.responseTimeMs } ?: 1.0
    val rowData = items.map {
        TopMetricRowData(
            title = it.address,
            countText = "%.1f ms".format(it.responseTimeMs),
            percentageText = null,
            progress = (it.responseTimeMs / maxTime).toFloat().coerceIn(0f, 1f),
            isDomain = false
        )
    }
    TopMetricCard(
        title = title,
        items = rowData,
        barColor = barColor,
        modifier = modifier
    )
}

@Composable
private fun TopMetricRow(
    item: TopMetricRowData,
    barColor: Color,
    onBlockDomain: ((String) -> Unit)?,
    onUnblockDomain: ((String) -> Unit)?
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = middleEllipsize(item.title, 32),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.countText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (item.percentageText != null) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = item.percentageText,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (item.isDomain && (onBlockDomain != null || onUnblockDomain != null)) {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "Domain actions",
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false }
                        ) {
                            onBlockDomain?.let { block ->
                                DropdownMenuItem(
                                    text = { Text("Block domain") },
                                    leadingIcon = { Icon(Icons.Default.Block, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        block(item.title)
                                    }
                                )
                            }
                            onUnblockDomain?.let { unblock ->
                                DropdownMenuItem(
                                    text = { Text("Unblock domain") },
                                    leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                                    onClick = {
                                        menuExpanded = false
                                        unblock(item.title)
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        // Progress bar
        LinearProgressIndicator(
            progress = { item.progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}
