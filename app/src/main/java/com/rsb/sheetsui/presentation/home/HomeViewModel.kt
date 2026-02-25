package com.rsb.sheetsui.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.domain.model.Form
import com.rsb.sheetsui.domain.model.Spreadsheet
import com.rsb.sheetsui.domain.repository.CreatedForm
import com.rsb.sheetsui.domain.repository.FormRepository
import com.rsb.sheetsui.domain.repository.SpreadsheetRepository
import com.rsb.sheetsui.domain.util.CurrencyLocale
import com.rsb.sheetsui.domain.util.VoiceCommandParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class HomeTab { Sheets, Forms }

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val repository: SpreadsheetRepository,
    private val formRepository: FormRepository,
    private val firebaseAuth: com.google.firebase.auth.FirebaseAuth
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(HomeTab.Sheets)
    val selectedTab: StateFlow<HomeTab> = _selectedTab.asStateFlow()

    private val _state = MutableStateFlow<HomeUiState>(HomeUiState.Loading)
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    private val _formsState = MutableStateFlow<HomeFormsUiState>(HomeFormsUiState.Loading)
    val formsState: StateFlow<HomeFormsUiState> = _formsState.asStateFlow()

    private val _creating = MutableStateFlow(false)
    val creating: StateFlow<Boolean> = _creating.asStateFlow()

    private val _creatingForm = MutableStateFlow(false)
    val creatingForm: StateFlow<Boolean> = _creatingForm.asStateFlow()

    private val _events = MutableSharedFlow<HomeEvent>()
    val events: SharedFlow<HomeEvent> = _events.asSharedFlow()

    init {
        loadSpreadsheets()
        loadForms()
    }

    fun selectTab(tab: HomeTab) {
        _selectedTab.value = tab
    }

    fun loadSpreadsheets() {
        _state.value = HomeUiState.Loading
        viewModelScope.launch {
            repository.getSpreadsheets().collect { result ->
                result
                    .onSuccess { list ->
                        _state.value = if (list.isEmpty()) {
                            HomeUiState.Empty
                        } else {
                            HomeUiState.Success(list)
                        }
                    }
                    .onFailure { error ->
                        _state.value = HomeUiState.Error(
                            error.localizedMessage ?: "Failed to load spreadsheets"
                        )
                    }
            }
        }
    }

    fun loadForms() {
        _formsState.value = HomeFormsUiState.Loading
        viewModelScope.launch {
            repository.getForms().collect { result ->
                result
                    .onSuccess { list ->
                        _formsState.value = if (list.isEmpty()) {
                            HomeFormsUiState.Empty
                        } else {
                            HomeFormsUiState.Success(list)
                        }
                    }
                    .onFailure { error ->
                        _formsState.value = HomeFormsUiState.Error(
                            error.localizedMessage ?: "Failed to load forms"
                        )
                    }
            }
        }
    }

    fun createSpreadsheet(title: String) {
        if (_creating.value) return
        _creating.value = true

        viewModelScope.launch {
            repository.createSpreadsheet(title)
                .onSuccess { spreadsheet ->
                    _events.emit(HomeEvent.SpreadsheetCreated(spreadsheet))
                    loadSpreadsheets()
                }
                .onFailure { error ->
                    _events.emit(HomeEvent.Error(
                        error.localizedMessage ?: "Failed to create spreadsheet"
                    ))
                }
            _creating.value = false
        }
    }

    fun createForm(title: String) {
        if (_creatingForm.value) return
        _creatingForm.value = true

        viewModelScope.launch {
                formRepository.createStandaloneForm(title)
                .onSuccess { created ->
                    _events.emit(HomeEvent.FormCreated(created))
                    loadForms()
                }
                .onFailure { error ->
                    _events.emit(HomeEvent.Error(
                        error.localizedMessage ?: "Failed to create form"
                    ))
                }
            _creatingForm.value = false
        }
    }

    private val _deletingId = MutableStateFlow<String?>(null)
    val deletingId: StateFlow<String?> = _deletingId.asStateFlow()

    private val _showVoiceDialog = MutableStateFlow(false)
    val showVoiceDialog: StateFlow<Boolean> = _showVoiceDialog.asStateFlow()

    fun startVoiceInput() { _showVoiceDialog.value = true }
    fun dismissVoiceDialog() { _showVoiceDialog.value = false }

    fun appendVoiceRow(spreadsheetId: String, transcript: String) {
        viewModelScope.launch {
            val parsed = VoiceCommandParser.parse(transcript)
            if (parsed.amount.isBlank()) {
                _events.emit(HomeEvent.Error("Could not parse amount from: $transcript"))
                return@launch
            }
            val dataResult = repository.getSheetData(spreadsheetId, "").first()
            dataResult.onSuccess { data ->
                if (data.headers.isEmpty()) {
                    _events.emit(HomeEvent.Error("Sheet has no headers"))
                    return@launch
                }
                val row = data.headers.map { header ->
                    val h = header.lowercase()
                    when {
                        h.contains("category") || h.contains("description") || h.contains("item") -> parsed.category
                        h.contains("amount") || h.contains("total") || h.contains("price") || h.contains("cost") -> "${CurrencyLocale.defaultSymbol}${parsed.amount}"
                        h.contains("date") -> parsed.date
                        else -> ""
                    }
                }
                val email = firebaseAuth.currentUser?.email ?: ""
                val ts = java.time.Instant.now().toString().take(19)
                val audit = if (email.isNotBlank()) email to ts else null
                repository.appendRow(spreadsheetId, "", row, audit)
                    .onSuccess { _events.emit(HomeEvent.SpreadsheetCreated(Spreadsheet(spreadsheetId, "Updated", null, null))) }
                    .onFailure { _events.emit(HomeEvent.Error(it.localizedMessage ?: "Failed to add row")) }
            }.onFailure { _events.emit(HomeEvent.Error(it.localizedMessage ?: "Failed to load sheet")) }
        }
    }

    fun deleteSpreadsheet(fileId: String) {
        if (_deletingId.value != null) return
        _deletingId.value = fileId

        viewModelScope.launch {
            repository.deleteSpreadsheet(fileId)
                .onSuccess {
                    _events.emit(HomeEvent.SpreadsheetDeleted)
                    loadSpreadsheets()
                }
                .onFailure { error ->
                    _events.emit(HomeEvent.Error(
                        error.localizedMessage ?: "Failed to delete spreadsheet"
                    ))
                }
            _deletingId.value = null
        }
    }

    private val _deletingFormId = MutableStateFlow<String?>(null)
    val deletingFormId: StateFlow<String?> = _deletingFormId.asStateFlow()

    fun deleteForm(formId: String) {
        if (_deletingFormId.value != null) return
        _deletingFormId.value = formId

        viewModelScope.launch {
            repository.deleteForm(formId)
                .onSuccess {
                    _events.emit(HomeEvent.FormDeleted)
                    loadForms()
                }
                .onFailure { error ->
                    _events.emit(HomeEvent.Error(
                        error.localizedMessage ?: "Failed to delete form"
                    ))
                }
            _deletingFormId.value = null
        }
    }
}

sealed interface HomeUiState {
    data object Loading : HomeUiState
    data object Empty : HomeUiState
    data class Success(val spreadsheets: List<Spreadsheet>) : HomeUiState
    data class Error(val message: String) : HomeUiState
}

sealed interface HomeFormsUiState {
    data object Loading : HomeFormsUiState
    data object Empty : HomeFormsUiState
    data class Success(val forms: List<Form>) : HomeFormsUiState
    data class Error(val message: String) : HomeFormsUiState
}

sealed interface HomeEvent {
    data class SpreadsheetCreated(val spreadsheet: Spreadsheet) : HomeEvent
    data class FormCreated(val form: CreatedForm) : HomeEvent
    data object SpreadsheetDeleted : HomeEvent
    data object FormDeleted : HomeEvent
    data class Error(val message: String) : HomeEvent
}
