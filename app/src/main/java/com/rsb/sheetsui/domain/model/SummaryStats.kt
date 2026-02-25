package com.rsb.sheetsui.domain.model

/**
 * Enterprise summary statistics for the sheet header.
 */
data class SummaryStats(
    val totalRecords: Int,
    val financialSum: Double?,
    val statusDistribution: List<StatusCount>
)

data class StatusCount(val label: String, val count: Int, val ratio: Float)
