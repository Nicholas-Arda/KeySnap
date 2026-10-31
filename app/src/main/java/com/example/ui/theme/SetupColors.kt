package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
fun setupWarning(): Color = if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFFFFC56A) else Color(0xFF885000)

@Composable
fun setupSuccess(): Color = if (MaterialTheme.colorScheme.surface.luminance() < .5f) Color(0xFF81D9A4) else Color(0xFF206C40)
