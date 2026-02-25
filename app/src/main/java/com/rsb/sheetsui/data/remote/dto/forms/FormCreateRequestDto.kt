package com.rsb.sheetsui.data.remote.dto.forms

import com.google.gson.annotations.SerializedName

data class FormCreateRequestDto(
    val info: FormInfoDto
)

data class FormInfoDto(
    val title: String,
    @SerializedName("documentTitle") val documentTitle: String? = null,
    val description: String? = null
)
