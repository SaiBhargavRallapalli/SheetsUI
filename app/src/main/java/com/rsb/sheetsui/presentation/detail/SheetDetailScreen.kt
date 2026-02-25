package com.rsb.sheetsui.presentation.detail

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rsb.sheetsui.domain.repository.CreatedForm
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rsb.sheetsui.domain.model.FieldType
import com.rsb.sheetsui.domain.model.SheetData
import com.rsb.sheetsui.domain.model.SheetTab
import com.rsb.sheetsui.presentation.components.KanbanView
import com.rsb.sheetsui.presentation.components.RowDetailBottomSheet
import com.rsb.sheetsui.domain.repository.ExportFormat
import com.rsb.sheetsui.domain.util.ReceiptData
import com.rsb.sheetsui.presentation.form.FormSharingBottomSheet
import kotlinx.coroutines.launch

@Composable
private fun ChangeTypeDialog(
    headerName: String,
    onDismiss: () -> Unit,
    onSelect: (FieldType) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change type: $headerName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(FieldType.TEXT, FieldType.NUMBER, FieldType.CURRENCY, FieldType.DATE, FieldType.BOOLEAN, FieldType.FORMULA).forEach { type ->
                    TextButton(
                        onClick = { onSelect(type) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(type.name)
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

private fun launchShareIntent(context: android.content.Context, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    context.startActivity(Intent.createChooser(intent, null))
}

private val CELL_MIN_WIDTH: Dp = 120.dp
private val CELL_PADDING: Dp = 12.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetDetailScreen(
    onBack: () -> Unit,
    onRowClick: (rowIndex: Int, sheetName: String) -> Unit = { _, _ -> },
    onAddRow: (sheetName: String) -> Unit = {},
    shouldRefresh: Boolean = false,
    viewModel: SheetDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val sheetTabs by viewModel.sheetTabs.collectAsStateWithLifecycle()
    val selectedSheetIndex by viewModel.selectedSheetIndex.collectAsStateWithLifecycle()
    val selectedSheetName by viewModel.selectedSheetName.collectAsStateWithLifecycle()
    val addingColumn by viewModel.addingColumn.collectAsStateWithLifecycle()
    val deletingRow by viewModel.deletingRow.collectAsStateWithLifecycle()
    val initializing by viewModel.initializing.collectAsStateWithLifecycle()
    val exporting by viewModel.exporting.collectAsStateWithLifecycle()
    val creatingForm by viewModel.creatingForm.collectAsStateWithLifecycle()
    val provisioning by viewModel.provisioning.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var showMenu by remember { mutableStateOf(false) }
    var showFormSharingSheet by remember { mutableStateOf<CreatedForm?>(null) }
    var showAddColumnDialog by rememberSaveable { mutableStateOf(false) }
    var showManageColumnsDialog by rememberSaveable { mutableStateOf(false) }
    var showCreateSheetDialog by rememberSaveable { mutableStateOf(false) }
    var showNewSheetDialog by rememberSaveable { mutableStateOf(false) }
    var showDuplicateSheetDialog by rememberSaveable { mutableStateOf(false) }
    var rowToDelete by remember { mutableStateOf<Int?>(null) }
    var rowDetailForBottomSheet by remember { mutableStateOf<Pair<Int, List<Any?>>?>(null) }
    var viewMode by rememberSaveable { mutableStateOf(false) }
    var showBarcodeOverlay by remember { mutableStateOf(false) }
    var showReceiptOverlay by remember { mutableStateOf(false) }
    var receiptToConfirm by remember { mutableStateOf<ReceiptData?>(null) }
    var showRenameSheetDialog by remember { mutableStateOf(false) }

    LaunchedEffect(shouldRefresh) {
        if (shouldRefresh) viewModel.loadSheetTabs()
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is DetailEvent.ColumnAdded ->
                    snackbarHostState.showSnackbar("Column \"${event.name}\" added")
                is DetailEvent.ColumnRenamed ->
                    snackbarHostState.showSnackbar("Column renamed to \"${event.name}\"")
                is DetailEvent.ColumnDeleted ->
                    snackbarHostState.showSnackbar("Column deleted")
                is DetailEvent.RowDeleted ->
                    snackbarHostState.showSnackbar("Row deleted")
                is DetailEvent.HeadersInitialized ->
                    snackbarHostState.showSnackbar("Headers initialized")
                is DetailEvent.ColumnTypeChanged ->
                    snackbarHostState.showSnackbar("Column type set to ${event.fieldType}")
                is DetailEvent.ExportSaved ->
                    snackbarHostState.showSnackbar("Saved to Downloads")
                is DetailEvent.FormCreated ->
                    showFormSharingSheet = event.form
                is DetailEvent.SheetAdded -> {
                    snackbarHostState.showSnackbar("Sheet \"${event.title}\" created")
                    showNewSheetDialog = false
                }
                is DetailEvent.SheetDuplicated -> {
                    snackbarHostState.showSnackbar("Sheet \"${event.title}\" duplicated")
                    showDuplicateSheetDialog = false
                }
                is DetailEvent.SheetRenamed ->
                    snackbarHostState.showSnackbar("Sheet renamed to \"${event.title}\"")
                is DetailEvent.RowAppended ->
                    snackbarHostState.showSnackbar("Receipt added")
                is DetailEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    var columnToChangeType by remember { mutableStateOf<Int?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(viewModel.spreadsheetName, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (state is DetailUiState.Success || state is DetailUiState.Empty) {
                        Box {
                            IconButton(onClick = { showMenu = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("Share spreadsheet") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        launchShareIntent(context, "https://docs.google.com/spreadsheets/d/${viewModel.spreadsheetId}/edit")
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Download as CSV") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exportSpreadsheet(ExportFormat.CSV)
                                    },
                                    enabled = !exporting
                                )
                                DropdownMenuItem(
                                    text = { Text("Download as Excel") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exportSpreadsheet(ExportFormat.XLSX)
                                    },
                                    enabled = !exporting
                                )
                                DropdownMenuItem(
                                    text = { Text("Download as PDF") },
                                    leadingIcon = { Icon(Icons.Default.Download, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.exportSpreadsheet(ExportFormat.PDF)
                                    },
                                    enabled = !exporting
                                )
                                DropdownMenuItem(
                                    text = { Text("Add column") },
                                    leadingIcon = { Icon(Icons.Default.ViewColumn, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showAddColumnDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Manage columns") },
                                    leadingIcon = { Icon(Icons.Default.ViewColumn, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showManageColumnsDialog = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan barcode") },
                                    leadingIcon = { Icon(Icons.Default.QrCodeScanner, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showBarcodeOverlay = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Scan receipt") },
                                    leadingIcon = { Icon(Icons.Default.ReceiptLong, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showReceiptOverlay = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Create Form from Sheet") },
                                    leadingIcon = { Icon(Icons.Default.TableChart, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        viewModel.createFormFromSpreadsheet()
                                    },
                                    enabled = !creatingForm
                                )
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text(if (viewMode) "Grid view" else "Board view") },
                                    leadingIcon = {
                                        Icon(
                                            if (viewMode) Icons.Default.GridView else Icons.Default.ViewAgenda,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        viewMode = !viewMode
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Create Sheet") },
                                    leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        showCreateSheetDialog = true
                                    },
                                    enabled = !provisioning
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (state is DetailUiState.Success || state is DetailUiState.Empty) {
                FloatingActionButton(onClick = { onAddRow(selectedSheetName) }) {
                    Icon(Icons.Default.Add, contentDescription = "Add row")
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (sheetTabs.isNotEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = selectedSheetIndex,
                            modifier = Modifier.weight(1f),
                            edgePadding = 0.dp
                        ) {
                            sheetTabs.forEachIndexed { index, tab ->
                                Tab(
                                    selected = selectedSheetIndex == index,
                                    onClick = { viewModel.selectSheet(index) },
                                    text = {
                                        Text(
                                            tab.title,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                )
                            }
                        }
                        val selectedTab = sheetTabs.getOrNull(selectedSheetIndex)
                        selectedTab?.let { tab ->
                            IconButton(onClick = { showRenameSheetDialog = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename sheet")
                            }
                        }
                        IconButton(onClick = { showCreateSheetDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "New or duplicate sheet")
                        }
                    }
                }
                Box(modifier = Modifier.weight(1f)) {
                    when (val current = state) {
                        is DetailUiState.Loading -> LoadingContent()
                        is DetailUiState.NoHeadersFound -> SetupSheetContent(
                    initializing = initializing,
                    onInitialize = { headerLine -> viewModel.initializeHeaders(headerLine) }
                )
                is DetailUiState.Empty -> EmptyContent()
                is DetailUiState.Success -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (viewMode) {
                        KanbanView(
                            data = current.data,
                            onRowClick = { rowIndex, row -> rowDetailForBottomSheet = rowIndex to row },
                            onRowLongPress = { rowToDelete = it },
                            onEditRow = { rowIndex -> onRowClick(rowIndex, selectedSheetName) }
                        )
                        } else {
                        SheetTable(
                            data = current.data,
                            onRowClick = { rowIndex ->
                                val row = current.data.rows.getOrNull(rowIndex)
                                if (row != null) rowDetailForBottomSheet = rowIndex to row
                            },
                        onRowLongPress = { rowIndex -> rowToDelete = rowIndex },
                        onRowDeleteClick = { rowIndex -> rowToDelete = rowIndex },
                        onHeaderLongPress = { columnToChangeType = it },
                        deletingRow = deletingRow
                    )
                        }
                    }
                }
                is DetailUiState.Error -> ErrorContent(
                    message = current.message,
                    onRetry = { viewModel.loadSheetTabs() }
                )
                    }
                }
            }
            if (provisioning) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator()
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Creating sheet…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    if (showAddColumnDialog) {
        AddColumnDialog(
            adding = addingColumn,
            onDismiss = { showAddColumnDialog = false },
            onAdd = { name ->
                viewModel.addColumn(name)
                showAddColumnDialog = false
            }
        )
    }

    val successData = (state as? DetailUiState.Success)?.data
    if (showManageColumnsDialog && successData != null) {
        ManageColumnsDialog(
            headers = successData.headers,
            onDismiss = { showManageColumnsDialog = false },
            onRename = { colIndex, newName ->
                viewModel.renameColumn(colIndex, newName)
            },
            onDelete = { colIndex ->
                viewModel.deleteColumn(colIndex)
            }
        )
    }

    columnToChangeType?.let { colIdx ->
        ChangeTypeDialog(
            headerName = (state as? DetailUiState.Success)?.data?.headers?.getOrElse(colIdx) { "" } ?: "",
            onDismiss = { columnToChangeType = null },
            onSelect = { type ->
                viewModel.setColumnTypeOverride(colIdx, type)
                columnToChangeType = null
            }
        )
    }

    showFormSharingSheet?.let { form ->
        FormSharingBottomSheet(
            form = form,
            onDismiss = { showFormSharingSheet = null },
            fromSpreadsheet = true
        )
    }

    if (showCreateSheetDialog) {
        CreateSheetChoiceDialog(
            onDismiss = { showCreateSheetDialog = false },
            onNew = {
                showCreateSheetDialog = false
                showNewSheetDialog = true
            },
            onDuplicate = {
                showCreateSheetDialog = false
                showDuplicateSheetDialog = true
            }
        )
    }

    if (showNewSheetDialog) {
        NewSheetDialog(
            provisioning = provisioning,
            onDismiss = { showNewSheetDialog = false },
            onCreate = { name ->
                viewModel.addSheet(name)
            }
        )
    }

    if (showDuplicateSheetDialog && sheetTabs.isNotEmpty()) {
        DuplicateSheetDialog(
            tabs = sheetTabs,
            provisioning = provisioning,
            onDismiss = { showDuplicateSheetDialog = false },
            onDuplicate = { sourceSheetId, newName ->
                viewModel.duplicateSheet(sourceSheetId, newName)
            }
        )
    }

    rowDetailForBottomSheet?.let { (rowIndex, row) ->
        val successData = (state as? DetailUiState.Success)?.data
        if (successData != null) {
            RowDetailBottomSheet(
                headers = successData.headers,
                row = row,
                onDismiss = { rowDetailForBottomSheet = null },
                onEdit = {
                    rowDetailForBottomSheet = null
                    onRowClick(rowIndex, selectedSheetName)
                },
                onCellChange = { colIdx, newValue ->
                    viewModel.updateCell(rowIndex, colIdx, newValue)
                    rowDetailForBottomSheet = rowIndex to row.mapIndexed { i, v ->
                        if (i == colIdx) newValue else v
                    }
                }
            )
        }
    }

    rowToDelete?.let { idx ->
        AlertDialog(
            onDismissRequest = { rowToDelete = null },
            title = { Text("Delete row") },
            text = { Text("Delete row ${idx + 1}? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteRow(idx)
                        rowToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { rowToDelete = null }) { Text("Cancel") }
            }
        )
    }

    if (showBarcodeOverlay) {
        com.rsb.sheetsui.presentation.scan.ScanBarcodeOverlay(
            onResult = { barcode ->
                showBarcodeOverlay = false
                val data = (state as? DetailUiState.Success)?.data ?: return@ScanBarcodeOverlay
                val rowIdx = viewModel.findRowByBarcode(barcode, data)
                if (rowIdx != null) {
                    val row = data.rows.getOrNull(rowIdx)
                    if (row != null) rowDetailForBottomSheet = rowIdx to row
                } else {
                    scope.launch {
                        snackbarHostState.showSnackbar("No row found with ID/SKU: $barcode")
                    }
                }
            },
            onDismiss = { showBarcodeOverlay = false }
        )
    }

    if (showReceiptOverlay) {
        com.rsb.sheetsui.presentation.scan.ScanReceiptOverlay(
            onResult = {
                showReceiptOverlay = false
                receiptToConfirm = it
            },
            onDismiss = { showReceiptOverlay = false }
        )
    }

    receiptToConfirm?.let { receipt ->
        val successData = (state as? DetailUiState.Success)?.data
        if (successData != null) {
            AlertDialog(
                onDismissRequest = { receiptToConfirm = null },
                title = { Text("Confirm Receipt Data") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Merchant: ${receipt.merchantName}")
                        Text("Total: ${receipt.totalAmount}")
                        Text("Date: ${receipt.date}")
                        Text("Type: ${receipt.receiptType}")
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.appendReceiptRow(receipt, successData.headers)
                        receiptToConfirm = null
                    }) { Text("Add to Sheet") }
                },
                dismissButton = {
                    TextButton(onClick = { receiptToConfirm = null }) { Text("Cancel") }
                }
            )
        }
    }

    if (showRenameSheetDialog) {
        val tab = sheetTabs.getOrNull(selectedSheetIndex)
        if (tab != null) {
            var newName by remember(tab) { mutableStateOf(tab.title) }
            AlertDialog(
                onDismissRequest = { showRenameSheetDialog = false },
                title = { Text("Rename Sheet") },
                text = {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Sheet name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !provisioning
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.renameSheet(tab.sheetId, newName.trim())
                            showRenameSheetDialog = false
                        },
                        enabled = newName.isNotBlank() && !provisioning
                    ) { Text("Rename") }
                },
                dismissButton = {
                    TextButton(onClick = { showRenameSheetDialog = false }) { Text("Cancel") }
                }
            )
        }
    }
}

@Composable
private fun CreateSheetChoiceDialog(
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onDuplicate: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Sheet") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Button(
                    onClick = onNew,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("New blank sheet")
                }
                Button(
                    onClick = onDuplicate,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Duplicate existing sheet")
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun NewSheetDialog(
    provisioning: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Sheet") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Sheet name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !provisioning
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(name.trim()) },
                enabled = name.isNotBlank() && !provisioning
            ) {
                if (provisioning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !provisioning) { Text("Cancel") }
        }
    )
}

@Composable
private fun DuplicateSheetDialog(
    tabs: List<SheetTab>,
    provisioning: Boolean,
    onDismiss: () -> Unit,
    onDuplicate: (Int, String) -> Unit
) {
    var selectedTab by remember { mutableStateOf<SheetTab?>(tabs.firstOrNull()) }
    var newName by remember { mutableStateOf("") }
    selectedTab = selectedTab.takeIf { it in tabs } ?: tabs.firstOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Duplicate Sheet") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("Select sheet to duplicate:", style = MaterialTheme.typography.labelMedium)
                tabs.forEach { tab ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedTab = tab },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = selectedTab == tab,
                            onClick = { selectedTab = tab }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(tab.title)
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New sheet name") },
                    placeholder = { Text((selectedTab?.title ?: "") + " (copy)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !provisioning
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val tab = selectedTab ?: return@TextButton
                    onDuplicate(tab.sheetId, newName.ifBlank { "${tab.title} (copy)" })
                },
                enabled = selectedTab != null && !provisioning
            ) {
                if (provisioning) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Duplicate")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !provisioning) { Text("Cancel") }
        }
    )
}

@Composable
private fun ManageColumnsDialog(
    headers: List<String>,
    onDismiss: () -> Unit,
    onRename: (Int, String) -> Unit,
    onDelete: (Int) -> Unit
) {
    var renamingColumn by remember { mutableStateOf<Int?>(null) }
    var renameValue by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manage Columns") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                headers.forEachIndexed { colIndex, header ->
                    if (renamingColumn == colIndex) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = renameValue,
                                onValueChange = { renameValue = it },
                                label = { Text("New name") },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(8.dp))
                            TextButton(
                                onClick = {
                                    if (renameValue.isNotBlank()) {
                                        onRename(colIndex, renameValue.trim())
                                        renamingColumn = null
                                    }
                                }
                            ) { Text("OK") }
                            TextButton(onClick = { renamingColumn = null }) { Text("Cancel") }
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = header.ifEmpty { "(Empty)" },
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    renamingColumn = colIndex
                                    renameValue = header
                                }
                            ) {
                                Icon(Icons.Default.Edit, contentDescription = "Rename")
                            }
                            IconButton(
                                onClick = { onDelete(colIndex) },
                                enabled = headers.size > 1
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun AddColumnDialog(
    adding: Boolean,
    onDismiss: () -> Unit,
    onAdd: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Column") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Column name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !adding
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onAdd(name.trim()) },
                enabled = name.isNotBlank() && !adding
            ) {
                if (adding) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !adding) { Text("Cancel") }
        }
    )
}

@Composable
private fun SheetTable(
    data: SheetData,
    onRowClick: (Int) -> Unit,
    onRowLongPress: (Int) -> Unit = {},
    onRowDeleteClick: (Int) -> Unit = {},
    onHeaderLongPress: (Int) -> Unit = {},
    deletingRow: Int? = null
) {
    val separatorIndices = data.separatorRowIndices
    val horizontalScroll = rememberScrollState()
    val columnCount = remember(data) {
        maxOf(data.headers.size, data.rows.maxOfOrNull { it.size } ?: 0)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().horizontalScroll(horizontalScroll),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .background(MaterialTheme.colorScheme.primaryContainer)
            ) {
                for (col in 0 until columnCount) {
                    val header = data.headers.getOrElse(col) { "" }
                    Box(
                        modifier = Modifier
                            .width(CELL_MIN_WIDTH)
                            .fillMaxHeight()
                            .padding(CELL_PADDING)
                            .combinedClickable(
                                onClick = {},
                                onLongClick = { onHeaderLongPress(col) }
                            ),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = header,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    if (col < columnCount - 1) {
                        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        if (data.rows.isEmpty()) {
            item(key = "empty") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.DocumentScanner,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                        )
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "No rows yet",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Scan a receipt or tap + to add a row",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        itemsIndexed(
            data.rows,
            key = { index, _ -> "row_$index" }
        ) { rowIndex, row ->
            val isSeparator = rowIndex in separatorIndices
            val effectiveRowIndex = separatorIndices.count { it < rowIndex }.let { sepCount -> rowIndex - sepCount }
            val bgColor = when {
                isSeparator -> MaterialTheme.colorScheme.surfaceContainerHighest
                effectiveRowIndex % 2 == 0 -> MaterialTheme.colorScheme.surface
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            }
            val isDeleting = deletingRow == rowIndex

            Row(
                modifier = Modifier
                    .height(IntrinsicSize.Min)
                    .background(bgColor)
                    .combinedClickable(
                        enabled = !isSeparator,
                        onClick = { if (!isDeleting && !isSeparator) onRowClick(rowIndex) },
                        onLongClick = { if (!isSeparator) onRowLongPress(rowIndex) }
                    )
            ) {
                for (col in 0 until columnCount) {
                    val cell = row.getOrElse(col) { "" }?.toString() ?: ""
                    Box(
                        modifier = Modifier.width(CELL_MIN_WIDTH).fillMaxHeight().padding(CELL_PADDING),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = cell, style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3, overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (col < columnCount - 1) {
                        VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
                if (!isSeparator) {
                    IconButton(
                        onClick = { onRowDeleteClick(rowIndex) },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = "Delete row",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun LoadingContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text("Loading sheet data…", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SetupSheetContent(
    initializing: Boolean,
    onInitialize: (String) -> Unit
) {
    var headerLine by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxSize().padding(24.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                Icons.Default.TableChart,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Setup Sheet",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "This sheet has no headers. Enter comma-separated column headers below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = headerLine,
                onValueChange = { headerLine = it },
                label = { Text("Headers (e.g. Task, Due Date, Status)") },
                placeholder = { Text("Task, Due Date, Status") },
                singleLine = false,
                modifier = Modifier.fillMaxWidth(),
                enabled = !initializing,
                minLines = 2
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = { onInitialize(headerLine) },
                enabled = headerLine.isNotBlank() && !initializing
            ) {
                if (initializing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(Modifier.width(12.dp))
                }
                Text(if (initializing) "Initializing…" else "Initialize Sheet")
            }
        }
    }
}

@Composable
private fun EmptyContent() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Icon(
                Icons.Default.DocumentScanner,
                contentDescription = null,
                modifier = Modifier.size(96.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
            Spacer(Modifier.height(24.dp))
            Text(
                "Start by Scanning a Receipt",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Use the scan icon above to add your first row, or tap + to add manually",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Failed to load data", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Retry") }
        }
    }
}
