package com.octopus.logging.model

data class LogContent(
    val lines: List<String>,
    val currentPage: Int,
    val totalPages: Int,
    val totalLines: Int,
)
