package com.rsb.sheetsui.domain.inference

import com.rsb.sheetsui.domain.model.FieldType
import java.util.Locale

/**
 * Analyses the header row and a sample data row to assign a [FieldType]
 * to each column.  Uses a two-signal approach:
 *   1. Header keyword matching (high confidence)
 *   2. Sample value pattern matching (confirms or overrides)
 * Formula columns are inferred and treated as read-only / highlighted by default.
 */
object SheetInferenceEngine {

    private val defaultLocale: Locale get() = Locale.getDefault()

    /**
     * @param userOverrides Optional map of columnIndex -> FieldType for user-defined overrides (from Room).
     */
    fun infer(
        headers: List<String>,
        sampleRow: List<Any?>,
        formulaSampleRow: List<String?>? = null,
        userOverrides: Map<Int, FieldType>? = null
    ): List<FieldType> {
        val columnCount = maxOf(headers.size, sampleRow.size)
        return (0 until columnCount).map { col ->
            userOverrides?.get(col)?.let { return@map it }
            val formulaCell = formulaSampleRow?.getOrElse(col) { null }
            if (formulaCell != null && formulaCell.startsWith("=")) {
                return@map FieldType.FORMULA
            }
            val header = headers.getOrElse(col) { "" }.lowercase().trim()
            val sample = sampleRow.getOrElse(col) { "" }?.toString()?.trim().orEmpty()
            inferColumn(header, sample)
        }
    }

    private fun inferColumn(header: String, sample: String): FieldType {
        val headerHint = inferFromHeader(header)
        val dataHint = inferFromSample(sample)

        // When column has no data (empty sample), use header-based guess
        if (sample.isBlank()) return headerHint

        // When data exists, data signal can override header
        if (dataHint != FieldType.TEXT) return dataHint
        if (headerHint != FieldType.TEXT) return headerHint
        return FieldType.TEXT
    }

    // ── Header-based heuristics ─────────────────────────────────────────

    private val DATE_KEYWORDS = setOf(
        "date", "time", "created", "updated", "modified",
        "birthday", "dob", "deadline", "due", "timestamp",
        "start", "end", "expir"
    )
    private val CURRENCY_KEYWORDS = setOf(
        "price", "cost", "amount", "total", "fee", "salary",
        "revenue", "budget", "payment", "balance", "income",
        "expense", "tax", "discount", "rate"
    )
    private val BOOLEAN_KEYWORDS = setOf(
        "active", "enabled", "completed", "done", "verified",
        "approved", "status", "paid", "available", "visible",
        "published", "archived", "deleted", "confirmed", "toggle"
    )
    private val NUMBER_KEYWORDS = setOf(
        "count", "quantity", "qty", "number", "num", "age",
        "score", "rating", "rank", "index", "size", "weight",
        "height", "length", "width", "percent", "percentage"
    )
    private val CATEGORY_KEYWORDS = setOf(
        "category", "description", "item", "type", "label"
    )
    private val PRODUCT_ID_KEYWORDS = setOf(
        "product id", "productid", "barcode", "sku", "item id", "itemid"
    )

    private fun inferFromHeader(header: String): FieldType = when {
        DATE_KEYWORDS.any { header.contains(it) }       -> FieldType.DATE
        CURRENCY_KEYWORDS.any { header.contains(it) }   -> FieldType.CURRENCY
        BOOLEAN_KEYWORDS.any { header.contains(it) }    -> FieldType.BOOLEAN
        NUMBER_KEYWORDS.any { header.contains(it) }     -> FieldType.NUMBER
        PRODUCT_ID_KEYWORDS.any { header.contains(it) }  -> FieldType.TEXT
        CATEGORY_KEYWORDS.any { header.contains(it) }   -> FieldType.TEXT
        else                                            -> FieldType.TEXT
    }

    // ── Sample-value-based heuristics ───────────────────────────────────

    private val BOOLEAN_VALUES = setOf(
        "true", "false", "yes", "no", "1", "0",
        "y", "n", "on", "off"
    )

    private fun getCurrencyRegex(): Regex {
        val symbols = listOf("$", "€", "£", "¥", "₹") + com.rsb.sheetsui.domain.util.CurrencyLocale.defaultSymbol
        val symbolPattern = symbols.distinct().joinToString("") { Regex.escape(it) }
        return Regex("""^[$symbolPattern]?\s*-?\d{1,3}(,\d{3})*(\.\d{1,2})?$""")
    }

    private val DATE_PATTERNS = listOf(
        Regex("""\d{4}-\d{2}-\d{2}"""),                    // 2024-01-15
        Regex("""\d{1,2}/\d{1,2}/\d{2,4}"""),              // 1/15/2024 or 01/15/24
        Regex("""\d{1,2}-\d{1,2}-\d{2,4}"""),              // 15-01-2024
        Regex("""\d{1,2}\s+\w{3,9}\s+\d{2,4}"""),         // 15 January 2024
        Regex("""\w{3,9}\s+\d{1,2},?\s+\d{2,4}"""),       // January 15, 2024
        Regex("""\d{4}-\d{2}-\d{2}T\d{2}:\d{2}"""),       // ISO 8601
    )

    private val NUMBER_REGEX = Regex("""^-?\d{1,3}(,\d{3})*(\.\d+)?$""")

    private fun inferFromSample(sample: String): FieldType {
        if (sample.isBlank()) return FieldType.TEXT

        if (sample.lowercase() in BOOLEAN_VALUES) return FieldType.BOOLEAN

        val hasCurrencySymbol = sample.firstOrNull()?.let { it in "$€£¥₹" } == true
        if (hasCurrencySymbol && getCurrencyRegex().matches(sample)) return FieldType.CURRENCY

        if (DATE_PATTERNS.any { it.matches(sample) }) return FieldType.DATE

        if (NUMBER_REGEX.matches(sample)) return FieldType.NUMBER

        return FieldType.TEXT
    }
}
