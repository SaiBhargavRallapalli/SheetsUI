package com.rsb.sheetsui.domain.model

enum class FieldType {
    TEXT,
    NUMBER,
    CURRENCY,
    DATE,
    BOOLEAN,
    /** Column contains formulas; UI shows as read-only / highlighted by default. */
    FORMULA
}
