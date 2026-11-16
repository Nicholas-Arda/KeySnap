package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R

/** The one place a "Pro" badge gets its text: always upper-case, translations stay as written. */
@Composable
fun proBadgeLabel(): String =
    stringResource(R.string.pro_table_pro).uppercase(LocalConfiguration.current.locales[0])
