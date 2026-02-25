package com.rsb.sheetsui.domain.model

/**
 * Domain model representing a spreadsheet from Google Drive.
 */
data class Spreadsheet(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val createdTime: String? = null
)
