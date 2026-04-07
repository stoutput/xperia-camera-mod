/*
 * Sony Xperia 1 V Camera HAL Tracer
 *
 * Attach to: vendor.somc.hardware.camera.provider@1.0-service
 * Purpose: Trace vendor tag usage and identify app-specific behavior
 *
 * Usage:
 *   frida -U -n "vendor.somc.hardware.camera.provider@1.0-service" -l trace_camera_hal.js
 *   (Then open Sony Camera Pro or a 3rd party camera app)
 */

'use strict';

// Sony vendor tag IDs (from dumpsys media.camera)
var SONY_VENDOR_TAGS = {
    // Statistics / Detection
    0x80000000: 'objectSelectTrigger',
    0x80000001: 'objectSelectTriggerArea',
    0x80000002: 'faceSelectTrigger',
    0x80000003: 'faceSelectTriggerArea',
    0x80000004: 'faceSmileScoresMode',
    0x80000005: 'sceneDetectMode',
    0x80000006: 'conditionDetectMode',
    0x80000007: 'eyeDetectMode',
    // Control
    0x80010000: 'aeMode',
    0x80010001: 'aeRegionMode',
    0x80010002: 'afRegionMode',
    0x80010003: 'afDriveMode',
    0x80010004: 'awbColorCompensationAbGm',
    0x80010007: 'stillSkinSmoothLevel',
    0x80010008: 'stillHdrMode',
    0x80010009: 'powerSaveMode',
    0x8001000a: 'sensitivityLimit',
    0x8001000b: 'exposureTimeLimit',
    0x8001000d: 'multiFrameNrMode',
    0x8001000e: 'videoStabilizationMode',
    0x8001000f: 'intelligentActiveTrigger',
    0x80010010: 'highQualitySnapshotMode',
    0x80010011: 'vagueControlMode',
    0x80010012: 'colorToneProfile',
    0x80010013: 'cinemaProfile',
    0x80010014: 'wbMode',
    0x80010015: 'wbCustomTrigger',
    0x80010016: 'snapshotPrepare',
    0x80010017: 'prepareBurstTrigger',
    0x80010018: 'targetBurstFrameRate',
    0x80010019: 'yuvFrameDrawMode',
    0x8001001a: 'yuvFrameDrawOrientation',
    0x8001001b: 'usecase',
    0x8001001c: 'videoMultiFrameHdrMode',
    0x8001001d: 'videoSensitivitySmoothingMode',
    0x8001001e: 'afSpeed',
    0x8001001f: 'productReviewMode',
    0x80010020: 'lowLightShotMode',
    0x80010021: 'fps',
    0x80010022: 'bokehStrength',
    0x80010023: 'hybridZoomMode',
    // Scaler
    0x80020002: 'superResolutionZoomMode',
    // Sensor hints
    0x80030000: 'sensitivityHint',
    0x80030001: 'exposureTimeHint',
    // Multi-camera
    0x80070000: 'logicalMultiCamera.mode',
    // WB
    0x80090000: 'wbRatio',
    0x80090001: 'wbTemperature',
};

function tagName(id) {
    var name = SONY_VENDOR_TAGS[id];
    return name ? name : ('vendor_0x' + id.toString(16));
}

console.log('[*] Setting up hooks...');

// ============================================================
// Hook 1: Trace camera_metadata operations
// ============================================================

// Hook both libcamera_metadata.so and local_libcamera_metadata.so
var metadataLibs = ['libcamera_metadata.so', 'local_libcamera_metadata.so'];
var hooked = 0;

metadataLibs.forEach(function(libName) {
    var mod = Process.findModuleByName(libName);
    if (!mod) {
        console.log('[-] ' + libName + ' not found');
        return;
    }
    console.log('[*] Found ' + libName + ' @ ' + mod.base);

    var find_fn = mod.findExportByName('find_camera_metadata_entry');
    var update_fn = mod.findExportByName('update_camera_metadata_entry');
    var add_fn = mod.findExportByName('add_camera_metadata_entry');

    if (find_fn) {
        try {
            Interceptor.attach(find_fn, {
                onEnter: function(args) {
                    this.tag = args[1].toUInt32();
                    this.entryPtr = args[2]; // camera_metadata_entry_t* output
                },
                onLeave: function(retval) {
                    try {
                        if (retval.toInt32() === 0) {
                            var tag = this.tag;
                            if ((tag & 0x80000000) !== 0) {
                                var name = tagName(tag);
                                if (name.indexOf('vendor_0x') === -1) {
                                    // Read value from camera_metadata_entry_t:
                                    // struct { uint32_t index, tag; uint8_t type; size_t count; union data{u8*,i32*,f*,i64*,...}; }
                                    // On arm64: index@0, tag@4, type@8, count@16, data_ptr@24
                                    var valStr = '';
                                    try {
                                        var entry = this.entryPtr;
                                        // arm64 camera_metadata_entry_t layout:
                                        // +0  size_t index (8)
                                        // +8  uint32_t tag (4)
                                        // +12 uint8_t type (1 + 3 pad)
                                        // +16 size_t count (8)
                                        // +24 union data ptr (8)
                                        var type = entry.add(12).readU8();
                                        var count = entry.add(16).readUInt();
                                        var dataPtr = entry.add(24).readPointer();
                                        if (count > 0 && count <= 16) {
                                            var vals = [];
                                            if (type === 0) { // byte
                                                for (var i = 0; i < Math.min(count, 8); i++)
                                                    vals.push(dataPtr.add(i).readU8());
                                            } else if (type === 1) { // int32
                                                for (var i = 0; i < Math.min(count, 4); i++)
                                                    vals.push(dataPtr.add(i*4).readS32());
                                            } else if (type === 2) { // float
                                                for (var i = 0; i < Math.min(count, 4); i++)
                                                    vals.push(dataPtr.add(i*4).readFloat().toFixed(2));
                                            } else if (type === 3) { // int64
                                                for (var i = 0; i < Math.min(count, 2); i++)
                                                    vals.push(dataPtr.add(i*8).readS64().toString());
                                            }
                                            valStr = '=' + vals.join(',');
                                        }
                                    } catch(e2) {}
                                    console.log('[READ] ' + name + valStr);
                                }
                            }
                        }
                    } catch(e) {}
                }
            });
            console.log('[+] Hooked find_camera_metadata_entry in ' + libName);
            hooked++;
        } catch(e) {
            console.log('[-] Failed to hook find in ' + libName + ': ' + e.message);
        }
    }

    if (update_fn) {
        try {
            Interceptor.attach(update_fn, {
                onEnter: function(args) {
                    this.tag = args[1].toUInt32();
                    this.data = args[2];
                    this.count = args[3].toUInt32();
                },
                onLeave: function(retval) {
                    try {
                        if (retval.toInt32() === 0) {
                            var tag = this.tag;
                            if ((tag & 0x80000000) !== 0) {
                                var name = tagName(tag);
                                if (name.indexOf('vendor_0x') === -1) {
                                    var valueStr = '';
                                    if (this.count > 0 && this.count <= 8) {
                                        var vals = [];
                                        for (var i = 0; i < this.count; i++) {
                                            vals.push(this.data.add(i).readU8());
                                        }
                                        valueStr = ' val=[' + vals.join(',') + ']';
                                    }
                                    console.log('[WRITE:' + libName.substring(0,5) + '] ' + name + valueStr);
                                }
                            }
                        }
                    } catch(e) {}
                }
            });
            console.log('[+] Hooked update_camera_metadata_entry in ' + libName);
            hooked++;
        } catch(e) {
            console.log('[-] Failed to hook update in ' + libName + ': ' + e.message);
        }
    }

    if (add_fn) {
        try {
            Interceptor.attach(add_fn, {
                onEnter: function(args) {
                    this.tag = args[1].toUInt32();
                    this.data = args[3];
                    this.count = args[4].toUInt32();
                },
                onLeave: function(retval) {
                    try {
                        if (retval.toInt32() === 0) {
                            var tag = this.tag;
                            if ((tag & 0x80000000) !== 0) {
                                var name = tagName(tag);
                                if (name.indexOf('vendor_0x') === -1) {
                                    var valueStr = '';
                                    if (this.count > 0 && this.count <= 8) {
                                        var vals = [];
                                        for (var i = 0; i < this.count; i++) {
                                            vals.push(this.data.add(i).readU8());
                                        }
                                        valueStr = ' val=[' + vals.join(',') + ']';
                                    }
                                    console.log('[ADD:' + libName.substring(0,5) + '] ' + name + valueStr);
                                }
                            }
                        }
                    } catch(e) {}
                }
            });
            console.log('[+] Hooked add_camera_metadata_entry in ' + libName);
            hooked++;
        } catch(e) {
            console.log('[-] Failed to hook add in ' + libName + ': ' + e.message);
        }
    }
});

console.log('[*] Total hooks installed: ' + hooked);

console.log('\n=== HAL Tracer Ready (with values) ===');
console.log('Tags now show values: e.g. colorToneProfile=3 means Vivid\n');
