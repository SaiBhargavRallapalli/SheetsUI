package com.rsb.sheetsui.presentation.edit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.data.remote.util.ApiErrorMapper
import com.rsb.sheetsui.domain.inference.SheetInferenceEngine
import com.rsb.sheetsui.domain.model.FieldType
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class EditRowViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: SpreadsheetRepository,
    private val columnOverrideDao: com.rsb.sheetsui.data.local.dao.ColumnOverrideDao,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : ViewModel() {

    val spreadsheetId: String = checkNotNull(savedStateHandle["spreadsheetId"])
    val spreadsheetName: String = savedStateHandle["name"] ?: "Spreadsheet"
    val rowIndex: Int = checkNotNull(savedStateHandle["rowIndex"])
    val sheetName: String = savedStateHandle["sheetName"] ?: ""

    private val _state = MutableStateFlow<EditUiState>(EditUiState.Loading)
    val state: StateFlow<EditUiState> = _state.asStateFlow()

    init {
        loadRow()
    }

    private fun loadRow() {
        _state.value = EditUiState.Loading
        viewModelScope.launch {
            val overrides = columnOverrideDao.getOverridesForSpreadsheet(spreadsheetId)
                .mapNotNull { runCatching { it.columnIndex to FieldType.valueOf(it.fieldType) }.getOrNull() }
                .toMap()
            repository.getSheetData(spreadsheetId, sheetName).collect { result ->
                result
                    .onSuccess { data ->
                        if (rowIndex >= data.rows.size) {
                            _state.value = EditUiState.Error("Row $rowIndex not found")
                            return@onSuccess
                        }

                        val row = data.rows[rowIndex]
                        val sampleRow = data.rows.firstOrNull() ?: row
                        val formulaRow = data.formulaRows.getOrNull(rowIndex)
                        val fieldTypes = SheetInferenceEngine.infer(data.headers, sampleRow, formulaRow, overrides)

                        val fields = data.headers.mapIndexed { col, header ->
                            val dataRowIndex = rowIndex + data.headerRowIndex + 1
                            val isMerged = com.rsb.sheetsui.domain.util.MergedCellResolver.isMergedCell(
                                data.mergeRanges, dataRowIndex, col
                            )
                            val rawFormula = formulaRow?.getOrElse(col) { null }
                            val displayValue = row.getOrElse(col) { "" }?.toString() ?: ""
                            val isFormulaCol = fieldTypes.getOrElse(col) { FieldType.TEXT } == FieldType.FORMULA
                            EditableField(
                                header = header,
                                type = fieldTypes.getOrElse(col) { FieldType.TEXT },
                                value = if (isFormulaCol && rawFormula != null) rawFormula else displayValue,
                                columnIndex = col,
                                isMerged = isMerged,
                                rawFormula = rawFormula,
                                displayValue = if (isFormulaCol) displayValue else null,
                                validation = data.columnValidations[col]
                            )
                        }

                        _state.value = EditUiState.Editing(fields, data.mergeRanges, data.lastModifiedTime, data.headerRowIndex)
                    }
                    .onFailure { error ->
                        val apiError = ApiErrorMapper.fromThrowable(error)
                        _state.value = EditUiState.Error(apiError.userMessage)
                    }
            }
        }
    }

    fun updateField(columnIndex: Int, newValue: String) {
        val current = _state.value as? EditUiState.Editing ?: return
        val updated = current.fields.map { field ->
            if (field.columnIndex == columnIndex) field.copy(value = newValue)
            else field
        }
        _state.value = EditUiState.Editing(updated)
    }

    fun save() {
        val current = _state.value as? EditUiState.Editing ?: return
        _state.value = EditUiState.Saving(current.fields)

        viewModelScope.launch {
            val currentModified = repository.getFileModifiedTime(spreadsheetId).getOrNull()
            if (current.lastModifiedTime != null && currentModified != null &&
                current.lastModifiedTime != currentModified
            ) {
                _state.value = EditUiState.ConflictDetected(
                    "Sheet has been updated by someone else. Refresh before saving?",
                    current
                )
                return@launch
            }

            val rowValues = current.fields.map { it.value as Any? }
            val email = firebaseAuth.currentUser?.email ?: ""
            val ts = java.time.Instant.now().toString().take(19)
            val audit = if (email.isNotBlank()) email to ts else null
            repository.updateRow(spreadsheetId, sheetName, rowIndex, rowValues, current.mergeRanges, current.headerRowIndex, audit)
                .onSuccess {
                    _state.value = EditUiState.Success
                }
                .onFailure { error ->
                    val apiError = ApiErrorMapper.fromThrowable(error)
                    _state.value = EditUiState.Error(apiError.userMessage)
                }
        }
    }

    fun retryLoad() = loadRow()

    fun onConflictRefresh(editingState: EditUiState.Editing) {
        _state.value = EditUiState.Loading
        loadRow()
    }

    fun onConflictDismiss(editingState: EditUiState.Editing) {
        _state.value = editingState
    }

    private val _deleting = MutableStateFlow(false)
    val deleting = _deleting.asStateFlow()

    fun deleteRow(onSuccess: () -> Unit) {
        if (_deleting.value) return
        val current = _state.value as? EditUiState.Editing ?: return
        _deleting.value = true

        viewModelScope.launch {
            repository.deleteRow(spreadsheetId, sheetName, rowIndex, current.headerRowIndex)
                .onSuccess {
                    _deleting.value = false
                    onSuccess()
                }
                .onFailure { error ->
                    _deleting.value = false
                    val apiError = ApiErrorMapper.fromThrowable(error)
                    _state.value = EditUiState.Error(apiError.userMessage)
                }
        }
    }
}

data class EditableField(
    val header: String,
    val type: FieldType,
    val value: String,
    val columnIndex: Int,
    val isMerged: Boolean = false,
    val rawFormula: String? = null,
    /** Calculated result for formula cells (shown when not editing formula). */
    val displayValue: String? = null,
    /** Data validation from Sheets (dropdown options or checkbox). */
    val validation: com.rsb.sheetsui.domain.model.ColumnValidation? = null
)

sealed interface EditUiState {
    data object Loading : EditUiState
    data class Editing(
        val fields: List<EditableField>,
        val mergeRanges: List<com.rsb.sheetsui.domain.util.MergeRange> = emptyList(),
        val lastModifiedTime: String? = null,
        val headerRowIndex: Int = 0
    ) : EditUiState
    data class Saving(val fields: List<EditableField>) : EditUiState
    data object Success : EditUiState
    data class Error(val message: String) : EditUiState
    data class ConflictDetected(val message: String, val editingState: Editing) : EditUiState
}
