package com.rsb.sheetsui.domain.model

/**
 * Data validation rule for a column (dropdown list or checkbox from Google Sheets).
 */
sealed class ColumnValidation {
    /** Dropdown with fixed options (ONE_OF_LIST) */
    data class Dropdown(val options: List<String>) : ColumnValidation()

    /** Checkbox (BOOLEAN) */
    data object Checkbox : ColumnValidation()
}
