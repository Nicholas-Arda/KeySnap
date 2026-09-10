package com.example.ui.home

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import io.github.nicholasarda.keysnap.R
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ads.AdConfig
import com.example.ui.components.ConsoleShape
import com.example.ui.components.InnerShape
import com.example.ui.components.MinTouchTarget
import com.google.android.gms.ads.AdListener
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.nativead.NativeAdView

private const val AD_ICON_TAG = "native_ad_icon"
private const val AD_HEADLINE_TAG = "native_ad_headline"
private const val AD_BODY_TAG = "native_ad_body"

/**
 * Fixed at the second slot of the suggestion list (see [SuggestedShortcutsPanel]) so it never
 * reflows as scripts are added or removed. Same row shape, icon-circle language and "pill" slot as
 * a real suggestion — the "AD" pill in [TileBadge]'s spot and the outer ring are what tell it apart.
 * The asset views live inside a real [NativeAdView] so AdMob's click/impression tracking sees them,
 * which rules out reading the ad's fields into plain Compose text.
 */
@Composable
fun NativeAdCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var nativeAd by remember { mutableStateOf<NativeAd?>(null) }
    val colors = MaterialTheme.colorScheme
    val onSurfaceArgb = colors.onSurface.toArgb()
    val onSurfaceVariantArgb = colors.onSurfaceVariant.toArgb()

    DisposableEffect(Unit) {
        val loader = AdLoader.Builder(context, AdConfig.NATIVE_UNIT_ID)
            .forNativeAd { ad -> nativeAd = ad }
            .withAdListener(object : AdListener() {})
            .build()
        loader.loadAd(AdRequest.Builder().build())
        onDispose { nativeAd?.destroy() }
    }

    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = MinTouchTarget)
            .clip(ConsoleShape)
            .background(colors.primary.copy(alpha = .06f))
            .border(1.dp, colors.primary.copy(alpha = .3f), ConsoleShape)
            .testTag("suggestion_native_ad"),
    ) {
        val ad = nativeAd
        if (ad == null) {
            AdPlaceholderRow()
        } else {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { ctx -> buildNativeAdView(ctx, onSurfaceArgb, onSurfaceVariantArgb) },
                update = { view -> bindNativeAd(view, ad) },
            )
        }
        AdPill(Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 12.dp))
    }
}

@Composable
private fun AdPlaceholderRow() {
    val colors = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(38.dp)
                .clip(InnerShape)
                .background(colors.primary.copy(alpha = .1f)),
        )
        Column(Modifier.padding(start = 12.dp, end = 40.dp)) {
            Text(stringResource(R.string.ad_loading), style = MaterialTheme.typography.bodyLarge, color = colors.onSurfaceVariant)
        }
    }
}

@Composable
private fun AdPill(modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Text(
        "AD",
        modifier
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = .16f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelSmall,
        color = colors.primary,
    )
}

private fun buildNativeAdView(context: Context, primaryTextColor: Int, secondaryTextColor: Int): NativeAdView {
    val dp = context.resources.displayMetrics.density
    fun Int.toPx() = (this * dp).toInt()

    val icon = ImageView(context).apply {
        tag = AD_ICON_TAG
        layoutParams = LinearLayout.LayoutParams(38.toPx(), 38.toPx())
    }
    val headline = TextView(context).apply {
        tag = AD_HEADLINE_TAG
        setTextColor(primaryTextColor)
        textSize = 16f
        setTypeface(typeface, Typeface.BOLD)
        maxLines = 1
    }
    val body = TextView(context).apply {
        tag = AD_BODY_TAG
        setTextColor(secondaryTextColor)
        textSize = 13f
        maxLines = 1
    }
    val textColumn = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
            marginStart = 12.toPx()
            marginEnd = 40.toPx()
        }
        addView(headline)
        addView(body)
    }
    val row = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setPadding(0, 8.toPx(), 0, 8.toPx())
        addView(icon)
        addView(textColumn)
    }
    return NativeAdView(context).apply {
        layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(row)
        iconView = icon
        headlineView = headline
        bodyView = body
        callToActionView = row
    }
}

private fun bindNativeAd(view: NativeAdView, ad: NativeAd) {
    val context = view.context
    (view.headlineView as TextView).text = ad.headline
    val bodyView = view.bodyView as TextView
    val bodyText = ad.body
    if (bodyText.isNullOrBlank()) {
        bodyView.text = context.getString(R.string.ad_sponsored)
    } else {
        bodyView.text = bodyText
    }
    val iconView = view.iconView as ImageView
    val icon = ad.icon
    if (icon != null) {
        iconView.setImageDrawable(icon.drawable)
    }
    view.setNativeAd(ad)
}
