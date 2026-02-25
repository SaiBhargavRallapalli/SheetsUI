package com.rsb.sheetsui.presentation.home

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.rsb.sheetsui.domain.model.Form
import com.rsb.sheetsui.domain.model.Spreadsheet
import com.rsb.sheetsui.domain.repository.CreatedForm
import com.rsb.sheetsui.presentation.form.FormSharingBottomSheet
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    displayName: String?,
    onSignOut: () -> Unit,
    onSheetClick: (Spreadsheet) -> Unit = {},
    onSettings: () -> Unit = {},
    viewModel: HomeViewModel = hiltViewModel()
) {
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val formsState by viewModel.formsState.collectAsStateWithLifecycle()
    val creating by viewModel.creating.collectAsStateWithLifecycle()
    val creatingForm by viewModel.creatingForm.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val snackbarHostState = remember { SnackbarHostState() }
    var showCreateChoice by rememberSaveable { mutableStateOf(false) }
    var showSpreadsheetDialog by rememberSaveable { mutableStateOf(false) }
    var showFormDialog by rememberSaveable { mutableStateOf(false) }
    var formToShare by remember { mutableStateOf<CreatedForm?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.SpreadsheetCreated ->
                    snackbarHostState.showSnackbar("\"${event.spreadsheet.name}\" created")
                is HomeEvent.FormCreated ->
                    formToShare = event.form
                is HomeEvent.SpreadsheetDeleted ->
                    snackbarHostState.showSnackbar("Spreadsheet deleted")
                is HomeEvent.FormDeleted ->
                    snackbarHostState.showSnackbar("Form deleted")
                is HomeEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    val deletingId by viewModel.deletingId.collectAsStateWithLifecycle()
    val deletingFormId by viewModel.deletingFormId.collectAsStateWithLifecycle()
    var sheetToDelete by remember { mutableStateOf<Spreadsheet?>(null) }
    var formToDelete by remember { mutableStateOf<Form?>(null) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text(
                            when (selectedTab) {
                                HomeTab.Sheets -> "My Spreadsheets"
                                HomeTab.Forms -> "My Forms"
                            }
                        )
                        if (displayName != null) {
                            Text(
                                text = displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.padding(end = 8.dp)) {
                        SegmentedButton(
                            selected = selectedTab == HomeTab.Sheets,
                            onClick = { viewModel.selectTab(HomeTab.Sheets) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                            label = { Text("Sheets") },
                            icon = { Icon(Icons.Default.Description, null, Modifier.size(18.dp)) }
                        )
                        SegmentedButton(
                            selected = selectedTab == HomeTab.Forms,
                            onClick = { viewModel.selectTab(HomeTab.Forms) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                            label = { Text("Forms") },
                            icon = { Icon(Icons.Default.Assessment, null, Modifier.size(18.dp)) }
                        )
                    }
                    if (selectedTab == HomeTab.Sheets && (state is HomeUiState.Success || state is HomeUiState.Empty)) {
                        IconButton(onClick = { viewModel.loadSpreadsheets() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    if (selectedTab == HomeTab.Forms && (formsState is HomeFormsUiState.Success || formsState is HomeFormsUiState.Empty)) {
                        IconButton(onClick = { viewModel.loadForms() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                    IconButton(onClick = onSignOut) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Sign out")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (selectedTab == HomeTab.Sheets && state is HomeUiState.Success && (state as HomeUiState.Success).spreadsheets.isNotEmpty()) {
                    FloatingActionButton(
                        onClick = { viewModel.startVoiceInput() },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Icon(Icons.Default.Mic, contentDescription = "Add via voice")
                    }
                }
                FloatingActionButton(onClick = { showCreateChoice = true }) {
                    Icon(Icons.Default.Add, contentDescription = "Create")
                }
            }
        }
    ) { innerPadding ->
        when (selectedTab) {
            HomeTab.Sheets -> {
                AnimatedContent(
                    targetState = state,
                    label = "home_sheets",
                    modifier = Modifier.padding(innerPadding)
                ) { currentState ->
                    when (currentState) {
                        is HomeUiState.Loading -> LoadingContent("Loading spreadsheets…")
                        is HomeUiState.Empty -> EmptyContent(
                            icon = Icons.Default.Description,
                            title = "No spreadsheets found",
                            subtitle = "Tap + to create one, or pull to refresh",
                            onRefresh = { viewModel.loadSpreadsheets() }
                        )
                        is HomeUiState.Success -> SpreadsheetList(
                            spreadsheets = currentState.spreadsheets,
                            isRefreshing = false,
                            onRefresh = { viewModel.loadSpreadsheets() },
                            onClick = onSheetClick,
                            onDeleteClick = { sheetToDelete = it }
                        )
                        is HomeUiState.Error -> ErrorContent(
                            message = currentState.message,
                            onRetry = { viewModel.loadSpreadsheets() }
                        )
                    }
                }
            }
            HomeTab.Forms -> {
                AnimatedContent(
                    targetState = formsState,
                    label = "home_forms",
                    modifier = Modifier.padding(innerPadding)
                ) { currentState ->
                    when (currentState) {
                        is HomeFormsUiState.Loading -> LoadingContent("Loading forms…")
                        is HomeFormsUiState.Empty -> EmptyContent(
                            icon = Icons.Default.Assessment,
                            title = "No forms found",
                            subtitle = "Tap + to create one, or pull to refresh",
                            onRefresh = { viewModel.loadForms() }
                        )
                        is HomeFormsUiState.Success -> FormList(
                            forms = currentState.forms,
                            isRefreshing = false,
                            onRefresh = { viewModel.loadForms() },
                            onFormClick = { form ->
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(form.editUrl)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            },
                            onDeleteClick = { formToDelete = it }
                        )
                        is HomeFormsUiState.Error -> ErrorContent(
                            message = currentState.message,
                            onRetry = { viewModel.loadForms() }
                        )
                    }
                }
            }
        }
    }

    sheetToDelete?.let { sheet ->
        AlertDialog(
            onDismissRequest = { sheetToDelete = null },
            title = { Text("Delete spreadsheet") },
            text = { Text("Permanently delete \"${sheet.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteSpreadsheet(sheet.id)
                        sheetToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { sheetToDelete = null }) { Text("Cancel") }
            }
        )
    }

    formToDelete?.let { form ->
        AlertDialog(
            onDismissRequest = { formToDelete = null },
            title = { Text("Delete form") },
            text = { Text("Permanently delete \"${form.name}\"? This cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteForm(form.id)
                        formToDelete = null
                    }
                ) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { formToDelete = null }) { Text("Cancel") }
            }
        )
    }

    val showVoice by viewModel.showVoiceDialog.collectAsStateWithLifecycle()
    if (showVoice && state is HomeUiState.Success) {
        VoiceInputDialog(
            spreadsheets = (state as HomeUiState.Success).spreadsheets,
            onDismiss = { viewModel.dismissVoiceDialog() },
            onSubmit = { spreadsheetId, transcript ->
                viewModel.appendVoiceRow(spreadsheetId, transcript)
                viewModel.dismissVoiceDialog()
            }
        )
    }

    if (showCreateChoice) {
        CreateChoiceDialog(
            onDismiss = { showCreateChoice = false },
            onCreateSpreadsheet = {
                showCreateChoice = false
                showSpreadsheetDialog = true
            },
            onCreateForm = {
                showCreateChoice = false
                showFormDialog = true
            }
        )
    }

    if (showSpreadsheetDialog) {
        CreateSpreadsheetDialog(
            creating = creating,
            onDismiss = { showSpreadsheetDialog = false },
            onCreate = { title ->
                viewModel.createSpreadsheet(title)
                showSpreadsheetDialog = false
            }
        )
    }

    formToShare?.let { form ->
        FormSharingBottomSheet(
            form = form,
            onDismiss = { formToShare = null }
        )
    }

    if (showFormDialog) {
        CreateFormDialog(
            creating = creatingForm,
            onDismiss = { showFormDialog = false },
            onCreate = { title ->
                viewModel.createForm(title)
                showFormDialog = false
            }
        )
    }
}

@Composable
private fun CreateChoiceDialog(
    onDismiss: () -> Unit,
    onCreateSpreadsheet: () -> Unit,
    onCreateForm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onCreateSpreadsheet,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Description, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Spreadsheet")
                }
                Button(
                    onClick = onCreateForm,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.TableChart, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Create Form")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateFormDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Form") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Form title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim().ifBlank { "Untitled form" }) },
                enabled = !creating
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Cancel") }
        }
    )
}

@Composable
private fun CreateSpreadsheetDialog(
    creating: Boolean,
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var title by rememberSaveable { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Spreadsheet") },
        text = {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Spreadsheet title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = !creating
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onCreate(title.trim()) },
                enabled = title.isNotBlank() && !creating
            ) {
                if (creating) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                } else {
                    Text("Create")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !creating) { Text("Cancel") }
        }
    )
}

@Composable
private fun LoadingContent(message: String = "Loading…") {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
            Spacer(Modifier.height(16.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptyContent(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onRefresh: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(16.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRefresh) { Text("Refresh") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SpreadsheetList(
    spreadsheets: List<Spreadsheet>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onClick: (Spreadsheet) -> Unit,
    onDeleteClick: (Spreadsheet) -> Unit = {}
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(spreadsheets, key = { it.id }) { sheet ->
                SpreadsheetCard(
                    sheet = sheet,
                    onClick = { onClick(sheet) },
                    onDeleteClick = { onDeleteClick(sheet) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FormList(
    forms: List<Form>,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onFormClick: (Form) -> Unit,
    onDeleteClick: (Form) -> Unit = {}
) {
    PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = onRefresh, modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 88.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(forms, key = { it.id }) { form ->
                FormCard(
                    form = form,
                    onClick = { onFormClick(form) },
                    onDeleteClick = { onDeleteClick(form) }
                )
            }
        }
    }
}

@Composable
private fun FormCard(
    form: Form,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Assessment,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = form.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                form.modifiedTime?.let { raw ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatModifiedTime(raw),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete form",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpreadsheetCard(
    sheet: Spreadsheet,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit = {}
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(26.dp)
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sheet.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface
                )
                sheet.modifiedTime?.let { raw ->
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = formatModifiedTime(raw),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
            IconButton(
                onClick = onDeleteClick,
                modifier = Modifier.size(40.dp)
            ) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "Delete spreadsheet",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ErrorContent(message: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(56.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
            )
            Spacer(Modifier.height(20.dp))
            Text(
                "Something went wrong",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(28.dp))
            Button(
                onClick = onRetry,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Retry")
            }
        }
    }
}

private fun formatModifiedTime(isoTimestamp: String): String = try {
    val instant = Instant.parse(isoTimestamp)
    DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM).withZone(ZoneId.systemDefault()).format(instant)
} catch (_: Exception) {
    isoTimestamp
}
