package com.xperia.cameraxposed;

import android.os.Build;
import android.provider.Settings;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import java.util.HashMap;
import java.util.Map;

public class SonyCameraHook implements IXposedHookLoadPackage {

    private static final String TAG = "SonyCamXposed";
    private static final String MODULE_PKG = "com.xperia.cameraxposed";
    private static final String[] SONY_APPS = {
            "com.sonymobile.photopro", "jp.co.sony.mc.videopro",
            "jp.co.sony.mc.camera.app", "jp.co.sony.mc.cameraapq"
    };

    // ─── Device detection ────────────────────────────────────────────────────
    //
    // The Camera2 vendor tag key constructor takes a Class<?> that determines
    // the HAL wire type.  Xperia 1 V (PDX234) uses TYPE_BYTE (Byte.class).
    // Xperia 1 IV (PDX223 / SOG06 / SO-51C) uses TYPE_INT32 (Integer.TYPE).
    // Using the wrong type causes the HAL to silently drop or misparse the tag.

    private static class DeviceConfig {
        final String name;
        final Class<?> keyType;
        DeviceConfig(String name, Class<?> keyType) {
            this.name  = name;
            this.keyType = keyType;
        }
    }

    private static final DeviceConfig XPERIA_1V  = new DeviceConfig("Xperia 1 V",  Byte.class);
    private static final DeviceConfig XPERIA_1IV = new DeviceConfig("Xperia 1 IV", Integer.TYPE);

    private static final DeviceConfig DEVICE;
    static {
        String d = Build.DEVICE != null ? Build.DEVICE.toLowerCase(java.util.Locale.ROOT) : "";
        // 1 IV: global (pdx223), KDDI Japan (sog06), docomo Japan (so-51c)
        DEVICE = (d.equals("pdx223") || d.equals("sog06") || d.equals("so-51c"))
                ? XPERIA_1IV : XPERIA_1V;
    }

    // ─── Tag tables ──────────────────────────────────────────────────────────
    // {prefKey, vendorTagName, defaultValue, type("list"|"switch")}
    private static final String[][] TAG_DEFS = {
            {"colorToneProfile",         "com.sonymobile.control.colorToneProfile",             "0", "list"},
            {"cinemaProfile",            "com.sonymobile.control.cinemaProfile",                 "0", "list"},
            {"stillHdrMode",             "com.sonymobile.control.stillHdrMode",                  "0", "list"},
            {"multiFrameNrMode",         "com.sonymobile.control.multiFrameNrMode",              "1", "switch"},
            {"highQualitySnapshotMode",  "com.sonymobile.control.highQualitySnapshotMode",       "0", "switch"},
            {"eyeDetectMode",            "com.sonymobile.statistics.eyeDetectMode",               "1", "list"},
            {"sceneDetectMode",          "com.sonymobile.statistics.sceneDetectMode",             "1", "switch"},
            {"conditionDetectMode",      "com.sonymobile.statistics.conditionDetectMode",         "1", "switch"},
            {"videoMultiFrameHdrMode",   "com.sonymobile.control.videoMultiFrameHdrMode",        "0", "switch"},
            {"videoSensitivitySmoothing","com.sonymobile.control.videoSensitivitySmoothingMode",  "1", "switch"},
            {"videoStabilizationMode",   "com.sonymobile.control.videoStabilizationMode",        "0", "list"},
            {"superResolutionZoomMode",  "com.sonymobile.scaler.superResolutionZoomMode",        "0", "list"},
    };

    // Tags that are always injected regardless of user settings.
    // These are required by Sony's EXCAL → BIONZ XR pipeline.
    private static final String[][] ALWAYS_TAGS = {
            {"com.sonymobile.control.aeMode",                 "1"},
            {"com.sonymobile.control.afDriveMode",            "1"},
            {"com.sonymobile.control.wbMode",                 "0"},
            {"com.sonymobile.control.powerSaveMode",          "0"},
            {"com.sonymobile.statistics.faceSmileScoresMode", "0"},
            {"com.sonymobile.logicalMultiCamera.mode",        "0"},
            {"com.sonymobile.control.vagueControlMode",       "1"},
            {"com.sonymobile.control.usecase",                "1"},
            {"com.sonymobile.control.yuvFrameDrawMode",       "1"},
            {"com.sonymobile.control.lowLightShotMode",       "1"},
    };

    // Session keys must be present at camera-session creation time so they
    // appear in SessionConfiguration.setSessionParameters().
    private static final String[] SESSION_SEED_TAGS = {
            "com.sonymobile.control.vagueControlMode",
            "com.sonymobile.control.usecase",
    };
    private static final int[] SESSION_SEED_VALS = {1, 1};

    // ─── Prefs relay ─────────────────────────────────────────────────────────
    private volatile Map<String, Object> prefMap = new HashMap<>();
    private volatile long lastLoadMs = 0;
    private static final long RELOAD_INTERVAL_MS = 5000;
    private boolean loggedOnce = false;

    private void reloadPrefs(android.content.Context ctx) {
        long now = System.currentTimeMillis();
        if (now - lastLoadMs < RELOAD_INTERVAL_MS) return;
        lastLoadMs = now;
        try {
            String raw = Settings.Global.getString(ctx.getContentResolver(), "sony_cam_xposed");
            if (raw == null || raw.isEmpty()) {
                XposedBridge.log(TAG + ": Settings.Global key not set");
                prefMap = new HashMap<>();
                return;
            }
            org.json.JSONObject json = new org.json.JSONObject(raw);
            Map<String, Object> fresh = new HashMap<>();
            java.util.Iterator<String> keys = json.keys();
            while (keys.hasNext()) {
                String k = keys.next();
                fresh.put(k, json.get(k));
            }
            prefMap = fresh;
            XposedBridge.log(TAG + ": prefs loaded, size=" + fresh.size());
        } catch (Exception e) {
            XposedBridge.log(TAG + ": reloadPrefs failed: " + e.getMessage());
        }
    }

    private int readPref(String key, String type, String defaultVal) {
        Object val = prefMap.get(key);
        try {
            if ("list".equals(type)) {
                return Integer.parseInt(val != null ? val.toString() : defaultVal);
            } else {
                boolean def = !"0".equals(defaultVal);
                if (val instanceof Boolean) return (Boolean) val ? 1 : 0;
                if (val instanceof String) return Boolean.parseBoolean((String) val) ? 1 : 0;
                return def ? 1 : 0;
            }
        } catch (Exception e) {
            try { return Integer.parseInt(defaultVal); } catch (Exception e2) { return 0; }
        }
    }

    /**
     * Box an int value into the type expected by the device's HAL.
     * 1 V  → Byte  (TYPE_BYTE)
     * 1 IV → Integer (TYPE_INT32)
     */
    private static Object boxValue(int value) {
        return (DEVICE.keyType == Byte.class) ? (byte) value : value;
    }

    private Map<String, Integer> getTagValues(String appPkg) {
        try {
            android.content.Context ctx = (android.app.Application)
                    Class.forName("android.app.ActivityThread")
                            .getMethod("currentApplication")
                            .invoke(null);
            if (ctx != null) reloadPrefs(ctx);
        } catch (Exception ignored) {}

        Map<String, Integer> tags = new HashMap<>();

        Object enabledVal = prefMap.get("enabled");
        boolean enabled = !(enabledVal instanceof Boolean) || (Boolean) enabledVal;

        if (!loggedOnce) {
            loggedOnce = true;
            XposedBridge.log(TAG + ": first build() for " + appPkg
                    + " device=" + DEVICE.name
                    + " keyType=" + DEVICE.keyType.getSimpleName()
                    + " enabled=" + enabled
                    + " prefsSize=" + prefMap.size());
        }

        if (!enabled) return tags;

        Object useGlobalVal = prefMap.get(appPkg + ".useGlobalDefaults");
        boolean useGlobal = !(useGlobalVal instanceof Boolean) || (Boolean) useGlobalVal;
        String prefix = useGlobal ? "" : appPkg + ".";

        for (String[] def : TAG_DEFS) {
            String perAppKey = prefix + def[0];
            String key = (!useGlobal && !prefMap.containsKey(perAppKey)) ? def[0] : perAppKey;
            tags.put(def[1], readPref(key, def[3], def[2]));
        }
        for (String[] t : ALWAYS_TAGS) {
            tags.put(t[0], Integer.parseInt(t[1]));
        }

        return tags;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;

        boolean isSonyApp = false;
        for (String s : SONY_APPS) if (s.equals(pkg)) { isSonyApp = true; break; }
        if (isSonyApp || MODULE_PKG.equals(pkg)) return;

        try {
            Class<?> builderClass = XposedHelpers.findClass(
                    "android.hardware.camera2.CaptureRequest$Builder", lpparam.classLoader);
            Class<?> keyClass = XposedHelpers.findClass(
                    "android.hardware.camera2.CaptureRequest$Key", lpparam.classLoader);

            final Map<String, Object> keyCache = new HashMap<>();

            // Seed session keys on every builder at creation time so they appear in
            // SessionConfiguration.setSessionParameters() if the app uses that API.
            try {
                Class<?> implClass = XposedHelpers.findClass(
                        "android.hardware.camera2.impl.CameraDeviceImpl", lpparam.classLoader);
                XposedBridge.hookAllMethods(implClass, "createCaptureRequest", new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        Object builder = param.getResult();
                        if (builder == null) return;
                        for (int i = 0; i < SESSION_SEED_TAGS.length; i++) {
                            try {
                                final String tagName = SESSION_SEED_TAGS[i];
                                Object key = keyCache.get(tagName);
                                if (key == null) {
                                    key = XposedHelpers.newInstance(keyClass, tagName, DEVICE.keyType);
                                    keyCache.put(tagName, key);
                                }
                                XposedHelpers.callMethod(builder, "set", key, boxValue(SESSION_SEED_VALS[i]));
                            } catch (Exception ignored) {}
                        }
                    }
                });
                XposedBridge.log(TAG + ": Hooked createCaptureRequest on " + pkg);
            } catch (Exception e) {
                XposedBridge.log(TAG + ": createCaptureRequest hook failed: " + e);
            }

            XposedBridge.hookAllMethods(builderClass, "build", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Map<String, Integer> tags = getTagValues(pkg);
                    if (tags.isEmpty()) return;

                    Object builder = param.thisObject;
                    for (Map.Entry<String, Integer> entry : tags.entrySet()) {
                        try {
                            String tagName = entry.getKey();
                            int value = entry.getValue();
                            Object key = keyCache.get(tagName);
                            if (key == null) {
                                key = XposedHelpers.newInstance(keyClass, tagName, DEVICE.keyType);
                                keyCache.put(tagName, key);
                            }
                            Object existing = null;
                            try { existing = XposedHelpers.callMethod(builder, "get", key); }
                            catch (Exception ignored) {}
                            if (existing == null) {
                                XposedHelpers.callMethod(builder, "set", key, boxValue(value));
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });

            XposedBridge.log(TAG + ": Hooked " + pkg);

        } catch (Throwable t) {
            XposedBridge.log(TAG + ": hook setup failed for " + pkg + ": " + t);
        }
    }
}
