package dev.og69.eab.update

data class ReleaseInfo(
    val tagName: String,
    val normalizedVersion: String,
    val apkDownloadUrl: String,
    val apkSizeBytes: Long,
)
