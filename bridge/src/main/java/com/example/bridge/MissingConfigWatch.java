package com.example.bridge;

/**
 * Decides when a vanished config file means the app was uninstalled. Uninstall deletes the app's
 * external files dir without running any app code, so the file's absence is the only signal the
 * detached bridge gets. The app rewrites the config via temp+rename (or an in-place rewrite), so
 * the file is never absent during a normal update; an app update or force-stop leaves it in place.
 */
final class MissingConfigWatch {
    private final int graceMisses;
    private int misses;

    MissingConfigWatch(int graceMisses) {
        this.graceMisses = graceMisses;
    }

    /** Call once per poll; true when the file has been missing for more than the grace period. */
    boolean shouldShutDown(boolean fileExists) {
        if (fileExists) {
            misses = 0;
            return false;
        }
        return ++misses > graceMisses;
    }
}
