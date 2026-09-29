package com.octopus.logging.model

data class LogStats(
    val totalLines: Int,
    val debugCount: Int,
    val infoCount: Int,
    val warnCount: Int,
    val errorCount: Int,
)
