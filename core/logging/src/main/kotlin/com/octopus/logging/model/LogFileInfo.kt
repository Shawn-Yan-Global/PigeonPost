package com.octopus.logging.model

data class LogFileInfo(
    val path: String,
    val size: Long,
    val lastModified: String,
    val exists: Boolean,
)
