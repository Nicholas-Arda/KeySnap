package com.example.bridge;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class RingerControllerTest {

    @Test
    public void cycleOrderMatchesTheAppSide() {
        assertEquals(RingerController.MODE_VIBRATE, RingerController.next(RingerController.MODE_NORMAL));
        assertEquals(RingerController.MODE_SILENT, RingerController.next(RingerController.MODE_VIBRATE));
        assertEquals(RingerController.MODE_NORMAL, RingerController.next(RingerController.MODE_SILENT));
    }

    @Test
    public void unknownModeNameIsRejected() {
        assertEquals(RingerController.MODE_SILENT, RingerController.modeFor("silent"));
        assertEquals(-1, RingerController.modeFor("loud"));
        assertEquals(-1, RingerController.modeFor(null));
    }
}
