package com.rsb.sheetsui.domain.model

/**
 * Represents a sheet tab in a spreadsheet.
 */
data class SheetTab(
    val sheetId: Int,
    val title: String,
    val index: Int
)
