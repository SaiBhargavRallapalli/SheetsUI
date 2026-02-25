package com.rsb.sheetsui.domain.util

import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts Merchant Name, Total Amount, and Date from OCR text (e.g. receipt scans).
 * Uses vertical alignment: sorts text blocks by Y-coordinate so item names and prices
 * on the same line are associated correctly.
 */
object ReceiptOcrExtractor {

    private val textRecognizer: TextRecognizer =
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    /** Regex for currency amounts: 450, 1,234.56, ₹500, $10.99 */
    private val AMOUNT_PATTERNS = listOf(
        Regex("""(?:total|amount|amt|sum|due|balance)\s*:?\s*[₹$€£]?\s*([\d,]+\.?\d*)""", RegexOption.IGNORE_CASE),
        Regex("""[₹$€£]\s*([\d,]+\.?\d*)"""),
        Regex("""([\d,]+\.?\d{2})\s*(?:inr|rs|rupees?|usd|eur)""", RegexOption.IGNORE_CASE)
    )

    /** Regex for dates: 23/02/2025, 23-02-2025, 2025-02-23, Feb 23 2025 */
    private val DATE_PATTERNS = listOf(
        Regex("""(\d{1,2})[/\-](\d{1,2})[/\-](\d{2,4})"""),
        Regex("""(\d{4})[/\-](\d{2})[/\-](\d{2})"""),
        Regex("""(?:date|on)\s*:?\s*(\d{1,2})[/\-]\d{1,2}[/\-]\d{2,4}""", RegexOption.IGNORE_CASE)
    )

    /** First line often is merchant name; or look for "at [Name]" */
    private val MERCHANT_INDICATORS = listOf(
        Regex("""(?:at|from|store|merchant)\s*:?\s*(.+?)(?:\n|$)""", RegexOption.IGNORE_CASE)
    )

    suspend fun processImage(image: InputImage): ReceiptData = withContext(Dispatchers.IO) {
        runCatching {
            val result: Text = Tasks.await(textRecognizer.process(image))
            val orderedText = buildVerticallyOrderedText(result)
            extractReceiptData(orderedText)
        }.getOrDefault(ReceiptData())
    }

    /** Sorts all text lines by Y-coordinate for correct item–price alignment. */
    private fun buildVerticallyOrderedText(text: Text): String {
        val linesWithY = mutableListOf<Pair<Int, String>>()
        for (block in text.textBlocks) {
            val blockBox = block.boundingBox
            for (line in block.lines) {
                val lineBox = line.boundingBox ?: blockBox
                val y = lineBox?.top ?: 0
                linesWithY.add(y to line.text)
            }
        }
        return linesWithY
            .sortedBy { it.first }
            .map { it.second }
            .joinToString("\n")
    }

    fun extractReceiptData(fullText: String): ReceiptData {
        val lines = fullText.lines().map { it.trim() }.filter { it.isNotBlank() }
        return ReceiptData(
            merchantName = extractMerchant(lines),
            totalAmount = extractAmount(fullText),
            date = extractDate(fullText),
            receiptType = extractReceiptType(fullText)
        )
    }

    private fun extractMerchant(lines: List<String>): String {
        if (lines.isEmpty()) return ""
        for (indicator in MERCHANT_INDICATORS) {
            val match = indicator.find(lines.joinToString("\n"))
            if (match != null) return match.groupValues.getOrElse(1) { "" }.trim().take(100)
        }
        return lines.firstOrNull()?.take(80) ?: ""
    }

    private fun extractAmount(text: String): String {
        for (pattern in AMOUNT_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val num = match.groupValues.getOrElse(1) { "" }.replace(",", "")
                if (num.isNotBlank() && num.toDoubleOrNull() != null) {
                    return num
                }
            }
        }
        return ""
    }

    private fun extractDate(text: String): String {
        for (pattern in DATE_PATTERNS) {
            val match = pattern.find(text)
            if (match != null) {
                val groups = match.groupValues
                if (groups.size >= 4) {
                    val d = groups[1]
                    val m = groups[2]
                    val y = groups[3].let { if (it.length == 2) "20$it" else it }
                    return "$y-${m.padStart(2, '0')}-${d.padStart(2, '0')}"
                }
                if (groups.size >= 2) return groups[1]
            }
        }
        return ""
    }

    private fun extractReceiptType(text: String): String {
        val lower = text.lowercase()
        return when {
            Regex("""\binvoice\b""").containsMatchIn(lower) -> "Invoice"
            Regex("""\b(cash\s*receipt|receipt)\b""").containsMatchIn(lower) -> "Cash"
            Regex("""\bestimate\b""").containsMatchIn(lower) -> "Estimate"
            Regex("""\bquotation\b""").containsMatchIn(lower) -> "Quotation"
            Regex("""\bbill\b""").containsMatchIn(lower) -> "Bill"
            else -> ""
        }
    }
}

data class ReceiptData(
    val merchantName: String = "",
    val totalAmount: String = "",
    val date: String = "",
    val receiptType: String = ""
)
