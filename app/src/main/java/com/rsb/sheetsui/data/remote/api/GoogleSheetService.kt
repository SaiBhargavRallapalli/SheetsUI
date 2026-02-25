package com.rsb.sheetsui.data.remote.api

import com.rsb.sheetsui.data.remote.dto.AppendValuesResponseDto
import com.rsb.sheetsui.data.remote.dto.BatchGetValuesResponseDto
import com.rsb.sheetsui.data.remote.dto.BatchUpdateResponseDto
import com.rsb.sheetsui.data.remote.dto.BatchUpdateSpreadsheetRequest
import com.rsb.sheetsui.data.remote.dto.ClearValuesResponseDto
import com.rsb.sheetsui.data.remote.dto.CreateSpreadsheetRequestDto
import com.rsb.sheetsui.data.remote.dto.SpreadsheetDto
import com.rsb.sheetsui.data.remote.dto.ValueRangeDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface for Google Sheets API v4.
 * Base URL: https://sheets.googleapis.com
 *
 * All endpoints require OAuth 2.0 authentication.
 * Add "Authorization: Bearer {access_token}" header via OkHttp Interceptor.
 */
interface GoogleSheetService {

    @POST("v4/spreadsheets")
    suspend fun createSpreadsheet(
        @Body body: CreateSpreadsheetRequestDto
    ): Response<SpreadsheetDto>

    @POST("v4/spreadsheets/{spreadsheetId}:batchUpdate")
    suspend fun batchUpdate(
        @Path("spreadsheetId") spreadsheetId: String,
        @Body body: BatchUpdateSpreadsheetRequest
    ): Response<BatchUpdateResponseDto>

    @GET("v4/spreadsheets/{spreadsheetId}")
    suspend fun getSpreadsheet(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("includeGridData") includeGridData: Boolean = false,
        @Query("fields") fields: String? = null,
        @Query("ranges") ranges: List<String>? = null
    ): Response<SpreadsheetDto>

    /**
     * Returns a range of values from a spreadsheet.
     * GET https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}
     *
     * @param range A1 notation, e.g. "Sheet1!A1:D10" or "Sheet1"
     * @param valueRenderOption How values should be rendered (FORMATTED_VALUE, UNFORMATTED_VALUE, FORMULA)
     * @param dateTimeRenderOption How dates/times should be rendered (SERIAL_NUMBER, FORMATTED_STRING)
     */
    @GET("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun getValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Query("valueRenderOption") valueRenderOption: String = "FORMATTED_VALUE",
        @Query("dateTimeRenderOption") dateTimeRenderOption: String = "FORMATTED_STRING",
        @Query("majorDimension") majorDimension: String = "ROWS"
    ): Response<ValueRangeDto>

    /**
     * Returns one or more ranges of values from a spreadsheet.
     * GET https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values:batchGet
     *
     * @param ranges A1 notation ranges (e.g. "Sheet1!A1:B2" or "Sheet1!A1:D")
     */
    @GET("v4/spreadsheets/{spreadsheetId}/values:batchGet")
    suspend fun batchGetValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Query("ranges") ranges: List<String>,
        @Query("valueRenderOption") valueRenderOption: String = "FORMATTED_VALUE",
        @Query("dateTimeRenderOption") dateTimeRenderOption: String = "FORMATTED_STRING",
        @Query("majorDimension") majorDimension: String = "ROWS"
    ): Response<BatchGetValuesResponseDto>

    /**
     * Appends values to a spreadsheet.
     * POST https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}:append
     *
     * @param range A1 notation for the range to search for a table (e.g. "Sheet1!A1:D")
     * @param valueInputOption USER_ENTERED (parses as if typed) or RAW (stores as-is)
     * @param insertDataOption INSERT_ROWS or OVERWRITE
     */
    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:append")
    suspend fun appendValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Body body: ValueRangeDto,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Query("insertDataOption") insertDataOption: String = "INSERT_ROWS",
        @Query("includeValuesInResponse") includeValuesInResponse: Boolean = false
    ): Response<AppendValuesResponseDto>

    /**
     * Sets values in a range of a spreadsheet.
     * PUT https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}
     *
     * Use for UPDATE operations - replaces existing data in the range.
     */
    @PUT("v4/spreadsheets/{spreadsheetId}/values/{range}")
    suspend fun updateValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String,
        @Body body: ValueRangeDto,
        @Query("valueInputOption") valueInputOption: String = "USER_ENTERED",
        @Query("includeValuesInResponse") includeValuesInResponse: Boolean = false
    ): Response<ValueRangeDto>

    /**
     * Clears values from a spreadsheet.
     * POST https://sheets.googleapis.com/v4/spreadsheets/{spreadsheetId}/values/{range}:clear
     */
    @POST("v4/spreadsheets/{spreadsheetId}/values/{range}:clear")
    suspend fun clearValues(
        @Path("spreadsheetId") spreadsheetId: String,
        @Path("range") range: String
    ): Response<ClearValuesResponseDto>
}
