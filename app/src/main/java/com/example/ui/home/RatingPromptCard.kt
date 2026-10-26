package com.example.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.unit.dp
import com.example.data.RatingPromptChoice
import com.example.ui.components.ConsolePanel
import com.example.ui.components.MinTouchTarget
import io.github.nicholasarda.keysnap.R

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RatingPromptCard(onChoice: (RatingPromptChoice) -> Unit) {
    ConsolePanel(
        label = stringResource(R.string.rating_prompt_title),
        testTag = "rating_prompt",
    ) {
        Text(
            text = stringResource(R.string.rating_prompt_body),
            style = MaterialTheme.typography.bodyMedium.copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Button(
                onClick = { onChoice(RatingPromptChoice.RATE) },
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.rating_prompt_rate))
            }
            TextButton(
                onClick = { onChoice(RatingPromptChoice.LATER) },
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.rating_prompt_later))
            }
            TextButton(
                onClick = { onChoice(RatingPromptChoice.NO_THANKS) },
                modifier = Modifier.heightIn(min = MinTouchTarget),
            ) {
                Text(stringResource(R.string.rating_prompt_no_thanks))
            }
        }
    }
}

/** The Play package stays fixed even in side-by-side .dev builds. No review SDK is involved. */
internal fun openKeySnapPlayListing(context: Context) {
    val playId = "io.github.nicholasarda.keysnap"
    fun open(uri: String) = context.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(uri)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
    )
    runCatching { open("market://details?id=$playId") }
        .onFailure { runCatching { open("https://play.google.com/store/apps/details?id=$playId") } }
}
