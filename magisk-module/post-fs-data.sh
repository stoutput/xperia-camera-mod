#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/local/tmp/sony-camera-unlock.log
echo "$(date) Sony Camera2 API Unlocker v3.0" > "$LOG"

# Ensure the real library is accessible to our shim via bind mount
REAL_DST="/vendor/lib64/local_libcamera_metadata_real.so"
SAVED="/data/local/tmp/local_libcamera_metadata_real.so"

if [ ! -f "$SAVED" ] && [ -f "$MODDIR/original_lib.so" ]; then
    cp "$MODDIR/original_lib.so" "$SAVED"
    chmod 644 "$SAVED"
    echo "$(date) Copied original lib to $SAVED" >> "$LOG"
fi

if [ -f "$SAVED" ]; then
    # Create mount point and bind mount the real lib
    touch "$REAL_DST" 2>/dev/null
    mount --bind "$SAVED" "$REAL_DST" 2>/dev/null
    if [ $? -eq 0 ]; then
        echo "$(date) Bind mounted real lib at $REAL_DST" >> "$LOG"
    else
        echo "$(date) Bind mount failed, trying alternative" >> "$LOG"
        # Alternative: our shim will try /data/local/tmp path too
    fi
else
    echo "$(date) ERROR: No saved original library found" >> "$LOG"
fi

# Properties
resetprop persist.camera.HAL3.enabled 1 2>/dev/null
resetprop persist.vendor.camera.vendortag.allclient 1 2>/dev/null

# Config file
CONF=/data/local/tmp/sony-camera-shim.conf
if [ ! -f "$CONF" ]; then
    cat > "$CONF" << 'EOF'
# Sony Camera2 API Unlocker - Vendor Tag Defaults
# Edit and reboot. Injected into ALL Camera2 API apps.
# Creative Looks: 0=Standard 1=Neutral 3=Vivid 4=Vivid2 6=Film 7=Instant 8=Sunset 11=B&W
colorToneProfile=0
cinemaProfile=0
eyeDetectMode=1
sceneDetectMode=1
conditionDetectMode=1
multiFrameNrMode=0
stillHdrMode=0
highQualitySnapshotMode=0
videoMultiFrameHdrMode=0
videoSensitivitySmoothing=1
superResolutionZoomMode=0
EOF
    chmod 644 "$CONF"
fi

echo "$(date) Done" >> "$LOG"
