package com.rsb.sheetsui.domain.repository

enum class ExportFormat(val mimeType: String, val extension: String, val displayName: String) {
    CSV("text/csv", "csv", "CSV"),
    XLSX("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "xlsx", "Excel"),
    PDF("application/pdf", "pdf", "PDF")
}

interface ExportRepository {

    /**
     * Exports the spreadsheet to the given format and saves it to the device's Downloads folder.
     * @return Result with the file Uri or error
     */
    suspend fun exportAndSave(
        spreadsheetId: String,
        spreadsheetName: String,
        format: ExportFormat
    ): Result<android.net.Uri>
}
