package com.example.epubreader.presentation.viewmodel

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.epubreader.EpubReaderApplication
import com.example.epubreader.domain.model.Book
import com.example.epubreader.domain.usecase.DeleteBookUseCase
import com.example.epubreader.domain.usecase.GetBooksUseCase
import com.example.epubreader.domain.usecase.ImportBookUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class LibraryViewModel(application: Application) : AndroidViewModel(application) {

    private val tag = "EpubDebug"
    private val container = (application as EpubReaderApplication).container
    private val importBookUseCase = ImportBookUseCase(
        container.epubRepository,
        container.bookRepository
    )
    private val getBooksUseCase = GetBooksUseCase(container.bookRepository)
    private val deleteBookUseCase = DeleteBookUseCase(container.bookRepository)

    private val _uiState = MutableStateFlow(LibraryUiState())
    val uiState: StateFlow<LibraryUiState> = _uiState.asStateFlow()

    init {
        loadBooks()
    }

    private fun loadBooks() {
        viewModelScope.launch {
            getBooksUseCase().collect { books ->
                Log.d(tag, "loadBooks update: size=${books.size}")
                _uiState.value = _uiState.value.copy(
                    books = books,
                    isLoading = false
                )
            }
        }
    }

    fun importBook(uri: Uri, fileSize: Long) {
        viewModelScope.launch {
            Log.d(tag, "importBook click: uri=$uri, size=$fileSize")
            _uiState.value = _uiState.value.copy(isImporting = true, importError = null)

            importBookUseCase(uri, fileSize)
                .onSuccess { book ->
                    Log.d(tag, "importBook success: id=${book.id}, title=${book.title}")
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importSuccess = true,
                        lastImportedBook = book
                    )
                }
                .onFailure { error ->
                    Log.e(tag, "importBook failed: ${error.message}", error)
                    _uiState.value = _uiState.value.copy(
                        isImporting = false,
                        importError = error.message ?: "导入失败"
                    )
                }
        }
    }

    fun deleteBook(bookId: String) {
        viewModelScope.launch {
            Log.d(tag, "deleteBook click: id=$bookId")
            runCatching { deleteBookUseCase(bookId) }
                .onFailure { Log.e(tag, "deleteBook failed: ${it.message}", it) }
        }
    }

    fun resetImportState() {
        _uiState.value = _uiState.value.copy(
            importSuccess = false,
            importError = null,
            lastImportedBook = null
        )
    }
}

data class LibraryUiState(
    val books: List<Book> = emptyList(),
    val isLoading: Boolean = true,
    val isImporting: Boolean = false,
    val importSuccess: Boolean = false,
    val importError: String? = null,
    val lastImportedBook: Book? = null
) {
    val isEmpty: Boolean
        get() = books.isEmpty() && !isLoading
}
