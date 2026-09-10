package com.example.bridge;

import android.content.Context;
import android.hardware.camera2.CameraManager;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Torch control from the shell UID.
 *
 * <p>Verified on ColorOS 16 / Android 16: {@code setTorchMode} succeeds for uid 2000, but
 * {@code getCameraIdList}/{@code getCameraCharacteristics} throw SecurityException because the
 * process has no real package identity. Torch-capable camera ids are therefore discovered from
 * {@link CameraManager.TorchCallback}, which reports every torch-capable id on registration,
 * instead of by querying characteristics.
 */
public final class TorchController {

    private final Object lock = new Object();
    private final Set<String> torchCameraIds = new LinkedHashSet<>();
    private CameraManager cameraManager;
    private volatile boolean torchOn;
    private String preferredId;

    /** Must be called on a thread with a prepared, running Looper so callbacks are delivered. */
    public void initialize(Context context) {
        try {
            cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        } catch (Throwable t) {
            BridgeLog.w("Torch: camera service unavailable: " + t);
            return;
        }
        if (cameraManager == null) {
            BridgeLog.w("Torch: camera service unavailable");
            return;
        }
        try {
            cameraManager.registerTorchCallback(new CameraManager.TorchCallback() {
                @Override
                public void onTorchModeChanged(String cameraId, boolean enabled) {
                    synchronized (lock) {
                        torchCameraIds.add(cameraId);
                        if (preferredId == null) {
                            preferredId = cameraId;
                        }
                        if (cameraId.equals(preferredId)) {
                            torchOn = enabled;
                        }
                    }
                }

                @Override
                public void onTorchModeUnavailable(String cameraId) {
                    synchronized (lock) {
                        torchCameraIds.remove(cameraId);
                        if (cameraId.equals(preferredId)) {
                            preferredId = torchCameraIds.isEmpty() ? null : torchCameraIds.iterator().next();
                        }
                    }
                }
            }, null);
        } catch (Throwable t) {
            BridgeLog.w("Torch: registerTorchCallback failed, falling back to camera id 0: " + t);
        }
        synchronized (lock) {
            if (preferredId == null) {
                preferredId = "0";
            }
        }
        BridgeLog.i("Torch ready, cameraId=" + preferredId + " known=" + torchCameraIds);
    }

    public boolean isOn() {
        return torchOn;
    }

    public boolean toggle() {
        return set(!torchOn);
    }

    public boolean set(boolean enabled) {
        CameraManager manager = cameraManager;
        if (manager == null) {
            BridgeLog.w("Torch: no camera manager");
            return false;
        }
        String primary;
        String[] candidates;
        synchronized (lock) {
            primary = preferredId != null ? preferredId : "0";
            candidates = torchCameraIds.toArray(new String[0]);
        }
        if (trySet(manager, primary, enabled)) {
            return true;
        }
        // A specific id can become unavailable while another camera client holds it.
        for (String id : candidates) {
            if (!id.equals(primary) && trySet(manager, id, enabled)) {
                synchronized (lock) {
                    preferredId = id;
                }
                return true;
            }
        }
        BridgeLog.w("Torch: no camera id accepted setTorchMode(" + enabled + ")");
        return false;
    }

    private boolean trySet(CameraManager manager, String cameraId, boolean enabled) {
        try {
            manager.setTorchMode(cameraId, enabled);
            torchOn = enabled;
            return true;
        } catch (Throwable t) {
            BridgeLog.w("Torch: setTorchMode(" + cameraId + ", " + enabled + ") failed: " + t);
            return false;
        }
    }
}
