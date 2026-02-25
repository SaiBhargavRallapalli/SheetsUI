package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for Google Sheets API v4 spreadsheets.values.batchGet response.
 */
data class BatchGetValuesResponseDto(
    @SerializedName("spreadsheetId") val spreadsheetId: String? = null,
    @SerializedName("valueRanges") val valueRanges: List<ValueRangeDto>? = null
)
