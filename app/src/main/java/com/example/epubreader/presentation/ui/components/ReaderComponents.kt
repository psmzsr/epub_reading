package com.example.epubreader.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun EpubContent(
    textContent: String,
    fontSize: Int,
    textColor: Color,
    backgroundColor: Color,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(horizontal = 20.dp, vertical = 24.dp)
    ) {
        Text(
            text = textContent.ifBlank { "当前页没有可显示内容" },
            style = TextStyle(
                color = textColor,
                fontSize = fontSize.sp,
                lineHeight = (fontSize * 1.8f).sp
            )
        )
    }
}
