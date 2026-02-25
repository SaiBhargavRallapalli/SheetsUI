package com.rsb.sheetsui.data.remote.util

import com.rsb.sheetsui.domain.error.ApiError
import retrofit2.HttpException
import java.io.IOException

object ApiErrorMapper {

    fun fromThrowable(t: Throwable): ApiError = when (t) {
        is HttpException -> {
            val code = t.code()
            val body = t.response()?.errorBody()?.string()
            val rawMessage = parseErrorMessage(body) ?: t.message()
            when (code) {
                429 -> ApiError.RateLimit()
                403 -> ApiError.PermissionDenied(
                    userMessage = "Access denied. Sign out and sign in again, or ensure required APIs are enabled (see ENTERPRISE_SETUP.md)."
                )
                else -> ApiError.Generic(
                    userMessage = rawMessage ?: "Request failed (${code})",
                    retryable = code in 408..499 || code >= 500,
                    rawMessage = rawMessage
                )
            }
        }
        is IOException -> ApiError.Generic(
            userMessage = "Network error. Check your connection and try again.",
            retryable = true,
            rawMessage = t.message
        )
        else -> ApiError.Generic(
            userMessage = t.localizedMessage ?: "Something went wrong",
            retryable = true,
            rawMessage = t.message
        )
    }

    fun parseErrorMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        return try {
            com.google.gson.JsonParser.parseString(body).asJsonObject
                ?.get("error")?.asJsonObject
                ?.get("message")?.asString
        } catch (_: Exception) {
            null
        }
    }
}
