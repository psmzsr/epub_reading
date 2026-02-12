package com.example.epubreader.presentation.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.NavigateBefore
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.epubreader.domain.model.ReaderTheme
import com.example.epubreader.presentation.ui.components.EpubContent
import com.example.epubreader.presentation.ui.components.LoadingContent
import com.example.epubreader.presentation.viewmodel.ReaderViewModel

@Composable
fun ReaderScreen(
    bookId: String,
    bookPath: String,
    bookTitle: String,
    onBack: () -> Unit,
    viewModel: ReaderViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showSettingsPanel by remember { mutableStateOf(false) }
    var showChapterList by remember { mutableStateOf(false) }
    var showControls by remember { mutableStateOf(true) }

    LaunchedEffect(bookId, bookPath) {
        viewModel.openBook(bookPath, bookId)
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.saveProgress()
        }
    }

    val backgroundColor = Color(uiState.selectedTheme.backgroundColor)
    val textColor = Color(uiState.selectedTheme.textColor)
    val chapterScrollState = rememberScrollState()

    val chapterText = remember(
        uiState.readingState.currentChapter,
        uiState.readingState.totalChapters,
        uiState.fontSize
    ) {
        viewModel.getCurrentChapterContent()
    }

    LaunchedEffect(uiState.readingState.currentChapter, uiState.isLoading, chapterScrollState.maxValue) {
        if (uiState.isLoading) return@LaunchedEffect
        val progress = uiState.readingState.scrollPosition.coerceIn(0f, 1f)
        val targetOffset = (chapterScrollState.maxValue * progress).toInt()
        if (chapterScrollState.value != targetOffset) {
            chapterScrollState.scrollTo(targetOffset)
        }
    }

    LaunchedEffect(chapterScrollState.value, chapterScrollState.maxValue, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        val progress = if (chapterScrollState.maxValue <= 0) {
            0f
        } else {
            chapterScrollState.value.toFloat() / chapterScrollState.maxValue.toFloat()
        }
        viewModel.updateScrollPosition(progress)
    }

    val chapterProgress = if (chapterScrollState.maxValue <= 0) {
        0f
    } else {
        chapterScrollState.value.toFloat() / chapterScrollState.maxValue.toFloat()
    }

    Scaffold(
        containerColor = backgroundColor
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (uiState.isLoading) {
                LoadingContent(message = "正在加载书籍...")
            } else {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    if (showControls) {
                        ReaderTopBar(
                            title = bookTitle,
                            chapterInfo = uiState.readingState.chapterInfo,
                            onBack = onBack,
                            onSettings = { showSettingsPanel = true },
                            onChapterList = { showChapterList = true }
                        )
                    }

                    EpubContent(
                        textContent = chapterText.ifBlank { "当前章节内容加载中..." },
                        fontSize = uiState.fontSize,
                        textColor = textColor,
                        backgroundColor = backgroundColor,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(chapterScrollState)
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { showControls = !showControls })
                            }
                    )

                    if (showControls) {
                        ReaderBottomBar(
                            currentChapter = uiState.readingState.currentChapter,
                            totalChapters = uiState.readingState.totalChapters,
                            chapterProgress = chapterProgress,
                            onPreviousChapter = viewModel::previousChapter,
                            onNextChapter = viewModel::nextChapter,
                            backgroundColor = backgroundColor,
                            textColor = textColor
                        )
                    }
                }
            }

            if (showSettingsPanel) {
                SettingsPanel(
                    fontSize = uiState.fontSize,
                    isNightMode = uiState.isNightMode,
                    selectedTheme = uiState.selectedTheme,
                    onFontSizeChange = { delta ->
                        if (delta > 0) viewModel.increaseFontSize() else viewModel.decreaseFontSize()
                    },
                    onNightModeChange = viewModel::toggleNightMode,
                    onThemeSelect = viewModel::selectTheme,
                    onDismiss = { showSettingsPanel = false }
                )
            }

            if (showChapterList) {
                ChapterListDialog(
                    currentChapter = uiState.readingState.currentChapter,
                    chapterTitles = uiState.chapterTitles,
                    onChapterSelect = {
                        viewModel.goToChapter(it)
                        showChapterList = false
                    },
                    onDismiss = { showChapterList = false }
                )
            }
        }
    }
}

@Composable
private fun ReaderTopBar(
    title: String,
    chapterInfo: String,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onChapterList: () -> Unit
) {
    Surface(shadowElevation = 4.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "返回")
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1
                )

                IconButton(onClick = onChapterList) {
                    Icon(imageVector = Icons.Default.List, contentDescription = "目录")
                }

                IconButton(onClick = onSettings) {
                    Icon(imageVector = Icons.Default.Settings, contentDescription = "设置")
                }
            }

            Text(
                text = chapterInfo,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun ReaderBottomBar(
    currentChapter: Int,
    totalChapters: Int,
    chapterProgress: Float,
    onPreviousChapter: () -> Unit,
    onNextChapter: () -> Unit,
    backgroundColor: Color,
    textColor: Color
) {
    val canGoPrevious = currentChapter > 0
    val canGoNext = currentChapter < totalChapters - 1
    val progressPercent = (chapterProgress.coerceIn(0f, 1f) * 100).toInt()

    Surface(
        color = backgroundColor,
        shadowElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onPreviousChapter,
                enabled = canGoPrevious
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateBefore,
                    contentDescription = "上一章",
                    tint = if (canGoPrevious) textColor else textColor.copy(alpha = 0.3f)
                )
            }

            Text(
                text = "第 ${currentChapter + 1} 章 / 共 $totalChapters 章  ·  本章 $progressPercent%",
                style = MaterialTheme.typography.bodyMedium,
                color = textColor
            )

            IconButton(
                onClick = onNextChapter,
                enabled = canGoNext
            ) {
                Icon(
                    imageVector = Icons.Default.NavigateNext,
                    contentDescription = "下一章",
                    tint = if (canGoNext) textColor else textColor.copy(alpha = 0.3f)
                )
            }
        }
    }
}

@Composable
private fun SettingsPanel(
    fontSize: Int,
    isNightMode: Boolean,
    selectedTheme: ReaderTheme,
    onFontSizeChange: (Int) -> Unit,
    onNightModeChange: () -> Unit,
    onThemeSelect: (ReaderTheme) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("阅读设置") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("字体大小", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledIconButton(onClick = { onFontSizeChange(-1) }, enabled = fontSize > 12) {
                        Text("-", fontSize = 20.sp)
                    }

                    Text(
                        text = "${fontSize}sp",
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )

                    FilledIconButton(onClick = { onFontSizeChange(1) }, enabled = fontSize < 32) {
                        Text("+", fontSize = 20.sp)
                    }
                }

                Divider()

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("夜间模式", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = isNightMode, onCheckedChange = { onNightModeChange() })
                }

                Divider()
                Text("阅读主题", style = MaterialTheme.typography.titleSmall)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ReaderTheme.entries.forEach { theme ->
                        ThemeButton(
                            theme = theme,
                            isSelected = selectedTheme == theme,
                            onClick = { onThemeSelect(theme) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("确定")
            }
        }
    )
}

@Composable
private fun ThemeButton(
    theme: ReaderTheme,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(48.dp),
        shape = MaterialTheme.shapes.small,
        color = Color(theme.backgroundColor),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "文",
                color = Color(theme.textColor),
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun ChapterListDialog(
    currentChapter: Int,
    chapterTitles: List<String>,
    onChapterSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentChapter, chapterTitles.size) {
        if (chapterTitles.isEmpty()) return@LaunchedEffect
        val targetIndex = (currentChapter - 3).coerceIn(0, chapterTitles.lastIndex)
        listState.scrollToItem(targetIndex)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("目录") },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier.heightIn(max = 400.dp)
            ) {
                itemsIndexed(chapterTitles) { chapterIndex, chapterTitle ->
                    TextButton(
                        onClick = { onChapterSelect(chapterIndex) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = chapterTitle.ifBlank { "第 ${chapterIndex + 1} 章" },
                            color = if (chapterIndex == currentChapter) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            }
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}
