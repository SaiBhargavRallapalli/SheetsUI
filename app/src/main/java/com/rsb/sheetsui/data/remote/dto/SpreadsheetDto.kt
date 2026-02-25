package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for Google Sheets API v4 spreadsheets.get response (metadata).
 */
data class SpreadsheetDto(
    @SerializedName("spreadsheetId") val spreadsheetId: String? = null,
    @SerializedName("properties") val properties: SpreadsheetPropertiesDto? = null,
    @SerializedName("sheets") val sheets: List<SheetDto>? = null
)

data class SpreadsheetPropertiesDto(
    @SerializedName("title") val title: String? = null,
    @SerializedName("locale") val locale: String? = null,
    @SerializedName("timeZone") val timeZone: String? = null
)

data class SheetDto(
    @SerializedName("properties") val properties: SheetPropertiesDto? = null,
    @SerializedName("merges") val merges: List<MergeRangeDto>? = null,
    @SerializedName("filterViews") val filterViews: List<Any>? = null,
    @SerializedName("basicFilter") val basicFilter: Any? = null,
    @SerializedName("tables") val tables: List<Any>? = null,
    @SerializedName("developerMetadata") val developerMetadata: List<Any>? = null,
    @SerializedName("data") val data: List<GridDataDto>? = null
)

data class GridDataDto(
    @SerializedName("rowData") val rowData: List<RowDataDto>? = null
)

data class RowDataDto(
    @SerializedName("values") val values: List<CellDataDto>? = null
)

data class CellDataDto(
    @SerializedName("dataValidation") val dataValidation: DataValidationRuleDto? = null
)

data class DataValidationRuleDto(
    @SerializedName("condition") val condition: BooleanConditionDto? = null
)

data class BooleanConditionDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("values") val values: List<ConditionValueDto>? = null
)

data class ConditionValueDto(
    @SerializedName("userEnteredValue") val userEnteredValue: String? = null
)

/** Merge range from Sheets API. Primary (top-left) cell is at startRowIndex, startColumnIndex. */
data class MergeRangeDto(
    @SerializedName("startRowIndex") val startRowIndex: Int = 0,
    @SerializedName("endRowIndex") val endRowIndex: Int,
    @SerializedName("startColumnIndex") val startColumnIndex: Int = 0,
    @SerializedName("endColumnIndex") val endColumnIndex: Int
)

data class SheetPropertiesDto(
    @SerializedName("sheetId") val sheetId: Int? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("index") val index: Int? = null
)
