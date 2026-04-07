#!/system/bin/sh
LOG=/data/local/tmp/sony-camera-unlock.log
PID=$(pidof vendor.somc.hardware.camera.provider@1.0-service)
echo "$(date) Service: PID=$PID" >> "$LOG"
logcat -d | grep SonyCamShim >> "$LOG"
dumpsys media.camera 2>/dev/null | head -3 >> "$LOG"
