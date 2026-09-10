package com.example.ui.icon

import java.io.File
import javax.imageio.ImageIO
import kotlin.math.hypot
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A launcher masks an adaptive icon to a shape of its own choosing, so only the central 66dp
 * circle of the 108dp canvas is guaranteed to survive on every device. The source art fills 80%
 * of its own square, which would be clipped, so the foreground places the glyph smaller on a
 * larger canvas — and this test is what keeps it placed correctly. It reads the PNG off disk
 * rather than through resources so it fails on the file someone replaces, not on a stale build.
 */
class LauncherIconSafeZoneTest {

    private val foreground = File("src/main/res/drawable-nodpi/ic_launcher_foreground.png")

    /** The 66dp circle, expressed in the 108dp canvas the drawable is authored on. */
    private val safeRadius = 33.0
    private val centre = 54.0

    @Test fun theGlyphStaysInsideTheSixtySixDpCircle() {
        val (box, canvas) = opaqueBounds()
        val corners = listOf(box[0] to box[1], box[2] to box[1], box[0] to box[3], box[2] to box[3])
        val worst = corners.maxOf { (x, y) ->
            hypot(x / canvas * 108 - centre, y / canvas * 108 - centre)
        }
        assertTrue(
            "the glyph reaches ${"%.2f".format(worst)}dp from centre, past the ${safeRadius}dp " +
                "a launcher mask is guaranteed to show",
            worst <= safeRadius,
        )
    }

    @Test fun theGlyphIsNotSoSmallItWastesTheSafeZone() {
        // The other direction: art shrunk to nothing would pass the clipping test too.
        val (box, canvas) = opaqueBounds()
        val width = (box[2] - box[0]) / canvas * 108
        assertTrue("the glyph is only ${"%.1f".format(width)}dp wide", width >= safeRadius * 2 * 0.8)
    }

    /** Returns [minX, minY, maxX, maxY] of the non-transparent pixels, and the canvas size. */
    private fun opaqueBounds(): Pair<DoubleArray, Double> {
        val image = ImageIO.read(foreground)
        require(image.width == image.height) { "the foreground canvas must be square" }
        var minX = image.width; var minY = image.height; var maxX = -1; var maxY = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                // Anything under 8/255 is antialiasing fringe, not drawn content.
                if ((image.getRGB(x, y) ushr 24) < 8) continue
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
        require(maxX >= 0) { "the foreground is entirely transparent" }
        return doubleArrayOf(minX.toDouble(), minY.toDouble(), maxX + 1.0, maxY + 1.0) to
            image.width.toDouble()
    }
}
