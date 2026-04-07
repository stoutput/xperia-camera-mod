package com.xperia.camerax;

import android.hardware.camera2.CaptureRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * Sony Xperia vendor tag definitions for Camera2 API.
 * Extracted from dumpsys media.camera on PDX234 (Xperia 1 V).
 */
public class SonyVendorTags {

    // Cinema Profiles (com.sonymobile.control.cinemaProfile)
    public static final byte CINEMA_DEFAULT = 0;
    public static final byte CINEMA_1 = 1;
    public static final byte CINEMA_2 = 2;
    public static final byte CINEMA_3 = 3;
    public static final byte CINEMA_4 = 4;
    public static final byte CINEMA_5 = 5;
    public static final byte CINEMA_6 = 6;
    public static final byte CINEMA_7 = 7;
    public static final byte CINEMA_8 = 8;
    public static final byte CINEMA_9 = 9;
    public static final String[] CINEMA_NAMES = {
        "Default", "Profile 1", "Profile 2", "Profile 3", "Profile 4",
        "Profile 5", "Profile 6", "Profile 7", "Profile 8", "Profile 9"
    };

    // Creative Looks (com.sonymobile.control.colorToneProfile)
    public static final byte LOOK_STANDARD = 0;
    public static final byte LOOK_NEUTRAL = 1;
    public static final byte LOOK_VIVID = 3;
    public static final byte LOOK_VIVID2 = 4;
    public static final byte LOOK_FILM = 6;
    public static final byte LOOK_INSTANT = 7;
    public static final byte LOOK_SUNSET = 8;
    public static final byte LOOK_BW = 11;
    public static final byte[] LOOK_VALUES = {0, 1, 3, 4, 6, 7, 8, 11};
    public static final String[] LOOK_NAMES = {
        "Standard (ST)", "Neutral (NT)", "Vivid (VV)", "Vivid 2 (VV2)",
        "Film (FL)", "Instant (IN)", "Sunset (SH)", "B&W (BW)"
    };

    // Video Stabilization modes
    public static final byte VSTAB_OFF = 0;
    public static final byte VSTAB_ON = 1;
    public static final byte VSTAB_ACTIVE = 2;
    public static final String[] VSTAB_NAMES = {"Off", "Standard", "Active"};

    // Use cases
    public static final byte USECASE_STILL = 0;
    public static final byte USECASE_VIDEO = 1;
    public static final byte USECASE_2 = 2;

    // Vendor tag key cache
    private static final Map<String, CaptureRequest.Key<Byte>> byteKeys = new HashMap<>();
    private static final Map<String, CaptureRequest.Key<Integer>> intKeys = new HashMap<>();

    public static CaptureRequest.Key<Byte> byteKey(String name) {
        return byteKeys.computeIfAbsent(name, n -> new CaptureRequest.Key<>(n, Byte.class));
    }

    public static CaptureRequest.Key<Integer> intKey(String name) {
        return intKeys.computeIfAbsent(name, n -> new CaptureRequest.Key<>(n, Integer.class));
    }

    /** Apply video-mode vendor tags to a CaptureRequest builder. */
    public static void applyVideoTags(CaptureRequest.Builder builder, VideoSettings settings) {
        // Use case: video
        builder.set(byteKey("com.sonymobile.control.usecase"), USECASE_VIDEO);

        // Cinema profile
        builder.set(byteKey("com.sonymobile.control.cinemaProfile"), settings.cinemaProfile);

        // Creative look
        builder.set(byteKey("com.sonymobile.control.colorToneProfile"), settings.colorToneProfile);

        // Video HDR
        builder.set(byteKey("com.sonymobile.control.videoMultiFrameHdrMode"),
                settings.videoHdr ? (byte) 1 : (byte) 0);

        // Video stabilization (Sony extended)
        builder.set(byteKey("com.sonymobile.control.videoStabilizationMode"),
                settings.stabilizationMode);

        // Video sensitivity smoothing
        builder.set(byteKey("com.sonymobile.control.videoSensitivitySmoothingMode"), (byte) 1);

        // Eye AF
        builder.set(byteKey("com.sonymobile.statistics.eyeDetectMode"),
                settings.eyeAf ? (byte) 1 : (byte) 0);

        // Scene detection
        builder.set(byteKey("com.sonymobile.statistics.sceneDetectMode"),
                settings.sceneDetect ? (byte) 1 : (byte) 0);

        // Condition detection
        builder.set(byteKey("com.sonymobile.statistics.conditionDetectMode"),
                settings.sceneDetect ? (byte) 1 : (byte) 0);

        // Face detection trigger
        builder.set(byteKey("com.sonymobile.statistics.faceSmileScoresMode"), (byte) 0);

        // AF drive mode
        builder.set(byteKey("com.sonymobile.control.afDriveMode"), (byte) 1);

        // AE mode
        builder.set(byteKey("com.sonymobile.control.aeMode"), (byte) 1);

        // WB auto
        builder.set(byteKey("com.sonymobile.control.wbMode"), (byte) 0);

        // Power save off
        builder.set(byteKey("com.sonymobile.control.powerSaveMode"), (byte) 0);

        // Multi-camera mode
        builder.set(byteKey("com.sonymobile.logicalMultiCamera.mode"), (byte) 0);
    }

    /** Apply session-level parameters to a builder used for session configuration. */
    public static void applySessionParams(CaptureRequest.Builder builder, VideoSettings settings) {
        builder.set(byteKey("com.sonymobile.control.usecase"), USECASE_VIDEO);
        builder.set(byteKey("com.sonymobile.control.multiFrameNrMode"), (byte) 0); // off for video
        builder.set(byteKey("com.sonymobile.control.videoMultiFrameHdrMode"),
                settings.videoHdr ? (byte) 1 : (byte) 0);
        builder.set(byteKey("com.sonymobile.control.highQualitySnapshotMode"), (byte) 0);
        builder.set(byteKey("com.sonymobile.control.vagueControlMode"), (byte) 0);
        builder.set(byteKey("com.sonymobile.control.videoStabilizationMode"),
                settings.stabilizationMode);
        builder.set(byteKey("com.sonymobile.logicalMultiCamera.mode"), (byte) 0);
    }

    public static class VideoSettings {
        public byte cinemaProfile = CINEMA_DEFAULT;
        public byte colorToneProfile = LOOK_STANDARD;
        public boolean videoHdr = false;
        public boolean eyeAf = true;
        public boolean sceneDetect = true;
        public byte stabilizationMode = VSTAB_OFF;
    }
}
