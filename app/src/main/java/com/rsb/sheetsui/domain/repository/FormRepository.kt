package com.rsb.sheetsui.domain.repository

import com.rsb.sheetsui.domain.model.FieldType
import com.rsb.sheetsui.domain.model.SheetData

data class CreatedForm(
    val formId: String,
    val responderUri: String,
    val formTitle: String
)

interface FormRepository {

    /**
     * Creates a standalone Google Form with the given title and one default text question.
     * Returns the form's responder URI for sharing/editing.
     */
    suspend fun createStandaloneForm(formTitle: String): Result<CreatedForm>

    /**
     * Creates a Google Form with one question per header.
     * Each question type is inferred from the header name (e.g. "Date" -> date question).
     * Returns the form's responder URI for sharing.
     */
    suspend fun createFormFromHeaders(
        formTitle: String,
        headers: List<String>,
        headerTypes: Map<Int, FieldType>?
    ): Result<CreatedForm>

    /**
     * Creates a Google Form from a spreadsheet's sheet data.
     * Uses headers, inferred field types, and column validations (e.g. dropdown options).
     * Returns the form's responder URI for sharing.
     * Note: Response integration (linking form to spreadsheet) must be done manually in Forms UI
     * via Responses → Link to Sheets → Select existing spreadsheet.
     */
    suspend fun createFormFromSpreadsheet(
        spreadsheetId: String,
        spreadsheetName: String,
        sheetData: SheetData
    ): Result<CreatedForm>
}
