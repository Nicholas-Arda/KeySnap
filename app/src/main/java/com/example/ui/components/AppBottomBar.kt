package com.example.ui.components

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Home
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.draw.CacheDrawScope
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.example.ui.theme.GlassColors
import com.example.ui.theme.LocalGlass
import io.github.nicholasarda.keysnap.R
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.launch

private val BarMargin = 12.dp
private val BarWidth = 244.dp
private val BarHeight = 68.dp
private val FabSize = 58.dp

/** The +'s centre sits this far above the bar's top edge; the notch is drawn around the same centre. */
private val FabCenterY = (-2).dp
private val FabOverhang = FabSize / 2 - FabCenterY
private val NotchRadius = 36.dp

/** Each side's tab: half the bar less the notch's share. */
private val TabWidth = BarWidth / 2 - 46.dp
private val LensSize = 50.dp

/** The frost's blur: light enough that text under the bar still reads as soft shapes, as in CalShot. */
private val FrostRadius = 14.dp

/** How far past the bar the frost samples, so its edge blurs real page rather than clamped pixels. */
private val FrostBleed = 28.dp

/**
 * The soft blur around the bar and the +: the box it may reach into, how far past their outline it
 * stays at full strength before fading, the fade, and the blur itself.
 */
private val HaloReach = 36.dp
private val HaloGrow = 10.dp
private val HaloFade = 12.dp
private val HaloBlur = 12.dp

/**
 * From the top of the navigation-bar inset to the top of the raised +. Pages end and snackbars sit
 * clear of this.
 */
val BottomBarClearance = BarMargin + BarHeight + FabOverhang

// The lens's two edges run on separate springs: the one leading the move is stiff and overshoots,
// the trailing one is soft, so the lens stretches in flight and squashes as it lands.
private val LeadSpring = spring<Float>(dampingRatio = 0.58f, stiffness = 307f)
private val TrailSpring = spring<Float>(dampingRatio = 0.9f, stiffness = 122f)

private data class BottomNavDestination(
    @StringRes val labelRes: Int,
    val icon: ImageVector,
    val page: Int,
    val testTag: String,
)

private val HomeTab = BottomNavDestination(R.string.nav_home, Icons.Default.Home, 0, "bottom_nav_home")
private val ShortcutsTab = BottomNavDestination(R.string.nav_shortcuts, Icons.Default.Bolt, 1, "bottom_nav_scripts")

/** A tab's centre along the bar, measured from its start edge, in dp. */
private fun tabCenter(page: Int): Float =
    if (page == HomeTab.page) TabWidth.value / 2 else BarWidth.value - TabWidth.value / 2

/**
 * A compact notched capsule: Home, a raised + that starts a new shortcut from any page, and
 * Shortcuts. Icons only; each tab still names itself to TalkBack. The bar is frosted glass over
 * [pages], the pager recorded as it draws, and a glass lens sits behind the selected tab and stretches
 * across to the other one when the selection changes.
 */
@Composable
fun AppBottomBar(
    selectedPage: Int,
    onPageSelected: (Int) -> Unit,
    onNewShortcut: () -> Unit,
    pages: GraphicsLayer,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val glass = LocalGlass.current
    val frosted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val half = LensSize.value / 2
    val lensStart = remember { Animatable(tabCenter(selectedPage) - half) }
    val lensEnd = remember { Animatable(tabCenter(selectedPage) + half) }
    LaunchedEffect(selectedPage) {
        val center = tabCenter(selectedPage)
        val towardEnd = center > (lensStart.value + lensEnd.value) / 2
        launch { lensStart.animateTo(center - half, if (towardEnd) TrailSpring else LeadSpring) }
        launch { lensEnd.animateTo(center + half, if (towardEnd) LeadSpring else TrailSpring) }
    }
    // Tall enough to hold the +'s overhang, so the whole button sits inside the bar's touch bounds.
    Box(
        modifier
            .navigationBarsPadding()
            .padding(bottom = BarMargin)
            .size(BarWidth, BarHeight + FabOverhang),
    ) {
        if (frosted) BarHalo(pages)
        Box(Modifier.align(Alignment.BottomCenter).size(BarWidth, BarHeight).selectableGroup()) {
            Spacer(
                Modifier
                    .matchParentSize()
                    .shadow(12.dp, BarShape, ambientColor = glass.barShadow, spotColor = glass.barShadow),
            )
            if (frosted) BarFrost(pages)
            Spacer(
                Modifier
                    .matchParentSize()
                    // Android 11 has no blur, so there the bar is near opaque instead of frosted.
                    .background(if (frosted) glass.barFill else glass.barFill.copy(alpha = .85f), BarShape)
                    .drawWithCache {
                        val bar = (BarShape.createOutline(size, layoutDirection, this) as Outline.Generic).path
                        onDrawBehind {
                            // Light catching the glass along its top edge, notch included.
                            clipPath(bar) {
                                translate(top = 1.dp.toPx()) { drawPath(bar, glass.barHighlight, style = Stroke(1.dp.toPx())) }
                            }
                        }
                    }
                    .border(1.2.dp, glass.barRim, BarShape),
            )
            Spacer(Modifier.matchParentSize().drawBehind { drawLens(lensStart.value, lensEnd.value, glass) })
            BarTab(HomeTab, selectedPage == HomeTab.page, onPageSelected, Modifier.align(Alignment.CenterStart))
            BarTab(ShortcutsTab, selectedPage == ShortcutsTab.page, onPageSelected, Modifier.align(Alignment.CenterEnd))
        }
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .size(FabSize)
                .shadow(10.dp, CircleShape, ambientColor = colors.primary, spotColor = colors.primary)
                .clip(CircleShape)
                .background(colors.primary)
                .clickable(role = Role.Button, onClick = onNewShortcut)
                .testTag("new_script_button"),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Add, stringResource(R.string.scripts_new_shortcut), Modifier.size(30.dp), tint = colors.onPrimary)
        }
    }
}

/**
 * The page behind the bar, blurred and a little more saturated, clipped to the bar: frosted glass,
 * the way the mockup's backdrop-filter drew it. Nothing outside the bar is touched.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun BoxScope.BarFrost(pages: GraphicsLayer) {
    val density = LocalDensity.current
    val frost = remember(density) {
        val radius = with(density) { FrostRadius.toPx() }
        val blur = android.graphics.RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP)
        val saturate = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(1.5f) })
        android.graphics.RenderEffect.createColorFilterEffect(saturate, blur).asComposeRenderEffect()
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Spacer(
        Modifier
            .align(Alignment.Center)
            .requiredSize(BarWidth + FrostBleed * 2, BarHeight + FrostBleed * 2)
            .onGloballyPositioned { origin = it.positionInRoot() }
            // Clipped after the blur, so the bar's edge shows page blurred across it, not a hard rim.
            .graphicsLayer {
                shape = FrostShape
                clip = true
            }
            .graphicsLayer { renderEffect = frost }
            .drawBehind { translate(-origin.x, -origin.y) { drawLayer(pages) } },
    )
}

/**
 * A light blur just around the bar and the +, fading back to a sharp page a short way out. The fade
 * follows their outline, so the halo hugs the capsule instead of ending in a line.
 */
@RequiresApi(Build.VERSION_CODES.S)
@Composable
private fun BoxScope.BarHalo(pages: GraphicsLayer) {
    val density = LocalDensity.current
    val blur = remember(density) {
        val radius = with(density) { HaloBlur.toPx() }
        BlurEffect(radius, radius, TileMode.Clamp)
    }
    var origin by remember { mutableStateOf(Offset.Zero) }
    Spacer(
        Modifier
            .align(Alignment.Center)
            .requiredSize(BarWidth + HaloReach * 2, BarHeight + FabOverhang + HaloReach * 2)
            .onGloballyPositioned { origin = it.positionInRoot() }
            // The fade masks the blurred page, so it needs a layer of its own to mask into.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val mask = haloMask()
                onDrawWithContent {
                    drawContent()
                    drawImage(mask, blendMode = BlendMode.DstIn)
                }
            }
            .graphicsLayer { renderEffect = blur }
            .drawBehind { translate(-origin.x, -origin.y) { drawLayer(pages) } },
    )
}

/**
 * The halo's alpha: the bar and the + drawn as one silhouette and blurred. It covers the whole box,
 * so masking with it leaves nothing untouched at the corners.
 */
private fun CacheDrawScope.haloMask(): ImageBitmap {
    val reach = HaloReach.toPx()
    val grow = HaloGrow.toPx()
    val barTop = reach + FabOverhang.toPx()
    val end = BarHeight.toPx() / 2 + grow
    val silhouette = android.graphics.Path().apply {
        addRoundRect(
            reach - grow,
            barTop - grow,
            size.width - reach + grow,
            barTop + BarHeight.toPx() + grow,
            end,
            end,
            android.graphics.Path.Direction.CW,
        )
        addCircle(size.width / 2, reach + FabSize.toPx() / 2, FabSize.toPx() / 2 + grow, android.graphics.Path.Direction.CW)
    }
    val bitmap = Bitmap.createBitmap(size.width.toInt(), size.height.toInt(), Bitmap.Config.ALPHA_8)
    android.graphics.Canvas(bitmap).drawPath(
        silhouette,
        android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
            maskFilter = BlurMaskFilter(HaloFade.toPx(), BlurMaskFilter.Blur.NORMAL)
        },
    )
    return bitmap.asImageBitmap()
}

@Composable
private fun BarTab(
    destination: BottomNavDestination,
    selected: Boolean,
    onPageSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        if (selected) colors.primary else colors.onSurfaceVariant,
        tween(150, easing = FastOutSlowInEasing),
        label = "tab${destination.page}Tint",
    )
    // The lens is the selection cue, so a press squeezes the icon instead of drawing a ripple.
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) .84f else 1f,
        spring(dampingRatio = .5f, stiffness = 600f),
        label = "tab${destination.page}Press",
    )
    Box(
        modifier
            .size(TabWidth, BarHeight)
            .selectable(
                selected = selected,
                interactionSource = interaction,
                indication = null,
                role = Role.Tab,
                onClick = { onPageSelected(destination.page) },
            )
            .testTag(destination.testTag),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            destination.icon,
            contentDescription = stringResource(destination.labelRes),
            modifier = Modifier.size(26.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            tint = tint,
        )
    }
}

/**
 * The glass lens between its two edges, in dp from the bar's start edge. Pulled apart it thins a
 * little, the way a drop of water does.
 */
private fun DrawScope.drawLens(start: Float, end: Float, glass: GlassColors) {
    val width = maxOf(40f, end - start)
    val height = LensSize.value - minOf(10f, abs(width - LensSize.value) * .07f)
    val fromStart = (start + end - width) / 2
    val x = if (layoutDirection == LayoutDirection.Rtl) BarWidth.value - fromStart - width else fromStart
    val topLeft = Offset(x.dp.toPx(), ((BarHeight.value - height) / 2).dp.toPx())
    val lensSize = Size(width.dp.toPx(), height.dp.toPx())
    val radius = CornerRadius(lensSize.height / 2)
    drawRoundRect(glass.lensFill, topLeft, lensSize, radius)
    // A bright crescent along the top inside edge, where light would catch the glass.
    val lens = Path().apply { addRoundRect(RoundRect(Rect(topLeft, lensSize), radius)) }
    clipPath(lens) {
        translate(top = 1.dp.toPx()) {
            drawRoundRect(glass.lensHighlight, topLeft, lensSize, radius, style = Stroke(1.dp.toPx()))
        }
    }
    drawRoundRect(glass.lensRim, topLeft, lensSize, radius, style = Stroke(1.dp.toPx()))
}

/**
 * A pill whose top edge dips around the +, with quadratic shoulders tangent to the notch so the edge
 * never kinks. [inset] draws it that far inside the given size, for the frost's oversized layer.
 */
private class NotchedBarShape(private val inset: Dp = 0.dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val pad = with(density) { inset.toPx() }
        val width = size.width - pad * 2
        val height = size.height - pad * 2
        val end = height / 2
        val cx = width / 2
        val notch = with(density) { NotchRadius.toPx() }
        val cy = with(density) { FabCenterY.toPx() }
        val shoulder = with(density) { 14.dp.toPx() }
        // Where the notch leaves the circle: sixty degrees either side of straight down.
        val angle = PI / 3
        val px = notch * sin(angle).toFloat()
        val py = cy + notch * cos(angle).toFloat()
        val qx = px + py / sin(angle).toFloat() * cos(angle).toFloat()
        return Outline.Generic(
            Path().apply {
                moveTo(end, 0f)
                lineTo(cx - qx - shoulder, 0f)
                quadraticBezierTo(cx - qx, 0f, cx - px, py)
                arcTo(Rect(Offset(cx, cy), notch), 150f, -120f, false)
                quadraticBezierTo(cx + qx, 0f, cx + qx + shoulder, 0f)
                lineTo(width - end, 0f)
                arcTo(Rect(Offset(width - end, end), end), -90f, 180f, false)
                lineTo(end, height)
                arcTo(Rect(Offset(end, end), end), 90f, 180f, false)
                close()
                translate(Offset(pad, pad))
            },
        )
    }
}

private val BarShape = NotchedBarShape()
private val FrostShape = NotchedBarShape(FrostBleed)
