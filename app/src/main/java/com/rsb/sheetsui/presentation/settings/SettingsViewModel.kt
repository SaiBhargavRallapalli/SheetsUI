package com.rsb.sheetsui.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.data.prefs.SecurityPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val securityPrefs: SecurityPreferences
) : ViewModel() {

    val privacyLockEnabled: Flow<Boolean> = securityPrefs.privacyLockEnabled

    fun setPrivacyLock(enabled: Boolean) {
        viewModelScope.launch {
            securityPrefs.setPrivacyLockEnabled(enabled)
        }
    }
}
