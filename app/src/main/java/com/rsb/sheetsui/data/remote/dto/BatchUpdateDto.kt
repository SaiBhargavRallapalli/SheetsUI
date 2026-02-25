package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * Top-level request for POST v4/spreadsheets/{spreadsheetId}:batchUpdate
 * @see <a href="https://developers.google.com/sheets/api/reference/rest/v4/spreadsheets/batchUpdate">batchUpdate</a>
 */
data class BatchUpdateSpreadsheetRequest(
    @SerializedName("requests") val requests: List<Any>,
    @SerializedName("includeSpreadsheetInResponse") val includeSpreadsheetInResponse: Boolean = false
)

/** Request to append rows or columns. Produces {"appendDimension": {...}} */
data class AppendDimensionRequest(
    @SerializedName("appendDimension") val appendDimension: AppendDimensionParams
)

/** Request to delete rows or columns. Produces {"deleteDimension": {...}} */
data class DeleteDimensionRequest(
    @SerializedName("deleteDimension") val deleteDimension: DeleteDimensionParams
)

data class DeleteDimensionParams(
    @SerializedName("range") val range: DimensionRangeDto
)

data class DimensionRangeDto(
    @SerializedName("sheetId") val sheetId: Int,
    @SerializedName("dimension") val dimension: String, // "ROWS" or "COLUMNS"
    @SerializedName("startIndex") val startIndex: Int,
    @SerializedName("endIndex") val endIndex: Int
)

/** Request to update cell values/format. Produces {"updateCells": {...}} */
data class UpdateCellsRequest(
    @SerializedName("updateCells") val updateCells: UpdateCellsParams
)

/** Request to repeat a cell across a range. Produces {"repeatCell": {...}} */
data class RepeatCellRequest(
    @SerializedName("repeatCell") val repeatCell: RepeatCellParams
)

/** Request to update sheet properties (e.g. freeze rows). Produces {"updateSheetProperties": {...}} */
data class UpdateSheetPropertiesRequest(
    @SerializedName("updateSheetProperties") val updateSheetProperties: UpdateSheetPropertiesParams
)

data class UpdateSheetPropertiesParams(
    @SerializedName("properties") val properties: SheetPropertiesUpdateDto,
    @SerializedName("fields") val fields: String
)

data class SheetPropertiesUpdateDto(
    @SerializedName("sheetId") val sheetId: Int,
    @SerializedName("title") val title: String? = null,
    @SerializedName("gridProperties") val gridProperties: GridPropertiesDto? = null
)

data class GridPropertiesDto(
    @SerializedName("frozenRowCount") val frozenRowCount: Int? = null
)

data class AppendDimensionParams(
    @SerializedName("sheetId") val sheetId: Int,
    @SerializedName("dimension") val dimension: String, // "ROWS" or "COLUMNS"
    @SerializedName("length") val length: Int
)

data class UpdateCellsParams(
    @SerializedName("rows") val rows: List<RowData>,
    @SerializedName("start") val start: GridCoordinate,
    @SerializedName("fields") val fields: String
)

data class RepeatCellParams(
    @SerializedName("range") val range: GridRange,
    @SerializedName("cell") val cell: CellData,
    @SerializedName("fields") val fields: String
)

data class GridRange(
    @SerializedName("sheetId") val sheetId: Int,
    @SerializedName("startRowIndex") val startRowIndex: Int = 0,
    @SerializedName("endRowIndex") val endRowIndex: Int = 1,
    @SerializedName("startColumnIndex") val startColumnIndex: Int = 0,
    @SerializedName("endColumnIndex") val endColumnIndex: Int
)

data class GridCoordinate(
    @SerializedName("sheetId") val sheetId: Int,
    @SerializedName("rowIndex") val rowIndex: Int,
    @SerializedName("columnIndex") val columnIndex: Int
)

data class RowData(
    @SerializedName("values") val values: List<CellData>
)

data class CellData(
    @SerializedName("userEnteredValue") val userEnteredValue: ExtendedValue? = null,
    @SerializedName("userEnteredFormat") val userEnteredFormat: CellFormat? = null
)

data class ExtendedValue(
    @SerializedName("stringValue") val stringValue: String? = null,
    @SerializedName("numberValue") val numberValue: Double? = null,
    @SerializedName("boolValue") val boolValue: Boolean? = null
)

data class CellFormat(
    @SerializedName("textFormat") val textFormat: TextFormat? = null,
    @SerializedName("numberFormat") val numberFormat: NumberFormatDto? = null
)

data class NumberFormatDto(
    @SerializedName("type") val type: String = "NUMBER",
    @SerializedName("pattern") val pattern: String? = null
)

data class TextFormat(
    @SerializedName("bold") val bold: Boolean? = null
)

/**
 * Request body for POST v4/spreadsheets (create spreadsheet).
 */
data class CreateSpreadsheetRequestDto(
    @SerializedName("properties") val properties: SpreadsheetPropertiesDto
)

data class BatchUpdateResponseDto(
    @SerializedName("spreadsheetId") val spreadsheetId: String? = null,
    @SerializedName("replies") val replies: List<Any?>? = null,
    @SerializedName("updatedSpreadsheet") val updatedSpreadsheet: SpreadsheetDto? = null
)

/** Request to add a new sheet. Produces {"addSheet": {"properties": {...}}} */
data class AddSheetRequest(
    @SerializedName("addSheet") val addSheet: AddSheetParams
)

data class AddSheetParams(
    @SerializedName("properties") val properties: AddSheetPropertiesDto
)

data class AddSheetPropertiesDto(
    @SerializedName("title") val title: String? = null,
    @SerializedName("index") val index: Int? = null
)

/** Request to duplicate a sheet. Produces {"duplicateSheet": {...}} */
data class DuplicateSheetRequest(
    @SerializedName("duplicateSheet") val duplicateSheet: DuplicateSheetParams
)

data class DuplicateSheetParams(
    @SerializedName("sourceSheetId") val sourceSheetId: Int,
    @SerializedName("newSheetName") val newSheetName: String? = null,
    @SerializedName("insertSheetIndex") val insertSheetIndex: Int? = null
)

/** Reply from duplicateSheet request. */
data class BatchUpdateReplyDto(
    @SerializedName("duplicateSheet") val duplicateSheet: DuplicateSheetReplyDto? = null
)

data class DuplicateSheetReplyDto(
    @SerializedName("properties") val properties: SheetPropertiesDto? = null
)
