package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for Google Sheets API v4 ValueRange response.
 * @see <a href="https://developers.google.com/sheets/api/reference/rest/v4/spreadsheets.values#ValueRange">ValueRange</a>
 */
data class ValueRangeDto(
    @SerializedName("range") val range: String? = null,
    @SerializedName("majorDimension") val majorDimension: String = "ROWS",
    @SerializedName("values") val values: List<List<Any?>>? = null
)
