package com.rsb.sheetsui.domain.util

/** Detected action type for a cell value. */
sealed interface CellActionType {
    data object None : CellActionType
    data object Phone : CellActionType
    data object Address : CellActionType
    data object Url : CellActionType
}

/** Regex for phone numbers (international format optional). */
private val PHONE_REGEX = Regex(
    """(?:\+?\d{1,3}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{2,4}[-.\s]?\d{2,4}(?:[-.\s]?\d{2,4})?"""
)

/** URL pattern. */
private val URL_REGEX = Regex(
    """https?://[^\s]+""",
    RegexOption.IGNORE_CASE
)

/** Address hints (street, ave, blvd, etc.). */
private val ADDRESS_HINTS = setOf("street", "st", "ave", "avenue", "blvd", "road", "rd", "lane", "dr", "drive")

object CellActionDetector {
    fun detect(value: String?): CellActionType {
        val s = value?.trim().orEmpty()
        if (s.isEmpty()) return CellActionType.None
        if (URL_REGEX.matches(s) || s.startsWith("http://", true) || s.startsWith("https://", true)) {
            return CellActionType.Url
        }
        if (PHONE_REGEX.matches(s.replace(" ", "")) && s.count { it.isDigit() } >= 7) {
            return CellActionType.Phone
        }
        if (s.length > 10 && ADDRESS_HINTS.any { s.lowercase().contains(it) }) {
            return CellActionType.Address
        }
        val urlMatch = URL_REGEX.find(s)
        if (urlMatch != null) return CellActionType.Url
        return CellActionType.None
    }

    fun extractUrl(value: String?): String? {
        val s = value?.trim().orEmpty()
        if (s.startsWith("http://", true) || s.startsWith("https://", true)) return s
        return URL_REGEX.find(s)?.value
    }

    fun extractPhoneUri(value: String?): String {
        val digits = value?.filter { it.isDigit() } ?: ""
        return "tel:$digits"
    }

    fun extractMapQuery(value: String?): String =
        android.net.Uri.encode(value?.trim().orEmpty())
}
