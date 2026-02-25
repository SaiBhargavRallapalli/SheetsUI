package com.rsb.sheetsui.domain.model

/**
 * Domain model representing a Google Form from the user's Drive.
 */
data class Form(
    val id: String,
    val name: String,
    val modifiedTime: String? = null,
    val createdTime: String? = null
) {
    val editUrl: String get() = "https://docs.google.com/forms/d/$id/edit"
    val responderUrl: String get() = "https://docs.google.com/forms/d/$id/viewform"
}
