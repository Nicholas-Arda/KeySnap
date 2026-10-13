package com.example.service.adb;

import android.Manifest;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import java.io.File;

/** Read-only phone checks after turning Advanced Mode off; invoke with verifyStoppedAutostart=true. */
public final class AdvancedAutostartStateInstrumentation extends Instrumentation {
    @Override
    public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        if (arguments == null || !"true".equals(arguments.getString("verifyStoppedAutostart"))) {
            finish(Activity.RESULT_CANCELED, new Bundle());
            return;
        }
        start();
    }

    @Override
    public void onStart() {
        Bundle result = new Bundle();
        try {
            Context context = getTargetContext();
            if (!"io.github.nicholasarda.keysnap.dev".equals(context.getPackageName())) {
                throw new IllegalStateException();
            }
            SharedPreferences prefs = context.getSharedPreferences("arda_mapper_adb_prefs", Context.MODE_PRIVATE);
            boolean wanted = prefs.getBoolean("advanced_mode_enabled", false);
            boolean owned = prefs.getBoolean("advanced_autostart_enabled_wifi_debugging", false);
            boolean permission = context.checkSelfPermission(Manifest.permission.WRITE_SECURE_SETTINGS)
                    == PackageManager.PERMISSION_GRANTED;
            JobScheduler scheduler = context.getSystemService(JobScheduler.class);
            boolean noJobs = scheduler != null && scheduler.getPendingJob(0x4B535202) == null
                    && scheduler.getPendingJob(0x4B535203) == null;
            File external = context.getExternalFilesDir(null);
            File[] payload = external == null ? null : new File(external, "bridge").listFiles();
            boolean noPayload = external != null && (payload == null || payload.length == 0);
            // Report only non-sensitive state; never identity, endpoint, or control-token values.
            result.putBoolean("advanced_mode_wanted", wanted);
            result.putBoolean("autostart_wifi_owned", owned);
            result.putBoolean("restart_permission_held", permission);
            result.putBoolean("no_autostart_jobs", noJobs);
            result.putBoolean("no_bridge_payload", noPayload);
            result.putBoolean("wireless_debugging_enabled",
                    Settings.Global.getInt(context.getContentResolver(), "adb_wifi_enabled", 0) == 1);
            boolean passed = !wanted && !owned && !permission && noJobs && noPayload;
            result.putBoolean("checks_passed", passed);
            finish(passed ? Activity.RESULT_OK : Activity.RESULT_CANCELED, result);
        } catch (Exception error) {
            result.putString("probe_error", error.getClass().getSimpleName());
            finish(Activity.RESULT_CANCELED, result);
        }
    }
}
