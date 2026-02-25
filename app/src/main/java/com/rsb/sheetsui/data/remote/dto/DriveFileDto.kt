package com.rsb.sheetsui.data.remote.dto

import com.google.gson.annotations.SerializedName

/**
 * DTO for Google Drive API v3 files.list response.
 * @see <a href="https://developers.google.com/drive/api/reference/rest/v3/files">File resource</a>
 */
data class DriveFileListDto(
    @SerializedName("kind") val kind: String? = null,
    @SerializedName("nextPageToken") val nextPageToken: String? = null,
    @SerializedName("incompleteSearch") val incompleteSearch: Boolean? = null,
    @SerializedName("files") val files: List<DriveFileDto>? = null
)

data class DriveFileDto(
    @SerializedName("id") val id: String? = null,
    @SerializedName("name") val name: String? = null,
    @SerializedName("mimeType") val mimeType: String? = null,
    @SerializedName("createdTime") val createdTime: String? = null,
    @SerializedName("modifiedTime") val modifiedTime: String? = null,
    @SerializedName("webViewLink") val webViewLink: String? = null
)
