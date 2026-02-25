package com.rsb.sheetsui.data.remote.api

import com.rsb.sheetsui.data.remote.dto.forms.FormCreateRequestDto
import com.rsb.sheetsui.data.remote.dto.forms.FormCreateResponseDto
import com.rsb.sheetsui.data.remote.dto.forms.FormBatchUpdateRequestDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Path

/**
 * Retrofit interface for Google Forms API v1.
 * Base URL: https://forms.googleapis.com/v1/
 */
interface GoogleFormsService {

    /**
     * Creates a new form with the given title.
     * POST https://forms.googleapis.com/v1/forms
     */
    @POST("forms")
    suspend fun createForm(
        @Body body: FormCreateRequestDto
    ): Response<FormCreateResponseDto>

    /**
     * Batch updates a form (add questions, etc).
     * POST https://forms.googleapis.com/v1/forms/{formId}:batchUpdate
     */
    @POST("forms/{formId}:batchUpdate")
    suspend fun batchUpdateForm(
        @Path("formId") formId: String,
        @Body body: FormBatchUpdateRequestDto
    ): Response<FormBatchUpdateResponseDto>
}

data class FormBatchUpdateResponseDto(
    val form: FormCreateResponseDto? = null
)
