package com.octopus.logging.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.sp
import com.octopus.logging.PigeonLogger
import com.octopus.logging.R
import com.octopus.logging.model.LogContent
import com.octopus.logging.model.LogFileInfo
import com.octopus.ui.spacing.AppSpacing

private const val TAG = "LogPreviewScreen"

@Composable
fun LogPreviewScreen(
    logFile: LogFileInfo,
    onBack: () -> Unit,
) {
    var logContent by remember { mutableStateOf<LogContent?>(null) }
    var currentPage by remember { mutableIntStateOf(0) }
    var isLoading by remember { mutableStateOf(false) }

    fun load(page: Int) {
        isLoading = true
        try {
            logContent = PigeonLogger.readLogContent(logFile.path, page, 100)
            currentPage = page
        } catch (e: Exception) {
            PigeonLogger.error("LogPreviewScreen", "Failed to load log content", e)
        } finally {
            isLoading = false
        }
    }

    LaunchedEffect(logFile.path) {
        load(0)
    }

    LogDetailScreen(
        logFile = logFile,
        logContent = logContent,
        currentPage = currentPage,
        isLoading = isLoading,
        onBack = onBack,
        onPageChanged = { newPage ->
            if (newPage >= 0) load(newPage)
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogDetailScreen(
    logFile: LogFileInfo,
    logContent: LogContent?,
    currentPage: Int,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPageChanged: (Int) -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        logFile.path.substringAfterLast("/"),
                        fontWeight = FontWeight.Bold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        PigeonLogger.debug(TAG, "LogPreviewScreen: Back button clicked")
                        onBack()
                    }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
            )
        },
    ) { paddingValues ->
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            // Pagination control
            if (logContent != null && logContent.totalPages > 1) {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(AppSpacing.small),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onPageChanged(currentPage - 1) },
                        enabled = currentPage > 0,
                    ) {
                        Text(stringResource(R.string.previous_page))
                    }

                    Text(
                        stringResource(
                            R.string.previous_pages_and_lines,
                            currentPage + 1, // %1$d
                            logContent.totalPages, // %2$d
                            logContent.totalLines, // %3$d
                        ),
                    )

                    Button(
                        onClick = { onPageChanged(currentPage + 1) },
                        enabled = currentPage < logContent.totalPages - 1,
                    ) {
                        Text(stringResource(R.string.next_page))
                    }
                }
            }

            // Log content
            Card(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = AppSpacing.large, vertical = AppSpacing.small),
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (logContent != null) {
                    SelectionContainer {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .verticalScroll(rememberScrollState())
                                    .padding(AppSpacing.large),
                        ) {
                            Text(
                                buildAnnotatedString {
                                    logContent.lines.forEach { line ->
                                        when {
                                            line.contains("ERROR") -> {
                                                withStyle(style = SpanStyle(color = Color.Red)) {
                                                    append(line)
                                                }
                                            }

                                            line.contains("WARN") -> {
                                                withStyle(style = SpanStyle(color = Color(0xFFFF9800))) {
                                                    append(line)
                                                }
                                            }

                                            line.contains("INFO") -> {
                                                withStyle(style = SpanStyle(color = Color.Blue)) {
                                                    append(line)
                                                }
                                            }

                                            line.contains("DEBUG") -> {
                                                withStyle(style = SpanStyle(color = Color.Gray)) {
                                                    append(line)
                                                }
                                            }

                                            else -> append(line)
                                        }
                                        append("\n")
                                    }
                                },
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                            )
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(stringResource(R.string.failed_to_load_log_content))
                    }
                }
            }
        }
    }
}
