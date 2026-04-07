#!/system/bin/sh
MODDIR=${0%/*}
LOG=/data/local/tmp/sony-camera-unlock.log
echo "$(date) v5.0 starting" > "$LOG"

SHIM="$MODDIR/system/vendor/lib64/local_libcamera_metadata.so"
TARGET="/vendor/lib64/local_libcamera_metadata.so"

# Save original if needed
BACKUP="/data/local/tmp/local_libcamera_metadata_real.so"
[ ! -f "$BACKUP" ] && cp "$TARGET" "$BACKUP" && chmod 644 "$BACKUP"

# Bind mount shim over original
mount --bind "$SHIM" "$TARGET" && echo "$(date) Shim mounted" >> "$LOG" || echo "$(date) Mount failed" >> "$LOG"

# Kill camera provider so init restarts it with our shim
PID=$(pidof vendor.somc.hardware.camera.provider@1.0-service)
[ -n "$PID" ] && kill $PID && echo "$(date) Killed provider PID=$PID, init will restart" >> "$LOG"

# Config file
CONF=/data/local/tmp/sony-camera-shim.conf
if [ ! -f "$CONF" ]; then
cat > "$CONF" << 'EOF'
# Sony Camera2 API Unlocker - Vendor Tag Defaults
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
