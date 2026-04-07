package com.xperia.cameraxposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
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

    // Tag name constants
    private static final String[][] TAG_DEFS = {
            // {pref_key, vendor_tag_name, default_value, type}
            // type: "list"=ListPreference(String), "switch"=SwitchPreference(boolean)
            {"colorToneProfile",         "com.sonymobile.control.colorToneProfile",            "0", "list"},
            {"cinemaProfile",            "com.sonymobile.control.cinemaProfile",                "0", "list"},
            {"stillHdrMode",             "com.sonymobile.control.stillHdrMode",                 "0", "list"},
            {"multiFrameNrMode",         "com.sonymobile.control.multiFrameNrMode",             "0", "switch"},
            {"highQualitySnapshotMode",  "com.sonymobile.control.highQualitySnapshotMode",      "0", "switch"},
            {"eyeDetectMode",            "com.sonymobile.statistics.eyeDetectMode",              "1", "list"},
            {"sceneDetectMode",          "com.sonymobile.statistics.sceneDetectMode",             "1", "switch"},
            {"conditionDetectMode",      "com.sonymobile.statistics.conditionDetectMode",         "1", "switch"},
            {"videoMultiFrameHdrMode",   "com.sonymobile.control.videoMultiFrameHdrMode",        "0", "switch"},
            {"videoSensitivitySmoothing","com.sonymobile.control.videoSensitivitySmoothingMode",  "1", "switch"},
            {"videoStabilizationMode",   "com.sonymobile.control.videoStabilizationMode",        "0", "list"},
            {"superResolutionZoomMode",  "com.sonymobile.scaler.superResolutionZoomMode",        "0", "list"},
    };

    // Always-injected tags (not configurable)
    private static final String[][] ALWAYS_TAGS = {
            {"com.sonymobile.control.aeMode", "1"},
            {"com.sonymobile.control.afDriveMode", "1"},
            {"com.sonymobile.control.wbMode", "0"},
            {"com.sonymobile.control.powerSaveMode", "0"},
            {"com.sonymobile.statistics.faceSmileScoresMode", "0"},
            {"com.sonymobile.logicalMultiCamera.mode", "0"},
    };

    private XSharedPreferences prefs;

    private void reloadPrefs() {
        if (prefs == null) {
            prefs = new XSharedPreferences(MODULE_PKG, "settings");
        }
        prefs.reload();
    }

    private byte readPref(String key, String type, String defaultVal) {
        try {
            if ("list".equals(type)) {
                return Byte.parseByte(prefs.getString(key, defaultVal));
            } else {
                boolean def = !"0".equals(defaultVal);
                return prefs.getBoolean(key, def) ? (byte) 1 : (byte) 0;
            }
        } catch (Exception e) {
            try { return Byte.parseByte(defaultVal); } catch (Exception e2) { return 0; }
        }
    }

    private Map<String, Byte> getTagValues(String appPkg) {
        reloadPrefs();
        Map<String, Byte> tags = new HashMap<>();

        if (!prefs.getBoolean("enabled", true)) return tags;

        // Check if this app uses per-app overrides
        boolean useGlobal = prefs.getBoolean(appPkg + ".useGlobalDefaults", true);
        String prefix = useGlobal ? "" : appPkg + ".";

        for (String[] def : TAG_DEFS) {
            String prefKey = def[0];
            String tagName = def[1];
            String defaultVal = def[2];
            String type = def[3];
            tags.put(tagName, readPref(prefix + prefKey, type, defaultVal));
        }

        for (String[] t : ALWAYS_TAGS) {
            tags.put(t[0], Byte.parseByte(t[1]));
        }

        return tags;
    }

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        final String pkg = lpparam.packageName;

        // Skip Sony apps and ourselves
        for (String s : SONY_APPS) if (s.equals(pkg)) return;
        if (MODULE_PKG.equals(pkg)) return;

        try {
            Class<?> builderClass = XposedHelpers.findClass(
                    "android.hardware.camera2.CaptureRequest$Builder", lpparam.classLoader);
            Class<?> keyClass = XposedHelpers.findClass(
                    "android.hardware.camera2.CaptureRequest$Key", lpparam.classLoader);

            final Map<String, Object> keyCache = new HashMap<>();

            XposedBridge.hookAllMethods(builderClass, "build", new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    Map<String, Byte> tags = getTagValues(pkg);
                    if (tags.isEmpty()) return;

                    Object builder = param.thisObject;

                    for (Map.Entry<String, Byte> entry : tags.entrySet()) {
                        try {
                            String tagName = entry.getKey();
                            byte value = entry.getValue();

                            Object key = keyCache.get(tagName);
                            if (key == null) {
                                key = XposedHelpers.newInstance(keyClass, tagName, Byte.class);
                                keyCache.put(tagName, key);
                            }

                            // Only inject if app didn't set this tag
                            Object existing = null;
                            try {
                                existing = XposedHelpers.callMethod(builder, "get", key);
                            } catch (Exception ignored) {}

                            if (existing == null) {
                                XposedHelpers.callMethod(builder, "set", key, value);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });

            XposedBridge.log(TAG + ": Hooked " + pkg);

        } catch (Throwable ignored) {}
    }
}
