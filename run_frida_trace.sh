#!/bin/bash
# Sony Xperia 1 V Camera HAL Frida Tracer
# Run this from the xperia-camera-mod directory

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
VENV="$SCRIPT_DIR/.venv/bin"

echo "=== Sony Camera HAL Tracer ==="
echo ""

# Start frida-server on device if not running
echo "[1/3] Starting frida-server on device..."
adb shell "su -c 'pidof frida-server'" > /dev/null 2>&1
if [ $? -ne 0 ]; then
    adb shell "su -c '/data/local/tmp/frida-server -D &'" &
    sleep 2
    echo "  frida-server started"
else
    echo "  frida-server already running"
fi

echo ""
echo "[2/3] Available camera processes:"
adb shell "ps -A | grep -i camera"

echo ""
echo "[3/3] Choose trace target:"
echo "  1) Trace camera HAL provider (vendor tags + binder UIDs)"
echo "  2) Trace cameraserver (detailed vendor tag values)"
echo "  3) Trace Sony Camera Pro app (app-side behavior)"
echo ""
read -p "Select (1/2/3): " choice

case $choice in
    1)
        echo "Attaching to camera provider HAL..."
        "$VENV/frida" -U -n "vendor.somc.hardware.camera.provider@1.0-service" \
            -l "$SCRIPT_DIR/frida-scripts/trace_camera_hal.js" \
            --no-pause
        ;;
    2)
        echo "Attaching to cameraserver..."
        "$VENV/frida" -U -n "cameraserver" \
            -l "$SCRIPT_DIR/frida-scripts/dump_vendor_tags_detailed.js" \
            --no-pause
        ;;
    3)
        echo "Spawning Sony Camera Pro..."
        "$VENV/frida" -U -f "com.sonymobile.photopro" \
            -l "$SCRIPT_DIR/frida-scripts/trace_camera_hal.js" \
            --no-pause
        ;;
    *)
        echo "Invalid choice"
        exit 1
        ;;
esac
