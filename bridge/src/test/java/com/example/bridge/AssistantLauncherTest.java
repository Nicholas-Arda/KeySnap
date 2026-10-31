package com.example.bridge;

import org.junit.Test;
import static org.junit.Assert.*;

public class AssistantLauncherTest {
    @Test public void externalAssistantCanOpenWithoutTheApp() {
        assertTrue(AssistantLauncher.canShow("com.google.android.googlequicksearchbox/com.google.android.voiceinteraction.GsaVoiceInteractionService"));
    }

    @Test public void neitherKeySnapInstallMayInvokeItself() {
        assertFalse(AssistantLauncher.canShow("io.github.nicholasarda.keysnap/com.example.assistant.KeySnapVoiceService"));
        assertFalse(AssistantLauncher.canShow("io.github.nicholasarda.keysnap.dev/com.example.assistant.KeySnapVoiceService"));
        assertFalse(AssistantLauncher.canShow(null));
        assertFalse(AssistantLauncher.canShow("null"));
        assertFalse(AssistantLauncher.canShow(""));
        assertFalse(AssistantLauncher.canShow("package/"));
    }
}
