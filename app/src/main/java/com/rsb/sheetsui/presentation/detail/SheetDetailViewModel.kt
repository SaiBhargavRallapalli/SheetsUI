package com.rsb.sheetsui.presentation.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.domain.model.SheetData
import com.rsb.sheetsui.domain.model.SheetTab
import com.rsb.sheetsui.domain.repository.CreatedForm
import com.rsb.sheetsui.domain.repository.ExportFormat
import com.rsb.sheetsui.domain.repository.ExportRepository
import com.rsb.sheetsui.domain.repository.FormRepository
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SheetDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpreadsheetRepository,
    private val columnOverrideDao: com.rsb.sheetsui.data.local.dao.ColumnOverrideDao,
    private val exportRepository: ExportRepository,
    private val formRepository: FormRepository,
    private val sheetSettingsPrefs: com.rsb.sheetsui.data.prefs.SheetSettingsPreferences,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : ViewModel() {

    val spreadsheetId: String = checkNotNull(savedStateHandle["spreadsheetId"])
    val spreadsheetName: String = savedStateHandle["name"] ?: "Spreadsheet"

    private val _sheetTabs = MutableStateFlow<List<SheetTab>>(emptyList())
    val sheetTabs: StateFlow<List<SheetTab>> = _sheetTabs.asStateFlow()

    private val _selectedSheetIndex = MutableStateFlow(0)
    val selectedSheetIndex: StateFlow<Int> = _selectedSheetIndex.asStateFlow()

    /** Currently selected sheet title for API calls. Empty string = first sheet. */
    private val _selectedSheetName = MutableStateFlow("")
    val selectedSheetName: StateFlow<String> = _selectedSheetName.asStateFlow()

    private val _state = MutableStateFlow<DetailUiState>(DetailUiState.Loading)
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private val _addingColumn = MutableStateFlow(false)
    val addingColumn: StateFlow<Boolean> = _addingColumn.asStateFlow()

    private val _deletingRow = MutableStateFlow<Int?>(null)
    val deletingRow: StateFlow<Int?> = _deletingRow.asStateFlow()

    private val _managingColumns = MutableStateFlow(false)
    val managingColumns: StateFlow<Boolean> = _managingColumns.asStateFlow()

    private val _initializing = MutableStateFlow(false)
    val initializing: StateFlow<Boolean> = _initializing.asStateFlow()

    private val _exporting = MutableStateFlow(false)
    val exporting: StateFlow<Boolean> = _exporting.asStateFlow()

    private val _creatingForm = MutableStateFlow(false)
    val creatingForm: StateFlow<Boolean> = _creatingForm.asStateFlow()

    private val _provisioning = MutableStateFlow(false)
    val provisioning: StateFlow<Boolean> = _provisioning.asStateFlow()

    private val _events = MutableSharedFlow<DetailEvent>()
    val events: SharedFlow<DetailEvent> = _events.asSharedFlow()

    private val _sheetSettingsFinancialCol = MutableStateFlow<Int?>(null)
    val sheetSettingsFinancialCol: StateFlow<Int?> = _sheetSettingsFinancialCol.asStateFlow()
    private val _sheetSettingsCurrency = MutableStateFlow<String?>(null)
    val sheetSettingsCurrency: StateFlow<String?> = _sheetSettingsCurrency.asStateFlow()

    init {
        loadSheetTabs()
        viewModelScope.launch {
            _selectedSheetName.collect { sheetName ->
                kotlinx.coroutines.coroutineScope {
                    launch {
                        sheetSettingsPrefs.getFinancialColumnIndex(spreadsheetId, sheetName).collect {
                            _sheetSettingsFinancialCol.value = it
                        }
                    }
                    launch {
                        sheetSettingsPrefs.getCurrencySymbol(spreadsheetId, sheetName).collect {
                            _sheetSettingsCurrency.value = it
                        }
                    }
                }
            }
        }
    }

    fun loadSheetTabs() {
        viewModelScope.launch {
            repository.getSheetTabs(spreadsheetId)
                .onSuccess { tabs ->
                    _sheetTabs.value = tabs
                    if (tabs.isEmpty()) {
                        _state.value = DetailUiState.Error("No sheets in spreadsheet")
                        return@launch
                    }
                    if (_selectedSheetIndex.value >= tabs.size) {
                        _selectedSheetIndex.value = 0
                    }
                    val idx = _selectedSheetIndex.value.coerceIn(0, tabs.size - 1)
                    _selectedSheetName.value = tabs[idx].title
                    loadSheet()
                }
                .onFailure { error ->
                    _state.value = DetailUiState.Error(
                        error.localizedMessage ?: "Failed to load sheets"
                    )
                }
        }
    }

    fun selectSheet(index: Int) {
        if (index == _selectedSheetIndex.value) return
        val tabs = _sheetTabs.value
        if (index !in tabs.indices) return
        _selectedSheetIndex.value = index
        _selectedSheetName.value = tabs[index].title
        loadSheet()
    }

    fun loadSheet() {
        _state.value = DetailUiState.Loading
        val sheetName = _selectedSheetName.value
        viewModelScope.launch {
            repository.getSheetData(spreadsheetId, sheetName).collect { result ->
                result
                    .onSuccess { data ->
                        val hasDataBelowRow1 = data.rows.any { row ->
                            row.any { cell -> (cell?.toString()?.trim() ?: "").isNotEmpty() }
                        }
                        val hasExplicitHeaders = data.headers.any { it.isNotBlank() }

                        _state.value = when {
                            hasDataBelowRow1 -> DetailUiState.Success(data)
                            hasExplicitHeaders -> DetailUiState.Success(data)
                            data.headers.isEmpty() && data.rows.isEmpty() -> DetailUiState.NoHeadersFound
                            else -> DetailUiState.Success(data)
                        }
                    }
                    .onFailure { error ->
                        _state.value = DetailUiState.Error(
                            error.localizedMessage ?: "Failed to load sheet data"
                        )
                    }
            }
        }
    }

    fun initializeHeaders(headerLine: String) {
        if (_initializing.value) return
        val headers = headerLine
            .split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (headers.isEmpty()) {
            viewModelScope.launch {
                _events.emit(DetailEvent.Error("Enter at least one header"))
            }
            return
        }

        _initializing.value = true
        viewModelScope.launch {
            val headerRowIndex = (_state.value as? DetailUiState.Success)?.data?.headerRowIndex ?: 0
            repository.initializeHeaders(spreadsheetId, _selectedSheetName.value, headers, headerRowIndex)
                .onSuccess {
                    _events.emit(DetailEvent.HeadersInitialized)
                    loadSheet()
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to initialize headers"
                    ))
                }
            _initializing.value = false
        }
    }

    fun addColumn(columnName: String) {
        val current = _state.value as? DetailUiState.Success ?: return
        if (_addingColumn.value) return
        _addingColumn.value = true

        viewModelScope.launch {
            repository.addColumn(
                spreadsheetId = spreadsheetId,
                sheetName = _selectedSheetName.value,
                columnName = columnName,
                currentColumnCount = current.data.headers.size,
                headerRowIndex = current.data.headerRowIndex
            )
                .onSuccess {
                    _events.emit(DetailEvent.ColumnAdded(columnName))
                    loadSheet()
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to add column"
                    ))
                }
            _addingColumn.value = false
        }
    }

    fun deleteRow(rowIndex: Int) {
        if (_deletingRow.value != null) return
        val current = _state.value as? DetailUiState.Success ?: return
        _deletingRow.value = rowIndex

        viewModelScope.launch {
            repository.deleteRow(spreadsheetId, _selectedSheetName.value, rowIndex, current.data.headerRowIndex)
                .onSuccess {
                    _events.emit(DetailEvent.RowDeleted)
                    loadSheet()
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to delete row"
                    ))
                }
            _deletingRow.value = null
        }
    }

    fun renameColumn(columnIndex: Int, newName: String) {
        val current = _state.value as? DetailUiState.Success ?: return
        if (newName.isBlank()) return

        viewModelScope.launch {
            repository.renameColumn(spreadsheetId, _selectedSheetName.value, columnIndex, newName, current.data.headerRowIndex)
                .onSuccess {
                    _events.emit(DetailEvent.ColumnRenamed(newName))
                    loadSheet()
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to rename column"
                    ))
                }
        }
    }

    fun setColumnTypeOverride(columnIndex: Int, fieldType: com.rsb.sheetsui.domain.model.FieldType) {
        viewModelScope.launch {
            columnOverrideDao.insert(
                com.rsb.sheetsui.data.local.entity.ColumnOverrideEntity(
                    spreadsheetId = spreadsheetId,
                    columnIndex = columnIndex,
                    fieldType = fieldType.name
                )
            )
            _events.emit(DetailEvent.ColumnTypeChanged(fieldType))
            loadSheet()
        }
    }

    fun exportSpreadsheet(format: ExportFormat) {
        if (_exporting.value) return

        _exporting.value = true
        viewModelScope.launch {
            exportRepository.exportAndSave(
                spreadsheetId = spreadsheetId,
                spreadsheetName = spreadsheetName,
                format = format
            )
                .onSuccess { uri ->
                    _events.emit(DetailEvent.ExportSaved(uri))
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to export"
                    ))
                }
            _exporting.value = false
        }
    }

    fun deleteColumn(columnIndex: Int) {
        val current = _state.value as? DetailUiState.Success ?: return
        if (current.data.headers.size <= 1) {
            viewModelScope.launch {
                _events.emit(DetailEvent.Error("Cannot delete the last column"))
            }
            return
        }

        viewModelScope.launch {
            repository.deleteColumn(spreadsheetId, _selectedSheetName.value, columnIndex)
                .onSuccess {
                    _events.emit(DetailEvent.ColumnDeleted)
                    loadSheet()
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to delete column"
                    ))
                }
        }
    }

    fun createFormFromSpreadsheet() {
        val current = _state.value as? DetailUiState.Success ?: return
        if (_creatingForm.value) return

        val data = current.data
        if (data.headers.isEmpty() || data.headers.all { it.isBlank() }) {
            viewModelScope.launch {
                _events.emit(DetailEvent.Error("Sheet has no headers"))
            }
            return
        }

        _creatingForm.value = true
        viewModelScope.launch {
            formRepository.createFormFromSpreadsheet(
                spreadsheetId = spreadsheetId,
                spreadsheetName = spreadsheetName,
                sheetData = data
            )
                .onSuccess { created ->
                    _events.emit(DetailEvent.FormCreated(created))
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to create form"
                    ))
                }
            _creatingForm.value = false
        }
    }

    fun addSheet(title: String) {
        if (title.isBlank() || _provisioning.value) return
        _provisioning.value = true
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.addSheet(spreadsheetId, title.trim())
            }
                .onSuccess { newTab ->
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.getSheetTabs(spreadsheetId)
                    }
                    if (r.isSuccess) {
                        val tabs = r.getOrThrow()
                        _sheetTabs.value = tabs
                        val idx = tabs.indexOfFirst { it.sheetId == newTab.sheetId }.takeIf { it >= 0 } ?: (tabs.size - 1)
                        _selectedSheetIndex.value = idx
                        _selectedSheetName.value = tabs.getOrNull(idx)?.title ?: newTab.title
                    } else {
                        _sheetTabs.update { it + newTab }
                        _selectedSheetIndex.value = _sheetTabs.value.size - 1
                        _selectedSheetName.value = newTab.title
                    }
                    loadSheet()
                    _events.emit(DetailEvent.SheetAdded(newTab.title))
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to create sheet"
                    ))
                }
            _provisioning.value = false
        }
    }

    fun duplicateSheet(sourceSheetId: Int, newSheetName: String) {
        if (_provisioning.value) return
        _provisioning.value = true
        viewModelScope.launch {
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                repository.duplicateSheet(spreadsheetId, sourceSheetId, newSheetName.trim())
            }
                .onSuccess { newTab ->
                    val r = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        repository.getSheetTabs(spreadsheetId)
                    }
                    if (r.isSuccess) {
                        val tabs = r.getOrThrow()
                        _sheetTabs.value = tabs
                        val idx = tabs.indexOfFirst { it.sheetId == newTab.sheetId }.takeIf { it >= 0 } ?: (tabs.size - 1)
                        _selectedSheetIndex.value = idx
                        _selectedSheetName.value = tabs.getOrNull(idx)?.title ?: newTab.title
                    } else {
                        _sheetTabs.update { (it + newTab).sortedBy { t -> t.index } }
                        _selectedSheetIndex.value = _sheetTabs.value.indexOfFirst { it.sheetId == newTab.sheetId }.takeIf { it >= 0 } ?: (_sheetTabs.value.size - 1)
                        _selectedSheetName.value = newTab.title
                    }
                    loadSheet()
                    _events.emit(DetailEvent.SheetDuplicated(newTab.title))
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to duplicate sheet"
                    ))
                }
            _provisioning.value = false
        }
    }

    fun renameSheet(sheetId: Int, newName: String) {
        if (_provisioning.value || newName.isBlank()) return
        _provisioning.value = true
        viewModelScope.launch {
            repository.renameSheet(spreadsheetId, sheetId, newName.trim())
                .onSuccess {
                    loadSheetTabs()
                    _events.emit(DetailEvent.SheetRenamed(newName.trim()))
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to rename sheet"
                    ))
                }
            _provisioning.value = false
        }
    }

    fun updateCell(rowIndex: Int, columnIndex: Int, newValue: String) {
        val current = _state.value as? DetailUiState.Success ?: return
        val row = current.data.rows.getOrNull(rowIndex) ?: return
        val updatedRow = row.mapIndexed { i, v ->
            if (i == columnIndex) newValue else v
        }
        viewModelScope.launch {
            val email = firebaseAuth.currentUser?.email ?: ""
            val ts = java.time.Instant.now().toString().take(19)
            val audit = if (email.isNotBlank()) email to ts else null
            repository.updateRow(
                spreadsheetId,
                _selectedSheetName.value,
                rowIndex,
                updatedRow,
                current.data.mergeRanges,
                current.data.headerRowIndex,
                audit
            )
                .onSuccess { loadSheet() }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to update cell"
                    ))
                }
        }
    }

    fun appendReceiptRow(receipt: com.rsb.sheetsui.domain.util.ReceiptData, headers: List<String>) {
        val row = headers.map { header ->
            val h = header.lowercase()
            when {
                h.contains("merchant") || h.contains("store") || h.contains("name") && !h.contains("product") -> receipt.merchantName
                h.contains("amount") || h.contains("total") || h.contains("price") || h.contains("cost") ->
                    if (receipt.totalAmount.isNotBlank()) receipt.totalAmount else ""
                h.contains("date") || h.contains("time") -> receipt.date
                h.contains("type") && (h.contains("receipt") || h.contains("document") || h.contains("bill")) -> receipt.receiptType
                else -> ""
            }
        }
        viewModelScope.launch {
            val email = firebaseAuth.currentUser?.email ?: ""
            val ts = java.time.Instant.now().toString().take(19)
            val audit = if (email.isNotBlank()) email to ts else null
            repository.appendRow(spreadsheetId, _selectedSheetName.value, row, audit)
                .onSuccess {
                    loadSheet()
                    _events.emit(DetailEvent.RowAppended)
                }
                .onFailure { error ->
                    _events.emit(DetailEvent.Error(
                        error.localizedMessage ?: "Failed to add receipt row"
                    ))
                }
        }
    }

    fun saveSheetSettings(financialColumnIndex: Int?, currencySymbol: String?) {
        viewModelScope.launch {
            val sheetName = _selectedSheetName.value
            sheetSettingsPrefs.setFinancialColumnIndex(spreadsheetId, sheetName, financialColumnIndex)
            sheetSettingsPrefs.setCurrencySymbol(spreadsheetId, sheetName, currencySymbol)
            _sheetSettingsFinancialCol.value = financialColumnIndex
            _sheetSettingsCurrency.value = currencySymbol
        }
    }

    /** Returns row index if barcode matches ID or SKU column, else null. */
    fun findRowByBarcode(barcode: String, data: com.rsb.sheetsui.domain.model.SheetData): Int? {
        val idCol = data.headers.indexOfFirst { h ->
            h.contains("id", ignoreCase = true) || h.contains("sku", ignoreCase = true)
        }
        if (idCol < 0) return null
        return data.rows.indexOfFirst { row ->
            (row.getOrNull(idCol)?.toString()?.trim() ?: "") == barcode.trim()
        }.takeIf { it >= 0 }
    }
}

sealed interface DetailUiState {
    data object Loading : DetailUiState
    data object NoHeadersFound : DetailUiState
    data object Empty : DetailUiState
    data class Success(val data: SheetData) : DetailUiState
    data class Error(val message: String) : DetailUiState
}

sealed interface DetailEvent {
    data class ColumnAdded(val name: String) : DetailEvent
    data class ColumnRenamed(val name: String) : DetailEvent
    data object ColumnDeleted : DetailEvent
    data object RowDeleted : DetailEvent
    data object HeadersInitialized : DetailEvent
    data class ColumnTypeChanged(val fieldType: com.rsb.sheetsui.domain.model.FieldType) : DetailEvent
    data class ExportSaved(val uri: android.net.Uri) : DetailEvent
    data class FormCreated(val form: CreatedForm) : DetailEvent
    data class SheetAdded(val title: String) : DetailEvent
    data class SheetDuplicated(val title: String) : DetailEvent
    data class SheetRenamed(val title: String) : DetailEvent
    data object RowAppended : DetailEvent
    data class Error(val message: String) : DetailEvent
}
