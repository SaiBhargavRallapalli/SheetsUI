package com.rsb.sheetsui.domain.util

/**
 * Maps merged ranges and resolves cell values to the primary (top-left) cell.
 * For merged cells, only the primary cell contains the value; others should display the primary's value.
 */
data class MergeRange(
    val startRowIndex: Int,
    val endRowIndex: Int,
    val startColumnIndex: Int,
    val endColumnIndex: Int
) {
    val primaryRow: Int get() = startRowIndex
    val primaryCol: Int get() = startColumnIndex

    fun contains(row: Int, col: Int): Boolean =
        row in startRowIndex until endRowIndex && col in startColumnIndex until endColumnIndex

    fun isPrimary(row: Int, col: Int): Boolean =
        row == startRowIndex && col == startColumnIndex
}

object MergedCellResolver {

    /**
     * Resolves which merge range (if any) contains the given cell.
     * @return The merge range, or null if the cell is not part of a merge.
     */
    fun getMergeContaining(merges: List<MergeRange>, row: Int, col: Int): MergeRange? =
        merges.firstOrNull { it.contains(row, col) }

    /**
     * Gets the primary (row, col) for a cell that may be part of a merge.
     * If the cell is in a merge, returns the top-left cell; otherwise returns (row, col).
     */
    fun getPrimaryCell(merges: List<MergeRange>, row: Int, col: Int): Pair<Int, Int> {
        val merge = getMergeContaining(merges, row, col)
        return if (merge != null) merge.primaryRow to merge.primaryCol else row to col
    }

    /**
     * True if (row, col) is part of a merged range and is NOT the primary cell.
     */
    fun isNonPrimaryMergedCell(merges: List<MergeRange>, row: Int, col: Int): Boolean {
        val merge = getMergeContaining(merges, row, col) ?: return false
        return !merge.isPrimary(row, col)
    }

    /**
     * True if (row, col) is part of any merged range.
     */
    fun isMergedCell(merges: List<MergeRange>, row: Int, col: Int): Boolean =
        getMergeContaining(merges, row, col) != null
}
