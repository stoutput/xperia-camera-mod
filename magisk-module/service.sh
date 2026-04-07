#!/system/bin/sh
# Service stage - restart camera provider with LD_PRELOAD
# This runs after boot is complete when we can manipulate services

LOG=/data/local/tmp/sony-camera-unlock.log
SHIM=/data/local/tmp/libcamera_metadata_shim.so

echo "$(date) Service stage - injecting shim" >> "$LOG"

if [ ! -f "$SHIM" ]; then
    echo "$(date) ERROR: Shim not found at $SHIM" >> "$LOG"
    exit 1
fi

# Get the current camera provider PID
PROVIDER_PID=$(pidof vendor.somc.hardware.camera.provider@1.0-service)
echo "$(date) Camera provider PID: $PROVIDER_PID" >> "$LOG"

# Method 1: Set LD_PRELOAD property and restart the service
# The init system will pick up the property on restart
setprop wrap.vendor.somc.hardware.camera-provider-1-0 "LD_PRELOAD=$SHIM"
echo "$(date) Set wrap property" >> "$LOG"

# Stop and restart the camera provider service so it picks up LD_PRELOAD
stop vendor.somc.hardware.camera-provider-1-0
sleep 1
start vendor.somc.hardware.camera-provider-1-0
sleep 2

# Verify
NEW_PID=$(pidof vendor.somc.hardware.camera.provider@1.0-service)
echo "$(date) Camera provider new PID: $NEW_PID" >> "$LOG"

if grep -q "libcamera_metadata_shim" /proc/$NEW_PID/maps 2>/dev/null; then
    echo "$(date) SUCCESS: Shim loaded in camera provider" >> "$LOG"
else
    echo "$(date) wrap property didn't work, trying direct restart with LD_PRELOAD" >> "$LOG"

    # Method 2: Kill and restart with explicit LD_PRELOAD
    stop vendor.somc.hardware.camera-provider-1-0
    sleep 1

    # Restart with LD_PRELOAD injected via environment
    LD_PRELOAD=$SHIM /vendor/bin/hw/vendor.somc.hardware.camera.provider@1.0-service &
    sleep 2

    NEW_PID=$(pidof vendor.somc.hardware.camera.provider@1.0-service)
    if grep -q "libcamera_metadata_shim" /proc/$NEW_PID/maps 2>/dev/null; then
        echo "$(date) SUCCESS: Shim loaded via direct restart" >> "$LOG"
    else
        echo "$(date) FALLBACK: Trying inject via /proc/PID" >> "$LOG"

        # Method 3: Use the standard Android init setenv approach
        # Write an override .rc that init will parse on next restart
        echo "$(date) Writing runtime .rc override" >> "$LOG"
        cat > /dev/sony_camera_shim.rc << 'RCEOF'
on property:sys.boot_completed=1
    setprop ctl.restart vendor.somc.hardware.camera-provider-1-0
RCEOF
        echo "$(date) Module loaded but shim injection needs manual verification" >> "$LOG"
    fi
fi

echo "$(date) Service stage done" >> "$LOG"
