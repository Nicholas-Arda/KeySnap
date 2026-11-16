package com.example.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStyleTest {

    @Test fun roundTripsIncludingASeparatorInTheCustomName() {
        val style = WidgetStyle(WidgetColor.LIGHT, opacity = 40, showLabel = false, customLabel = "a|b", showIcon = false)
        assertEquals(style, WidgetStyle.decode(style.encode()))
    }

    @Test fun lookAndAccentRoundTrip() {
        val custom = WidgetStyle(look = WidgetLook.NOTHING, accent = 0xFFD34A8F.toInt(), customLabel = "x|y")
        assertEquals(custom, WidgetStyle.decode(custom.encode()))
        val auto = WidgetStyle(look = WidgetLook.HYPER_OS)
        assertEquals(auto, WidgetStyle.decode(auto.encode()))
    }

    @Test fun formRoundTripsAndAStyleSavedBeforeFormsIsATile() {
        val capsule = WidgetStyle(look = WidgetLook.PIXEL, form = WidgetForm.CAPSULE, customLabel = "a|b")
        assertEquals(capsule, WidgetStyle.decode(capsule.encode()))
        assertEquals(
            WidgetStyle(look = WidgetLook.NOTHING, opacity = 60, form = WidgetForm.TILE),
            WidgetStyle.decode("v2|NOTHING|auto|BLUE|60|true|true|"),
        )
        assertNull(WidgetStyle.decode("v3|NOPE|PIXEL|auto|BLUE|50|true|true|"))
    }

    @Test fun aStyleSavedBeforeLooksStaysClassic() {
        assertEquals(
            WidgetStyle(WidgetColor.DARK, opacity = 80, customLabel = "Torch"),
            WidgetStyle.decode("DARK|80|true|true|Torch"),
        )
    }

    @Test fun lookFollowsThePhoneMaker() {
        assertEquals(WidgetLook.ONE_UI, WidgetLook.forPhone("samsung"))
        assertEquals(WidgetLook.HYPER_OS, WidgetLook.forPhone("Xiaomi"))
        assertEquals(WidgetLook.NOTHING, WidgetLook.forPhone("Nothing"))
        assertEquals(WidgetLook.PIXEL, WidgetLook.forPhone("Google"))
        assertEquals(WidgetLook.PIXEL, WidgetLook.forPhone("OPPO"))
    }

    @Test fun nothingStoredOrGarbageMeansNoStyle() {
        assertNull(WidgetStyle.decode(null))
        assertNull(WidgetStyle.decode("PURPLE|50|true|true|"))
        assertNull(WidgetStyle.decode("BLUE|x|true|true|"))
        assertNull(WidgetStyle.decode("v2|NOPE|auto|BLUE|50|true|true|"))
    }

    @Test fun iconOnlyAtOneCellIconAndNameWiderNeverEmpty() {
        val style = WidgetStyle()
        assertEquals(true to false, style.parts(compact = true))
        assertEquals(true to true, style.parts(compact = false))
        assertEquals(true to false, style.copy(showLabel = false).parts(compact = false))
        assertEquals(false to true, style.copy(showIcon = false, showLabel = false).parts(compact = true))
    }
}
