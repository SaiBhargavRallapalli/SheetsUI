package com.rsb.sheetsui.domain.inference

import com.rsb.sheetsui.domain.util.MergeRange
import com.rsb.sheetsui.domain.util.MergedCellResolver

/**
 * Discovers the header row (Anchor Row) in professional spreadsheets where
 * Row 1 may contain titles, dates, or logos. Scans the first 10 rows using:
 * - Density Check: row with most non-empty cells (headers typically 10+, title rows 1–2)
 * - Type Continuity: first row followed by rows of different data types (headers = text, data = numbers/dates)
 * - Separator detection: rows spanning columns or with very few cells = visual dividers
 */
object HeaderDiscoveryEngine {

    private const val MAX_SCAN_ROWS = 10
    private const val MIN_DENSITY_FOR_HEADER = 3
    private const val SEPARATOR_MAX_FILL = 2  // Rows with ≤2 non-empty cells may be separators

    data class DiscoveryResult(
        /** 0-based row index of the header row in the sheet. */
        val headerRowIndex: Int,
        /** Resolved header labels (handles merged cells: same name for each cell in merge). */
        val headers: List<String>,
        /** Rows below header (includes separator rows for visual display). */
        val dataRows: List<List<Any?>>,
        /** Indices into dataRows that are separator rows (non-editable visual dividers). */
        val separatorRowIndicesInData: Set<Int>,
        /** Max column count for column expansion. */
        val maxColumnCount: Int
    )

    /**
     * Discovers the header row and splits headers from data.
     * @param rawRows First N rows from the sheet (e.g. from values API)
     * @param mergeRanges Merge ranges for resolving merged header cells
     */
    fun discover(
        rawRows: List<List<Any?>>,
        mergeRanges: List<MergeRange>
    ): DiscoveryResult {
        if (rawRows.isEmpty()) {
            return DiscoveryResult(0, listOf("Column A"), emptyList(), emptySet(), 1)
        }

        val resolved = resolveMergedCellValues(rawRows, mergeRanges)
        val scanRows = resolved.take(MAX_SCAN_ROWS)

        val headerRowIndex = findHeaderRow(scanRows)
        val separatorIndices = identifySeparatorRows(scanRows, headerRowIndex)
        val headerCells = extractHeadersWithMerges(scanRows, mergeRanges, headerRowIndex)
        val (dataRows, separatorInData) = extractDataRows(resolved, headerRowIndex, separatorIndices)
        val maxCols = maxOf(
            headerCells.size,
            dataRows.maxOfOrNull { it.size } ?: 0
        )
        val headers = expandHeaders(headerCells, maxCols)

        return DiscoveryResult(
            headerRowIndex = headerRowIndex,
            headers = headers,
            dataRows = dataRows,
            separatorRowIndicesInData = separatorInData.toSet(),
            maxColumnCount = maxCols
        )
    }

    private fun findHeaderRow(rows: List<List<Any?>>): Int {
        if (rows.isEmpty()) return 0
        if (rows.size == 1) return 0

        // Density: non-empty cell count per row
        val densities = rows.map { row ->
            row.count { (it?.toString()?.trim() ?: "").isNotEmpty() }
        }

        // Type continuity: header row is usually all/mostly text; rows below have numbers/dates
        val typeContinuityScores = rows.mapIndexed { idx, row ->
            if (idx >= rows.size - 1) 0
            else {
                val nextRow = rows[idx + 1]
                val headerTypes = row.map { cellType(it) }
                val dataTypes = nextRow.map { cellType(it) }
                val differentTypeCount = headerTypes.zip(dataTypes).count { (h, d) ->
                    h == "text" && d in listOf("number", "date", "currency", "boolean")
                }
                differentTypeCount
            }
        }

        var bestRow = 0
        var bestScore = -1

        for (i in rows.indices) {
            val density = densities.getOrElse(i) { 0 }
            val typeScore = typeContinuityScores.getOrElse(i) { 0 }

            // Skip separator-like rows (very low density)
            if (density <= SEPARATOR_MAX_FILL && i > 0) continue

            // Combined score: density is primary, type continuity boosts
            val score = density * 2 + typeScore
            if (score > bestScore && density >= MIN_DENSITY_FOR_HEADER) {
                bestScore = score
                bestRow = i
            }
        }

        // Fallback: first row with any reasonable density
        if (bestScore < 0) {
            for (i in rows.indices) {
                if (densities.getOrElse(i) { 0 } >= 1) return i
            }
        }
        return bestRow
    }

    private fun cellType(cell: Any?): String {
        val s = cell?.toString()?.trim().orEmpty()
        if (s.isEmpty()) return "empty"
        if (s.lowercase() in setOf("true", "false", "yes", "no", "1", "0")) return "boolean"
        if (Regex("""^-?\d+(\.\d+)?$""").matches(s)) return "number"
        if (Regex("""^\d{4}-\d{2}-\d{2}""").containsMatchIn(s) ||
            Regex("""\d{1,2}/\d{1,2}/\d{2,4}""").containsMatchIn(s)
        ) return "date"
        if (Regex("""^[$€£¥₹]?\s*-?\d""").containsMatchIn(s)) return "currency"
        return "text"
    }

    private fun identifySeparatorRows(rows: List<List<Any?>>, headerRowIndex: Int): Set<Int> {
        val result = mutableSetOf<Int>()
        for (i in rows.indices) {
            if (i == headerRowIndex) continue
            val nonEmpty = rows[i].count { (it?.toString()?.trim() ?: "").isNotEmpty() }
            val span = rows[i].size
            if (nonEmpty <= SEPARATOR_MAX_FILL && span > 2) {
                result.add(i)
            }
        }
        return result
    }

    private fun extractHeadersWithMerges(
        rows: List<List<Any?>>,
        mergeRanges: List<MergeRange>,
        headerRowIndex: Int
    ): List<String> {
        val headerRow = rows.getOrNull(headerRowIndex) ?: return listOf("Column A")
        val maxCol = rows.maxOfOrNull { it.size } ?: 1

        return (0 until maxCol).map { col ->
            val cellStr = headerRow.getOrElse(col) { null }?.toString()?.trim().orEmpty()
            if (cellStr.isNotEmpty()) {
                cellStr
            } else {
                val (pr, pc) = MergedCellResolver.getPrimaryCell(mergeRanges, headerRowIndex, col)
                val primaryCell = rows.getOrNull(pr)?.getOrElse(pc) { null }?.toString()?.trim()
                primaryCell?.takeIf { it.isNotEmpty() } ?: ""
            }
        }
    }

    private fun expandHeaders(headerCells: List<String>, maxCols: Int): List<String> {
        return (0 until maxCols).map { i ->
            val h = headerCells.getOrElse(i) { "" }.trim()
            if (h.isNotEmpty()) h else "Untitled Column ${columnLetter(i)}"
        }
    }

    private fun columnLetter(index: Int): String {
        var i = index
        val sb = StringBuilder()
        while (i >= 0) {
            sb.insert(0, ('A' + i % 26))
            i = i / 26 - 1
        }
        return sb.toString()
    }

    private fun extractDataRows(
        resolvedRows: List<List<Any?>>,
        headerRowIndex: Int,
        separatorIndices: Set<Int>
    ): Pair<List<List<Any?>>, List<Int>> {
        val dataRows = mutableListOf<List<Any?>>()
        val separatorInData = mutableListOf<Int>()

        for (i in (headerRowIndex + 1) until resolvedRows.size) {
            val row = resolvedRows[i]
            if (separatorIndices.contains(i)) {
                separatorInData.add(dataRows.size)
                dataRows.add(row)
            } else {
                dataRows.add(row)
            }
        }
        return dataRows to separatorInData
    }

    private fun resolveMergedCellValues(
        rawRows: List<List<Any?>>,
        mergeRanges: List<MergeRange>
    ): List<List<Any?>> {
        if (mergeRanges.isEmpty()) return rawRows
        return rawRows.mapIndexed { rowIdx, row ->
            row.mapIndexed { colIdx, cell ->
                val cellStr = cell?.toString()?.trim().orEmpty()
                if (cellStr.isEmpty()) {
                    val (pr, pc) = MergedCellResolver.getPrimaryCell(mergeRanges, rowIdx, colIdx)
                    if (pr != rowIdx || pc != colIdx) {
                        rawRows.getOrNull(pr)?.getOrNull(pc)
                    } else cell
                } else cell
            }
        }
    }
}
