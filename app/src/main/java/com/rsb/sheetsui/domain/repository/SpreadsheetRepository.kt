package com.rsb.sheetsui.domain.repository

import com.rsb.sheetsui.domain.model.Form
import com.rsb.sheetsui.domain.model.SheetData
import com.rsb.sheetsui.domain.model.SheetTab
import com.rsb.sheetsui.domain.model.Spreadsheet
import kotlinx.coroutines.flow.Flow

interface SpreadsheetRepository {

    fun getSpreadsheets(): Flow<Result<List<Spreadsheet>>>

    fun getForms(): Flow<Result<List<Form>>>

    suspend fun getSheetTabs(spreadsheetId: String): Result<List<SheetTab>>

    fun getSheetData(spreadsheetId: String, sheetName: String): Flow<Result<SheetData>>

    suspend fun addSheet(spreadsheetId: String, title: String): Result<SheetTab>

    suspend fun renameSheet(spreadsheetId: String, sheetId: Int, newName: String): Result<Unit>

    suspend fun duplicateSheet(spreadsheetId: String, sourceSheetId: Int, newSheetName: String): Result<SheetTab>

    suspend fun createSpreadsheet(title: String): Result<Spreadsheet>

    suspend fun appendRow(
        spreadsheetId: String,
        sheetName: String,
        row: List<Any?>,
        auditInfo: Pair<String, String>? = null
    ): Result<Unit>

    suspend fun updateRow(
        spreadsheetId: String,
        sheetName: String,
        rowIndex: Int,
        row: List<Any?>,
        mergeRanges: List<com.rsb.sheetsui.domain.util.MergeRange>? = null,
        headerRowIndex: Int = 0,
        auditInfo: Pair<String, String>? = null
    ): Result<Unit>

    suspend fun deleteRow(spreadsheetId: String, sheetName: String, rowIndex: Int, headerRowIndex: Int = 0): Result<Unit>

    suspend fun deleteRow(spreadsheetId: String, sheetId: Int, rowIndex: Int, headerRowIndex: Int = 0): Result<Unit>

    suspend fun addColumn(spreadsheetId: String, sheetName: String, columnName: String, currentColumnCount: Int, headerRowIndex: Int = 0): Result<Unit>

    suspend fun renameColumn(spreadsheetId: String, sheetName: String, columnIndex: Int, newName: String, headerRowIndex: Int = 0): Result<Unit>

    suspend fun deleteColumn(spreadsheetId: String, sheetName: String, columnIndex: Int): Result<Unit>

    suspend fun initializeHeaders(spreadsheetId: String, sheetName: String, headers: List<String>, headerRowIndex: Int = 0): Result<Unit>

    suspend fun deleteSpreadsheet(fileId: String): Result<Unit>

    suspend fun deleteForm(formId: String): Result<Unit>

    /** Returns current Drive modifiedTime for conflict detection. */
    suspend fun getFileModifiedTime(spreadsheetId: String): Result<String?>
}
