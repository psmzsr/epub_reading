package com.example.epubreader.presentation.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.epubreader.domain.model.Book
import com.example.epubreader.presentation.ui.components.BookCard
import com.example.epubreader.presentation.ui.components.EmptyLibraryContent
import com.example.epubreader.presentation.ui.components.LoadingContent
import com.example.epubreader.presentation.viewmodel.LibraryViewModel

/** 书架页：导入 EPUB、展示书单、删除书籍。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onBookClick: (Book) -> Unit,
    viewModel: LibraryViewModel = viewModel()
) {
    val tag = "EpubDebug"
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val mimeTypes = remember {
        arrayOf("application/epub+zip", "application/octet-stream", "*/*")
    }

    var showDeleteDialog by remember { mutableStateOf<Book?>(null) }

    /** 统一处理文件选择回调，兼容持久权限与普通权限。 */
    fun handlePickedUri(uri: Uri, persistPermission: Boolean) {
        Log.i(tag, "file picked: uri=$uri, persist=$persistPermission")

        if (persistPermission) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }.onFailure { error ->
                Log.w(tag, "takePersistableUriPermission failed: ${error.message}")
            }
        }

        val fileSize = context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            val index = cursor.getColumnIndex(OpenableColumns.SIZE)
            if (cursor.moveToFirst() && index >= 0) cursor.getLong(index) else 0L
        } ?: 0L

        Log.i(tag, "file size = $fileSize")
        Toast.makeText(context, "开始导入 EPUB", Toast.LENGTH_SHORT).show()
        viewModel.importBook(uri, fileSize)
    }

    val getContentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri == null) {
            Log.i(tag, "GetContent callback: uri=null")
            Toast.makeText(context, "未选择文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        handlePickedUri(uri, persistPermission = false)
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) {
            Log.i(tag, "OpenDocument callback: uri=null")
            Toast.makeText(context, "未选择文件", Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        handlePickedUri(uri, persistPermission = true)
    }

    /**
     * launchPicker 方法。 
     */
    fun launchPicker() {
        runCatching {
            openDocumentLauncher.launch(mimeTypes)
        }.onFailure { error ->
            // 少数系统机型 OpenDocument 可能异常，回退到更宽松的 GetContent。
            Log.e(tag, "OpenDocument launch failed, fallback to GetContent", error)
            Toast.makeText(context, "系统文件选择器异常，尝试兼容模式", Toast.LENGTH_SHORT).show()
            getContentLauncher.launch("*/*")
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        Log.i(tag, "permission result: granted=$granted")
        if (granted) {
            launchPicker()
        } else {
            Toast.makeText(context, "未授予存储读取权限", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * pickEpub 方法。 
     */
    fun pickEpub() {
        Log.i(tag, "import button clicked, sdk=${Build.VERSION.SDK_INT}")
        Toast.makeText(context, "开始选择 EPUB 文件", Toast.LENGTH_SHORT).show()

        // Android 10 之前读取外部存储通常仍需运行时权限。
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            val permission = Manifest.permission.READ_EXTERNAL_STORAGE
            if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
                launchPicker()
            } else {
                permissionLauncher.launch(permission)
            }
        } else {
            launchPicker()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("书架") })
        },
        floatingActionButton = {
            FloatingActionButton(onClick = ::pickEpub) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "导入")
            }
        },
        snackbarHost = {
            SnackbarHost(hostState = snackbarHostState)
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                // 首屏加载态。
                uiState.isLoading -> LoadingContent(message = "正在加载书籍...")
                // 空书架态。
                uiState.isEmpty -> EmptyLibraryContent(onImportClick = ::pickEpub)
                else -> {
                    // 列表态。
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(items = uiState.books, key = { it.id }) { book ->
                            BookCard(
                                book = book,
                                onClick = { onBookClick(book) },
                                onLongClick = { showDeleteDialog = book }
                            )
                        }
                    }
                }
            }

            // 导入中的全屏 loading。
            if (uiState.isImporting) {
                LoadingContent(message = "正在导入...")
            }
        }
    }

    // 一次性错误消息。
    uiState.importError?.let { error ->
        LaunchedEffect(error) {
            snackbarHostState.showSnackbar(error)
            viewModel.resetImportState()
        }
    }

    // 一次性成功消息。
    if (uiState.importSuccess) {
        LaunchedEffect(uiState.importSuccess) {
            val title = uiState.lastImportedBook?.title.orEmpty()
            snackbarHostState.showSnackbar("已导入：$title")
            viewModel.resetImportState()
        }
    }

    // 长按删除确认弹窗。
    showDeleteDialog?.let { book ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("删除书籍") },
            text = { Text("确定删除《${book.title}》？此操作不可恢复。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteBook(book.id)
                        showDeleteDialog = null
                    }
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("取消")
                }
            }
        )
    }
}
