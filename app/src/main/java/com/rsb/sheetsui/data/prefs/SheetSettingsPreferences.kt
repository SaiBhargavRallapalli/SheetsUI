package com.rsb.sheetsui.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.sheetSettingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "sheet_settings")

@Singleton
class SheetSettingsPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private fun financialColKey(spreadsheetId: String, sheetName: String) =
        intPreferencesKey("financial_col_${spreadsheetId}_$sheetName")
    private fun currencyKey(spreadsheetId: String, sheetName: String) =
        stringPreferencesKey("currency_${spreadsheetId}_$sheetName")

    fun getFinancialColumnIndex(spreadsheetId: String, sheetName: String): Flow<Int?> =
        context.sheetSettingsDataStore.data.map {
            it[financialColKey(spreadsheetId, sheetName)]
        }

    fun getCurrencySymbol(spreadsheetId: String, sheetName: String): Flow<String?> =
        context.sheetSettingsDataStore.data.map {
            it[currencyKey(spreadsheetId, sheetName)]
        }

    suspend fun setFinancialColumnIndex(spreadsheetId: String, sheetName: String, index: Int?) {
        context.sheetSettingsDataStore.edit { prefs ->
            val key = financialColKey(spreadsheetId, sheetName)
            if (index != null) prefs[key] = index else prefs.remove(key)
        }
    }

    suspend fun setCurrencySymbol(spreadsheetId: String, sheetName: String, symbol: String?) {
        context.sheetSettingsDataStore.edit { prefs ->
            val key = currencyKey(spreadsheetId, sheetName)
            if (symbol != null) prefs[key] = symbol else prefs.remove(key)
        }
    }
}
