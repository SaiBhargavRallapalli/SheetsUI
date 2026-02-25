package com.rsb.sheetsui.data.remote.dto.forms

import com.google.gson.annotations.SerializedName

data class FormBatchUpdateRequestDto(
    @SerializedName("includeFormInResponse") val includeFormInResponse: Boolean = true,
    val requests: List<BatchUpdateRequestDto>
)

data class BatchUpdateRequestDto(
    @SerializedName("createItem") val createItem: CreateItemRequestDto? = null,
    @SerializedName("updateFormInfo") val updateFormInfo: UpdateFormInfoRequestDto? = null
)

data class UpdateFormInfoRequestDto(
    val info: FormInfoDto,
    @SerializedName("updateMask") val updateMask: String
)

data class CreateItemRequestDto(
    val item: FormItemDto,
    val location: FormLocationDto
)

data class FormLocationDto(
    val index: Int
)

data class FormItemDto(
    val title: String? = null,
    val description: String? = null,
    @SerializedName("questionItem") val questionItem: QuestionItemDto? = null
)

data class QuestionItemDto(
    val question: FormQuestionDto
)

data class FormQuestionDto(
    val required: Boolean = false,
    @SerializedName("textQuestion") val textQuestion: TextQuestionDto? = null,
    @SerializedName("dateQuestion") val dateQuestion: DateQuestionDto? = null,
    @SerializedName("scaleQuestion") val scaleQuestion: ScaleQuestionDto? = null,
    @SerializedName("choiceQuestion") val choiceQuestion: ChoiceQuestionDto? = null
)

data class TextQuestionDto(
    val paragraph: Boolean = false
)

data class ChoiceQuestionDto(
    val type: String,
    val options: List<ChoiceOptionDto>
)

data class ChoiceOptionDto(
    val value: String
)

data class DateQuestionDto(
    @SerializedName("includeTime") val includeTime: Boolean = false,
    @SerializedName("includeYear") val includeYear: Boolean = true
)

data class ScaleQuestionDto(
    val low: Int = 1,
    val high: Int = 5
)
