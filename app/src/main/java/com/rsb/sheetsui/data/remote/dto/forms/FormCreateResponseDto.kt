package com.rsb.sheetsui.data.remote.dto.forms

import com.google.gson.annotations.SerializedName

data class FormCreateResponseDto(
    @SerializedName("formId") val formId: String? = null,
    @SerializedName("responderUri") val responderUri: String? = null,
    @SerializedName("linkedSheetId") val linkedSheetId: String? = null,
    @SerializedName("revisionId") val revisionId: String? = null,
    val info: FormInfoDto? = null
)
