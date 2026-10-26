package com.example.bridge;

import java.io.File;
import java.util.List;
import java.util.Map;

/**
 * Public-source build. Shortcut modes are a KeySnap Pro feature whose source is private;
 * the Pro build replaces this file (see {@code keysnap.edition} in bridge/build.gradle.kts).
 * A mode condition fails closed and a cycle reports failure.
 */
public final class BridgeModes {
    private BridgeModes() { }

    public static BridgeModes open(File configFile) {
        return new BridgeModes();
    }

    /** Negative: the cycle did not happen. */
    public int cycle(Map<String, String> params, ModeSignal signal) {
        return -1;
    }

    public boolean matches(Map<String, String> params) {
        return false;
    }

    public static long triggerPause(List<BridgeConfig.Action> actions, long hapticDurationMs) {
        return 0L;
    }

    public interface ModeSignal {
        void broadcast(int mode);
    }
}
