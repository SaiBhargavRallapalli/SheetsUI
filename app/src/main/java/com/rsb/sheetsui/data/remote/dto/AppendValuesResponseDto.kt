package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for Google Sheets API v4 spreadsheets.values.append response.
 */
data class AppendValuesResponseDto(
    @SerializedName("spreadsheetId") val spreadsheetId: String? = null,
    @SerializedName("tableRange") val tableRange: String? = null,
    @SerializedName("updates") val updates: UpdateValuesResponseDto? = null
)

data class UpdateValuesResponseDto(
    @SerializedName("spreadsheetId") val spreadsheetId: String? = null,
    @SerializedName("updatedRange") val updatedRange: String? = null,
    @SerializedName("updatedRows") val updatedRows: Int? = null,
    @SerializedName("updatedColumns") val updatedColumns: Int? = null,
    @SerializedName("updatedCells") val updatedCells: Int? = null
)
