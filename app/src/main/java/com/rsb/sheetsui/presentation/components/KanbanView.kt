package com.rsb.sheetsui.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.rsb.sheetsui.domain.model.SheetData

private val STATUS_HEADERS = setOf("status", "state", "stage", "phase")

@Composable
fun KanbanView(
    data: SheetData,
    onRowClick: (Int, List<Any?>) -> Unit,
    onRowLongPress: (Int) -> Unit = {},
    onEditRow: (Int) -> Unit = {}
) {
    val statusCol = data.headers.indexOfFirst { h ->
        STATUS_HEADERS.any { h.contains(it, ignoreCase = true) }
    }
    val grouped = if (statusCol >= 0) {
        data.rows.mapIndexed { idx, row ->
            val status = (row.getOrElse(statusCol) { null }?.toString()?.trim() ?: "").takeIf { it.isNotBlank() } ?: "(No status)"
            status to (idx to row)
        }.groupBy({ it.first }, { it.second })
    } else {
        mapOf("All" to data.rows.mapIndexed { idx, row -> idx to row })
    }
    val columnWidth = 280.dp

    LazyRow(
        modifier = Modifier.fillMaxSize(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp)
    ) {
        grouped.forEach { (columnTitle, rows) ->
            item(key = columnTitle) {
                Column(
                    modifier = Modifier
                        .width(columnWidth)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.5f))
                        .padding(8.dp)
                ) {
                    Text(
                        "$columnTitle (${rows.size})",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(rows, key = { (idx, _) -> idx }) { (rowIndex, row) ->
                            KanbanCard(
                                headers = data.headers,
                                row = row,
                                onClick = { onRowClick(rowIndex, row) },
                                onLongClick = { onRowLongPress(rowIndex) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KanbanCard(
    headers: List<String>,
    row: List<Any?>,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val primaryCol = headers.indexOfFirst { it.isNotBlank() }
    val primaryText = if (primaryCol >= 0) {
        row.getOrElse(primaryCol) { null }?.toString()?.trim().orEmpty()
    } else ""
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            if (primaryText.isNotBlank()) {
                Text(
                    primaryText,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            headers.forEachIndexed { idx, header ->
                if (idx != primaryCol && header.isNotBlank()) {
                    val v = row.getOrElse(idx) { null }?.toString()?.trim().orEmpty()
                    if (v.isNotBlank()) {
                        Text(
                            "$header: $v",
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
