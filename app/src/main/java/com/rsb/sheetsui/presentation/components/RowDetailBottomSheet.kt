package com.rsb.sheetsui.presentation.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.rsb.sheetsui.domain.util.CellActionDetector
import com.rsb.sheetsui.domain.util.CellActionType
import com.rsb.sheetsui.presentation.edit.SmartDateField
import com.rsb.sheetsui.presentation.edit.SmartDropdownField

private val STATUS_OPTIONS = listOf("Pending", "Done", "In Progress")

@Composable
private fun trailingIconForAction(
    value: String,
    actionType: CellActionType,
    context: android.content.Context
): @Composable (() -> Unit)? = when (actionType) {
    is CellActionType.Phone -> {
        {
            IconButton(
                onClick = {
                    val uri = CellActionDetector.extractPhoneUri(value)
                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse(uri)))
                }
            ) {
                Icon(Icons.Default.Call, contentDescription = "Call")
            }
        }
    }
    is CellActionType.Address -> {
        {
            IconButton(
                onClick = {
                    val query = CellActionDetector.extractMapQuery(value)
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("geo:0,0?q=$query")))
                }
            ) {
                Icon(Icons.Default.LocationOn, contentDescription = "Map")
            }
        }
    }
    is CellActionType.Url -> {
        {
            IconButton(
                onClick = {
                    val url = CellActionDetector.extractUrl(value)
                    if (url != null) {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                }
            ) {
                Icon(Icons.Default.Link, contentDescription = "Open link")
            }
        }
    }
    else -> null
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RowDetailBottomSheet(
    headers: List<String>,
    row: List<Any?>,
    onDismiss: () -> Unit,
    onEdit: (() -> Unit)? = null,
    onCellChange: ((columnIndex: Int, newValue: String) -> Unit)? = null
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                "Row Details",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            headers.forEachIndexed { idx, header ->
                val value = row.getOrElse(idx) { null }?.toString()?.trim().orEmpty()
                val headerLower = header.lowercase()
                val isDateCol = headerLower.contains("date")
                val isStatusCol = headerLower == "status" || headerLower.contains("status")
                val canEdit = onCellChange != null && (isDateCol || isStatusCol)

                when {
                    isDateCol && canEdit -> SmartDateField(
                        label = header.ifEmpty { "Column ${idx + 1}" },
                        value = value,
                        onValueChange = { onCellChange?.invoke(idx, it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    isStatusCol && canEdit -> SmartDropdownField(
                        label = header.ifEmpty { "Column ${idx + 1}" },
                        value = value,
                        options = STATUS_OPTIONS,
                        onValueChange = { onCellChange?.invoke(idx, it) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    else -> {
                        val actionType = CellActionDetector.detect(value)
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = value,
                                onValueChange = { },
                                label = { Text(header.ifEmpty { "Column ${idx + 1}" }) },
                                modifier = Modifier.weight(1f),
                                readOnly = true,
                                trailingIcon = trailingIconForAction(value, actionType, context)
                            )
                        }
                    }
                }
            }
            if (onEdit != null) {
                TextButton(onClick = { onDismiss(); onEdit() }) {
                    Text("Edit row")
                }
            }
        }
    }
}


