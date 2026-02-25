package com.rsb.sheetsui.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.companyDataStore: DataStore<Preferences> by preferencesDataStore(name = "company")

@Singleton
class CompanyPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val logoUriKey = stringPreferencesKey("logo_uri")
    private val primaryColorKey = longPreferencesKey("primary_color")

    val logoUri: Flow<String?> = context.companyDataStore.data.map {
        (it[logoUriKey] ?: "").takeIf { s -> s.isNotEmpty() }
    }
    val primaryColor: Flow<Long?> = context.companyDataStore.data.map { it[primaryColorKey] }

    suspend fun setLogoUri(uri: String?) {
        context.companyDataStore.edit { it[logoUriKey] = uri ?: "" }
    }

    suspend fun setPrimaryColor(color: Long) {
        context.companyDataStore.edit { it[primaryColorKey] = color }
    }
}
