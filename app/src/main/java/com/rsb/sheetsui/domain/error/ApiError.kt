package com.rsb.sheetsui.domain.error

/**
 * User-friendly API error with clear "Why" and Retry guidance.
 */
sealed class ApiError(
    open val userMessage: String,
    open val retryable: Boolean = true
) {
    /** 429 Too Many Requests – Rate limit exceeded */
    data class RateLimit(
        override val userMessage: String = "Too many requests. Please wait a moment and try again.",
        override val retryable: Boolean = true
    ) : ApiError(userMessage, retryable)

    /** 403 Forbidden – Permission denied */
    data class PermissionDenied(
        override val userMessage: String = "Access denied. Sign out and sign in again, ensure APIs are enabled in Google Cloud (see ENTERPRISE_SETUP.md), or ask the sheet owner to share it with you.",
        override val retryable: Boolean = true
    ) : ApiError(userMessage, retryable)

    /** Generic API error */
    data class Generic(
        override val userMessage: String,
        override val retryable: Boolean = true,
        val rawMessage: String? = null
    ) : ApiError(userMessage, retryable)

    companion object {
        fun fromHttpCode(code: Int, rawMessage: String? = null): ApiError = when (code) {
            429 -> RateLimit()
            403 -> PermissionDenied()
            else -> Generic(
                userMessage = rawMessage ?: "Something went wrong",
                retryable = true,
                rawMessage = rawMessage
            )
        }
    }
}
