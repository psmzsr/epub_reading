package com.example.epubreader.presentation.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.epubreader.presentation.ui.screens.LibraryScreen
import com.example.epubreader.presentation.ui.screens.ReaderScreen

/** 页面路由定义。 */
sealed class Screen(val route: String) {
    data object Library : Screen("library")

    data object Reader : Screen("reader/{bookId}/{bookPath}/{bookTitle}") {
        /** 路由参数统一编码，避免书名/路径中的特殊字符破坏路由。 */
        fun createRoute(bookId: String, bookPath: String, bookTitle: String): String {
            return "reader/${Uri.encode(bookId)}/${Uri.encode(bookPath)}/${Uri.encode(bookTitle)}"
        }
    }
}

/** 应用主导航图。 */
@Composable
fun AppNavigation(
    navController: NavHostController
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Library.route
    ) {
        // 书架页：点击书籍后跳转到阅读页。
        composable(Screen.Library.route) {
            LibraryScreen(
                onBookClick = { book ->
                    navController.navigate(
                        Screen.Reader.createRoute(
                            bookId = book.id,
                            bookPath = book.filePath,
                            bookTitle = book.title
                        )
                    )
                }
            )
        }

        // 阅读页：从路由中恢复书籍上下文参数。
        composable(
            route = Screen.Reader.route,
            arguments = listOf(
                navArgument("bookId") { type = NavType.StringType },
                navArgument("bookPath") { type = NavType.StringType },
                navArgument("bookTitle") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val bookId = Uri.decode(backStackEntry.arguments?.getString("bookId") ?: "")
            val bookPath = Uri.decode(backStackEntry.arguments?.getString("bookPath") ?: "")
            val bookTitle = Uri.decode(backStackEntry.arguments?.getString("bookTitle") ?: "")

            ReaderScreen(
                bookId = bookId,
                bookPath = bookPath,
                bookTitle = bookTitle,
                onBack = { navController.popBackStack() }
            )
        }
    }
}
