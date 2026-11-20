package com.example.service

import org.junit.Assert.assertEquals
import org.junit.Test

class FlashlightStrengthTest {
    @Test fun percentMapsOntoTheTorchLevels() {
        // PLG110 has 4 levels.
        assertEquals(1, FlashlightController.strengthLevel(1, 4))
        assertEquals(1, FlashlightController.strengthLevel(25, 4))
        assertEquals(2, FlashlightController.strengthLevel(50, 4))
        assertEquals(3, FlashlightController.strengthLevel(51, 4))
        assertEquals(4, FlashlightController.strengthLevel(100, 4))
        assertEquals(1, FlashlightController.strengthLevel(0, 4))
        assertEquals(4, FlashlightController.strengthLevel(500, 4))
    }
}
