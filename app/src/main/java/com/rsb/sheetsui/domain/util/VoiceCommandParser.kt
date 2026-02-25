package com.rsb.sheetsui.domain.util

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses voice commands like "Add 450 rupees for lunch today"
 * to extract Category, Amount, and Date.
 */
object VoiceCommandParser {

    private val AMOUNT_REGEX = Regex(
        """(?:add|spent|spend|paid|cost)\s+(\d+(?:[.,]\d+)?)\s*(?:rupees?|rs|inr|₹)?""",
        RegexOption.IGNORE_CASE
    )
    private val ALT_AMOUNT_REGEX = Regex("""(\d+(?:[.,]\d+)?)\s*(?:rupees?|rs|inr|₹)""", RegexOption.IGNORE_CASE)

    private val CATEGORY_REGEX = Regex(
        """(?:for|on|category)\s+(\w+(?:\s+\w+)?)""",
        RegexOption.IGNORE_CASE
    )
    private val ALT_CATEGORY_REGEX = Regex(
        """(?:lunch|dinner|breakfast|snacks|groceries|transport|fuel|shopping|entertainment|rent|utilities|food|coffee|drinks)""",
        RegexOption.IGNORE_CASE
    )

    private val TODAY_ALIASES = setOf("today", "todays", "now")
    private val YESTERDAY_ALIASES = setOf("yesterday", "yday")
    private val TOMORROW_ALIASES = setOf("tomorrow", "tmr")

    fun parse(utterance: String): VoiceParsedData {
        val amount = parseAmount(utterance)
        val category = parseCategory(utterance)
        val date = parseDate(utterance)
        return VoiceParsedData(
            category = category,
            amount = amount,
            date = date
        )
    }

    private fun parseAmount(text: String): String {
        AMOUNT_REGEX.find(text)?.let { m -> return m.groupValues[1].replace(",", ".") }
        ALT_AMOUNT_REGEX.find(text)?.let { m -> return m.groupValues[1].replace(",", ".") }
        return ""
    }

    private fun parseCategory(text: String): String {
        CATEGORY_REGEX.find(text)?.let { m -> return m.groupValues[1].trim() }
        ALT_CATEGORY_REGEX.find(text)?.let { m -> return m.value.lowercase() }
        return ""
    }

    private fun parseDate(text: String): String {
        val lower = text.lowercase()
        val today = LocalDate.now()
        return when {
            TODAY_ALIASES.any { lower.contains(it) } -> today.format(DateTimeFormatter.ISO_LOCAL_DATE)
            YESTERDAY_ALIASES.any { lower.contains(it) } -> today.minusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            TOMORROW_ALIASES.any { lower.contains(it) } -> today.plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE)
            else -> today.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }
    }
}

data class VoiceParsedData(
    val category: String = "",
    val amount: String = "",
    val date: String = ""
)
