#!/system/bin/sh
# KernelSU module install script
# Runs during module installation to save the original library

MODDIR="$MODPATH"

ui_print "Sony Camera2 API Unlocker v3.0"
ui_print "Backing up original local_libcamera_metadata.so..."

# Save the original library inside the module directory
cp /vendor/lib64/local_libcamera_metadata.so "$MODDIR/original_lib.so"

if [ -f "$MODDIR/original_lib.so" ]; then
    ui_print "Original library backed up successfully"
else
    ui_print "WARNING: Could not back up original library"
    ui_print "Trying alternative path..."
    # KSU may have the original accessible differently
    cp /system/vendor/lib64/local_libcamera_metadata.so "$MODDIR/original_lib.so" 2>/dev/null
fi

# Also copy to /data for the shim to find at runtime
cp "$MODDIR/original_lib.so" /data/local/tmp/local_libcamera_metadata_real.so 2>/dev/null
chmod 644 /data/local/tmp/local_libcamera_metadata_real.so 2>/dev/null

ui_print "Install complete. Reboot to activate."
