package com.example.surface

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WidgetStyleTest {

    @Test fun roundTripsIncludingASeparatorInTheCustomName() {
        val style = WidgetStyle(WidgetColor.LIGHT, opacity = 40, showLabel = false, customLabel = "a|b", showIcon = false)
        assertEquals(style, WidgetStyle.decode(style.encode()))
    }

    @Test fun nothingStoredOrGarbageMeansNoStyle() {
        assertNull(WidgetStyle.decode(null))
        assertNull(WidgetStyle.decode("PURPLE|50|true|true|"))
        assertNull(WidgetStyle.decode("BLUE|x|true|true|"))
    }

    @Test fun iconOnlyAtOneCellIconAndNameWiderNeverEmpty() {
        val style = WidgetStyle()
        assertEquals(true to false, style.parts(compact = true))
        assertEquals(true to true, style.parts(compact = false))
        assertEquals(true to false, style.copy(showLabel = false).parts(compact = false))
        assertEquals(false to true, style.copy(showIcon = false, showLabel = false).parts(compact = true))
    }
}
