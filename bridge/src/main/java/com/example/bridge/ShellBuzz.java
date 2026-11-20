package com.example.bridge;

/** Plays a {@link BuzzPattern#waveform} from the shell UID. */
final class ShellBuzz {

    private ShellBuzz() { }

    /**
     * [hold]: return only once the pattern has played, so the next action cannot replace it.
     * Without it the call returns as soon as the waveform starts (Android 11 still paces it here,
     * having only the oneshot command).
     */
    static void play(long[] timings, boolean hold) {
        if (timings.length == 0) return;
        String[] command = new String[6 + timings.length];
        System.arraycopy(new String[]{"cmd", "vibrator_manager", "synced", "-d", "arda_mapper", "waveform"}, 0, command, 0, 6);
        long total = 0;
        for (int i = 0; i < timings.length; i++) {
            command[6 + i] = Long.toString(timings[i]);
            total += timings[i];
        }
        try {
            if (ShellKeyInjector.runShell(command)) {
                if (hold) Thread.sleep(total);
                return;
            }
            for (int i = 0; i < timings.length; i += 2) {
                Thread.sleep(timings[i]);
                ShellKeyInjector.runShell("cmd", "vibrator", "vibrate", Long.toString(timings[i + 1]), "arda_mapper");
                Thread.sleep(timings[i + 1]);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
