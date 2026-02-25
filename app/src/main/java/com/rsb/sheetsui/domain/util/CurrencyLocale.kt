package com.rsb.sheetsui.domain.util

import java.util.Currency
import java.util.Locale

/**
 * Provides locale-aware currency symbol and format patterns.
 * Defaults to INR (₹) for India locale, otherwise uses device default.
 */
object CurrencyLocale {

    private val defaultLocale: Locale get() = Locale.getDefault()

    val defaultSymbol: String
        get() = if (defaultLocale.country.equals("IN", ignoreCase = true)) {
            "₹"
        } else {
            try {
                Currency.getInstance(defaultLocale).symbol
            } catch (_: Exception) {
                "₹"
            }
        }

    /** Indian format: ₹#,##,##0.00 (lakhs/crores) */
    val defaultFormatPattern: String
        get() = if (defaultLocale.country.equals("IN", ignoreCase = true)) {
            "\"₹\"#,##,##0.00"
        } else {
            "\"$defaultSymbol\"#,##0.00"
        }

    /**
     * If value already contains a known currency symbol ($, €, £, ¥, ₹), return it.
     * Otherwise return default symbol.
     */
    fun symbolForValue(value: String): String {
        val trimmed = value.trim()
        return when {
            trimmed.startsWith("$") -> "$"
            trimmed.startsWith("€") -> "€"
            trimmed.startsWith("£") -> "£"
            trimmed.startsWith("¥") -> "¥"
            trimmed.startsWith("₹") -> "₹"
            else -> defaultSymbol
        }
    }
}
