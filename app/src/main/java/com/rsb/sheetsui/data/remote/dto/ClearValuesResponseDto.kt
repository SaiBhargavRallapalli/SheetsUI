package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for Google Sheets API v4 spreadsheets.values.clear response.
 */
data class ClearValuesResponseDto(
    @SerializedName("spreadsheetId") val spreadsheetId: String? = null,
    @SerializedName("clearedRange") val clearedRange: String? = null
)
