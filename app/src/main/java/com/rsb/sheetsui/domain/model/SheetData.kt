package com.rsb.sheetsui.domain.model

import com.rsb.sheetsui.domain.model.ColumnValidation
import com.rsb.sheetsui.domain.util.MergeRange

/** Financial column header keywords for SummaryStats. */
private val FINANCIAL_HEADERS = setOf("amount", "total", "price", "cost", "sum", "value")

/** Status column header keywords for SummaryStats. */
private val STATUS_HEADERS = setOf("status", "state", "stage", "phase")

/**
 * Domain model for sheet content with headers and rows.
 */
data class SheetData(
    val spreadsheetId: String,
    val sheetName: String,
    val headers: List<String>,
    val rows: List<List<Any?>>,
    /** 0-based row index of the header row in the sheet. CRUD uses this as offset. */
    val headerRowIndex: Int = 0,
    /** Indices into rows that are separator rows (non-editable visual dividers). */
    val separatorRowIndices: Set<Int> = emptySet(),
    /** Merge ranges for merged cell support. Row/col are 0-based API coordinates. */
    val mergeRanges: List<MergeRange> = emptyList(),
    /** Raw formula strings; formulaRows[row][col] starts with '=' if cell contains formula. */
    val formulaRows: List<List<String?>> = emptyList(),
    /** Data validation per column (dropdown options or checkbox) from Google Sheets. */
    val columnValidations: Map<Int, ColumnValidation> = emptyMap(),
    /** Drive file modifiedTime when data was fetched (for conflict detection). */
    val lastModifiedTime: String? = null,
    /** True if sheet has tables/filterViews (use absolute row indices). */
    val isStructuredTable: Boolean = false
) {
    /** Enterprise summary: Total Records, Financial Summary, Status Distribution. */
    val summaryStats: SummaryStats by lazy {
        val total = rows.size
        val financialCol = headers.indexOfFirst { h ->
            FINANCIAL_HEADERS.any { h.contains(it, ignoreCase = true) }
        }
        val financialSum = if (financialCol >= 0) {
            rows.sumOf { row ->
                val v = row.getOrElse(financialCol) { null }?.toString()?.trim().orEmpty()
                v.replace(Regex("[^0-9.-]"), "").toDoubleOrNull() ?: 0.0
            }.takeIf { it != 0.0 }
        } else null
        val statusCol = headers.indexOfFirst { h ->
            STATUS_HEADERS.any { h.contains(it, ignoreCase = true) }
        }
        val statusDistribution = if (statusCol >= 0) {
            val counts = rows.groupingBy { r ->
                (r.getOrElse(statusCol) { null }?.toString()?.trim() ?: "").takeIf { it.isNotBlank() } ?: "(Blank)"
            }.eachCount()
            val totalCount = counts.values.sum()
            counts.map { (label, count) ->
                StatusCount(label, count, if (totalCount > 0) count.toFloat() / totalCount else 0f)
            }.sortedByDescending { it.count }
        } else emptyList()
        SummaryStats(totalRecords = total, financialSum = financialSum, statusDistribution = statusDistribution)
    }

    /** Summary stats from formula cells (=SUM, =COUNT, =AVERAGE). */
    val quickStats: List<QuickStat> by lazy {
        val result = mutableListOf<QuickStat>()
        formulaRows.forEachIndexed { rowIdx, formulaRow ->
            formulaRow.forEachIndexed { colIdx, formula ->
                if (formula != null && formula.startsWith("=")) {
                    val label = when {
                        formula.uppercase().startsWith("=SUM") -> "Total Spent"
                        formula.uppercase().startsWith("=COUNT") -> "Total Count"
                        formula.uppercase().startsWith("=AVERAGE") -> "Average"
                        else -> headers.getOrElse(colIdx) { "" }.takeIf { it.isNotBlank() } ?: "Total"
                    }
                    val value = rows.getOrNull(rowIdx)?.getOrElse(colIdx) { "" }?.toString() ?: ""
                    if (value.isNotBlank()) result.add(QuickStat(label, value))
                }
            }
        }
        result.distinctBy { it.label }
    }
}

data class QuickStat(val label: String, val value: String)
