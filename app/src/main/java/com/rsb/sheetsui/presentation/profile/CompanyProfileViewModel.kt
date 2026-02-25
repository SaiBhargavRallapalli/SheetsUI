package com.rsb.sheetsui.presentation.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.rsb.sheetsui.data.prefs.CompanyPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CompanyProfileViewModel @Inject constructor(
    private val companyPrefs: CompanyPreferences
) : ViewModel() {

    val logoUri: Flow<String?> = companyPrefs.logoUri
    val primaryColor: Flow<Long?> = companyPrefs.primaryColor

    fun setLogoUri(uri: String?) {
        viewModelScope.launch {
            companyPrefs.setLogoUri(uri)
        }
    }

    fun setPrimaryColor(color: Long) {
        viewModelScope.launch {
            companyPrefs.setPrimaryColor(color)
        }
    }
}
