package com.rsb.sheetsui.presentation.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rsb.sheetsui.domain.model.ColumnValidation
import com.rsb.sheetsui.domain.model.FieldType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditRowScreen(
    onBack: () -> Unit,
    onSaved: () -> Unit,
    onDeleted: () -> Unit = {},
    onClone: () -> Unit = {},
    viewModel: EditRowViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleting by viewModel.deleting.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showMergedWarning by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is EditUiState.Success) {
            onSaved()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "Edit Row ${viewModel.rowIndex + 1}",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            viewModel.spreadsheetName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    val editingOrSaving = state is EditUiState.Editing || state is EditUiState.Saving
                    if (editingOrSaving) {
                        IconButton(
                            onClick = onClone,
                            enabled = state is EditUiState.Editing
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "Clone row")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            val editingOrSaving = state is EditUiState.Editing || state is EditUiState.Saving
            if (editingOrSaving) {
                val hasMergedFields = (state as? EditUiState.Editing)?.fields?.any { it.isMerged } == true
                FloatingActionButton(
                    onClick = {
                        if (hasMergedFields) {
                            showMergedWarning = true
                        } else {
                            viewModel.save()
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    if (state is EditUiState.Saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (val current = state) {
                is EditUiState.Loading -> LoadingContent()
                is EditUiState.Editing -> EditForm(
                    fields = current.fields,
                    onFieldChange = viewModel::updateField,
                    enabled = true,
                    onDeleteClick = { showDeleteConfirm = true }
                )
                is EditUiState.Saving -> {
                    Column {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        EditForm(
                            fields = current.fields,
                            onFieldChange = { _, _ -> },
                            enabled = false,
                            onDeleteClick = {}
                        )
                    }
                }
                is EditUiState.Success -> Unit
                is EditUiState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = { viewModel.retryLoad() }
                )
                is EditUiState.ConflictDetected -> {
                    EditForm(
                        fields = current.editingState.fields,
                        onFieldChange = { _, _ -> },
                        enabled = false,
                        onDeleteClick = {}
                    )
                }
            }
        }
    }

    if (showMergedWarning) {
        AlertDialog(
            onDismissRequest = { showMergedWarning = false },
            title = { Text("Merged cell") },
            text = {
                Text("This cell is merged. Updating it will change the value for the entire merged area.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showMergedWarning = false
                        viewModel.save()
                    }
                ) {
                    Text("Continue")
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergedWarning = false }) { Text("Cancel") }
            }
        )
    }

    if (state is EditUiState.ConflictDetected) {
        val conflict = state as EditUiState.ConflictDetected
        AlertDialog(
            onDismissRequest = { viewModel.onConflictDismiss(conflict.editingState) },
            title = { Text("Conflict Detected") },
            text = { Text(conflict.message) },
            confirmButton = {
                TextButton(onClick = { viewModel.onConflictRefresh(conflict.editingState) }) {
                    Text("Refresh")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.onConflictDismiss(conflict.editingState) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete row") },
            text = {
                Text("Delete row ${viewModel.rowIndex + 1}? This cannot be undone.")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteConfirm = false
                        viewModel.deleteRow(onDeleted)
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun EditForm(
    fields: List<EditableField>,
    onFieldChange: (Int, String) -> Unit,
    enabled: Boolean,
    onDeleteClick: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        fields.forEach { field ->
            Column {
                when (val v = field.validation) {
                    is ColumnValidation.Dropdown -> SmartDropdownField(
                        label = field.header,
                        value = field.value,
                        options = v.options,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    is ColumnValidation.Checkbox -> SmartBooleanField(
                        label = field.header,
                        value = field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    null -> when (field.type) {
                    FieldType.DATE -> SmartDateField(
                        label = field.header,
                        value = field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    FieldType.FORMULA -> SmartFormulaField(
                        label = field.header,
                        formulaValue = field.value,
                        displayValue = field.displayValue ?: field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    FieldType.CURRENCY -> SmartCurrencyField(
                        label = field.header,
                        value = field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    FieldType.NUMBER -> SmartNumberField(
                        label = field.header,
                        value = field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    FieldType.BOOLEAN -> SmartBooleanField(
                        label = field.header,
                        value = field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                    FieldType.TEXT -> SmartTextField(
                        label = field.header,
                        value = field.value,
                        onValueChange = { if (enabled) onFieldChange(field.columnIndex, it) }
                    )
                }
                }
                if (field.isMerged) {
                    Text(
                        "This cell is merged. Editing will update the entire merged area.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onDeleteClick,
            enabled = enabled,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError
            )
        ) {
            Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("Delete Row")
        }
        Spacer(Modifier.height(80.dp))
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(
                "Loading row data…",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                "Error",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
