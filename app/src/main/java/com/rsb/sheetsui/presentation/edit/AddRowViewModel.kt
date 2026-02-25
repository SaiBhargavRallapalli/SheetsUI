package com.rsb.sheetsui.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.domain.inference.SheetInferenceEngine
import com.rsb.sheetsui.domain.model.FieldType
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
import com.rsb.sheetsui.domain.util.CurrencyLocale
import com.rsb.sheetsui.domain.util.ReceiptData
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AddRowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpreadsheetRepository,
    private val columnOverrideDao: com.rsb.sheetsui.data.local.dao.ColumnOverrideDao,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : ViewModel() {

    val spreadsheetId: String = checkNotNull(savedStateHandle["spreadsheetId"])
    val spreadsheetName: String = savedStateHandle["name"] ?: "Spreadsheet"
    val sheetName: String = savedStateHandle["sheetName"] ?: ""
    private val cloneRowIndex: Int = savedStateHandle["cloneRow"] ?: -1

    private val _state = MutableStateFlow<AddRowUiState>(AddRowUiState.Loading)
    val state: StateFlow<AddRowUiState> = _state.asStateFlow()

    init {
        loadSchema()
    }

    private fun loadSchema() {
        _state.value = AddRowUiState.Loading
        viewModelScope.launch {
            val overrides = columnOverrideDao.getOverridesForSpreadsheet(spreadsheetId)
                .mapNotNull { runCatching { it.columnIndex to FieldType.valueOf(it.fieldType) }.getOrNull() }
                .toMap()
            repository.getSheetData(spreadsheetId, sheetName).collect { result ->
                result
                    .onSuccess { data ->
                        if (data.headers.isEmpty()) {
                            _state.value = AddRowUiState.Error("Sheet has no headers")
                            return@onSuccess
                        }

                        val sampleRow = data.rows.firstOrNull() ?: emptyList()
                        val formulaSample = data.formulaRows.firstOrNull()
                        val fieldTypes = SheetInferenceEngine.infer(data.headers, sampleRow, formulaSample, overrides)
                        val cloneFrom = if (cloneRowIndex in data.rows.indices) data.rows[cloneRowIndex] else null

                        val fields = data.headers.mapIndexed { col, header ->
                            val initialValue = cloneFrom?.getOrElse(col) { "" }?.toString() ?: ""
                            EditableField(
                                header = header,
                                type = fieldTypes.getOrElse(col) { FieldType.TEXT },
                                value = initialValue,
                                columnIndex = col,
                                isMerged = false
                            )
                        }

                        _state.value = AddRowUiState.Editing(fields)
                    }
                    .onFailure { error ->
                        _state.value = AddRowUiState.Error(
                            error.localizedMessage ?: "Failed to load sheet schema"
                        )
                    }
            }
        }
    }

    fun updateField(columnIndex: Int, newValue: String) {
        val current = _state.value as? AddRowUiState.Editing ?: return
        val updated = current.fields.map { field ->
            if (field.columnIndex == columnIndex) field.copy(value = newValue)
            else field
        }
        _state.value = AddRowUiState.Editing(updated)
    }

    fun save() {
        val current = _state.value as? AddRowUiState.Editing ?: return
        _state.value = AddRowUiState.Saving(current.fields)

        viewModelScope.launch {
            val rowValues = current.fields.map { it.value as Any? }
            val email = firebaseAuth.currentUser?.email ?: ""
            val ts = java.time.Instant.now().toString().take(19)
            val audit = if (email.isNotBlank()) email to ts else null
            repository.appendRow(spreadsheetId, sheetName, rowValues, audit)
                .onSuccess {
                    _state.value = AddRowUiState.Success
                }
                .onFailure { error ->
                    _state.value = AddRowUiState.Error(
                        error.localizedMessage ?: "Failed to add row"
                    )
                }
        }
    }

    fun retryLoad() = loadSchema()

    fun applyReceiptData(data: ReceiptData) {
        val current = _state.value as? AddRowUiState.Editing ?: return
        val updated = current.fields.map { field ->
            val header = field.header.lowercase()
            val newValue = when {
                header.contains("merchant") || header.contains("store") || header.contains("name") && !header.contains("product") -> data.merchantName.ifBlank { field.value }
                header.contains("amount") || header.contains("total") || header.contains("price") || header.contains("cost") -> if (data.totalAmount.isNotBlank()) "${CurrencyLocale.defaultSymbol}$data.totalAmount" else field.value
                header.contains("date") || header.contains("time") -> data.date.ifBlank { field.value }
                header.contains("type") && (header.contains("receipt") || header.contains("document") || header.contains("bill")) -> data.receiptType.ifBlank { field.value }
                else -> field.value
            }
            if (newValue != field.value) field.copy(value = newValue) else field
        }
        _state.value = AddRowUiState.Editing(updated)
    }

    fun applyBarcodeId(barcodeId: String) {
        val current = _state.value as? AddRowUiState.Editing ?: return
        val productIdField = current.fields.firstOrNull { f ->
            f.header.lowercase().contains("product") && f.header.lowercase().contains("id")
        } ?: current.fields.firstOrNull { f ->
            f.header.lowercase().contains("product") || f.header.lowercase().contains("barcode")
        }
        if (productIdField != null) {
            val updated = current.fields.map { f ->
                if (f.columnIndex == productIdField.columnIndex) f.copy(value = barcodeId)
                else f
            }
            _state.value = AddRowUiState.Editing(updated)
        }
    }
}

sealed interface AddRowUiState {
    data object Loading : AddRowUiState
    data class Editing(val fields: List<EditableField>) : AddRowUiState
    data class Saving(val fields: List<EditableField>) : AddRowUiState
    data object Success : AddRowUiState
    data class Error(val message: String) : AddRowUiState
}
