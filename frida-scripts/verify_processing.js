/*
 * Sony EXCAL Processing Verification
 *
 * 1. Hooks update_camera_metadata_entry to see what the HAL WRITES
 *    back in capture results (proves processing is active)
 * 2. Hooks socket send/recv on EXCAL IPC sockets to see pipeline commands
 *
 * Usage:
 *   frida -U -n "vendor.somc.hardware.camera.provider@1.0-service" \
 *       -l frida-scripts/verify_processing.js
 */

'use strict';

var SONY_TAGS = {
    0x80000007: 'eyeDetectMode',
    0x80000005: 'sceneDetectMode',
    0x80000006: 'conditionDetectMode',
    0x80000004: 'faceSmileScoresMode',
    0x80000008: 'objectSelectArea',
    0x80000009: 'faceSelectArea',
    0x8000000a: 'faceSmileScores',
    0x8000000b: 'scene',
    0x8000000c: 'condition',
    0x80010000: 'aeMode',
    0x80010001: 'aeRegionMode',
    0x80010002: 'afRegionMode',
    0x80010003: 'afDriveMode',
    0x80010004: 'awbColorCompensationAbGm',
    0x80010007: 'stillSkinSmoothLevel',
    0x80010008: 'stillHdrMode',
    0x80010009: 'powerSaveMode',
    0x8001000d: 'multiFrameNrMode',
    0x8001000e: 'videoStabilizationMode',
    0x80010010: 'highQualitySnapshotMode',
    0x80010011: 'vagueControlMode',
    0x80010012: 'colorToneProfile',
    0x80010013: 'cinemaProfile',
    0x80010014: 'wbMode',
    0x8001001b: 'usecase',
    0x8001001c: 'videoMultiFrameHdrMode',
    0x8001001d: 'videoSensitivitySmoothingMode',
    0x8001001e: 'afSpeed',
    0x8001001f: 'productReviewMode',
    0x80010020: 'lowLightShotMode',
    0x80010021: 'fps',
    0x80010022: 'bokehStrength',
    0x80010023: 'hybridZoomMode',
    // Result-only tags (HAL writes these)
    0x80010025: 'stillHdrState',
    0x80010026: 'intelligentActiveState',
    0x80010028: 'wbCustomState',
    0x80010029: 'wbCustomRatio',
    0x8001002a: 'wbCustomTemperature',
    0x8001002b: 'zeroShutterLagCapture',
    0x8001002c: 'captureDuration',
    0x8001002d: 'frameCaptureProgress',
    0x8001002e: 'prepareBurstState',
    0x8001002f: 'burstQuality',
    0x80010030: 'lowLightShotState',
    0x80010031: 'remainingBurstQueue',
    0x80010032: 'bokehStatus',
    0x80010033: 'bokehQuality',
    0x80020002: 'superResolutionZoomMode',
    0x80030002: 'illuminance',
    0x80030003: 'properExposureGap',
    0x80070000: 'logicalMultiCamera.mode',
};

function tagName(id) {
    return SONY_TAGS[id] || ('vendor_0x' + id.toString(16));
}

// Deduplicate log: only log when value changes
var lastWriteValues = {};
var lastReadValues = {};

console.log('[*] Sony EXCAL Processing Verifier');
console.log('[*] Looking for HAL WRITE-back of vendor tags (proves processing)');
console.log('[*] And EXCAL socket traffic\n');

// ============================================================
// Hook 1: WRITE hooks on update_camera_metadata_entry
// If the HAL writes colorToneProfile, eyeDetectMode, etc.
// back in capture results, the EXCAL pipeline is processing them
// ============================================================

var metadataLib = Process.findModuleByName('local_libcamera_metadata.so');
if (!metadataLib) metadataLib = Process.findModuleByName('libcamera_metadata.so');

if (metadataLib) {
    // Hook UPDATE (HAL writing results)
    var update_fn = metadataLib.findExportByName('update_camera_metadata_entry');
    if (update_fn) {
        Interceptor.attach(update_fn, {
            onEnter: function(args) {
                this.tag = args[1].toUInt32();
                this.data = args[2];
                this.count = args[3].toUInt32();
            },
            onLeave: function(retval) {
                try {
                    if (retval.toInt32() !== 0) return;
                    var tag = this.tag;
                    if ((tag & 0x80000000) === 0) return;
                    var name = tagName(tag);
                    if (name.indexOf('vendor_0x') !== -1) return;

                    // Read value
                    var val = '';
                    try {
                        if (this.count > 0 && this.count <= 8) {
                            var vals = [];
                            for (var i = 0; i < Math.min(this.count, 4); i++)
                                vals.push(this.data.add(i).readU8());
                            val = vals.join(',');
                        }
                    } catch(e) {}

                    var key = 'w_' + name;
                    if (lastWriteValues[key] !== val) {
                        lastWriteValues[key] = val;
                        console.log('[WRITE] ' + name + '=' + val);
                    }
                } catch(e) {}
            }
        });
        console.log('[+] Hooked update_camera_metadata_entry (WRITE)');
    }

    // Hook ADD (HAL adding new entries to results)
    var add_fn = metadataLib.findExportByName('add_camera_metadata_entry');
    if (!add_fn) add_fn = metadataLib.findExportByName('local_add_camera_metadata_entry');
    if (add_fn) {
        Interceptor.attach(add_fn, {
            onEnter: function(args) {
                this.tag = args[1].toUInt32();
                this.data = args[2];
                this.count = args[3].toUInt32();
            },
            onLeave: function(retval) {
                try {
                    if (retval.toInt32() !== 0) return;
                    var tag = this.tag;
                    if ((tag & 0x80000000) === 0) return;
                    var name = tagName(tag);
                    if (name.indexOf('vendor_0x') !== -1) return;

                    var val = '';
                    try {
                        if (this.count > 0 && this.count <= 8) {
                            var vals = [];
                            for (var i = 0; i < Math.min(this.count, 4); i++)
                                vals.push(this.data.add(i).readU8());
                            val = vals.join(',');
                        }
                    } catch(e) {}

                    var key = 'a_' + name;
                    if (lastWriteValues[key] !== val) {
                        lastWriteValues[key] = val;
                        console.log('[ADD] ' + name + '=' + val);
                    }
                } catch(e) {}
            }
        });
        console.log('[+] Hooked add (ADD)');
    }

    // Also hook READ with dedup for comparison
    var find_fn = metadataLib.findExportByName('find_camera_metadata_entry');
    if (find_fn) {
        var readCount = 0;
        Interceptor.attach(find_fn, {
            onEnter: function(args) {
                this.tag = args[1].toUInt32();
                this.entry = args[2];
            },
            onLeave: function(retval) {
                try {
                    if (retval.toInt32() !== 0) return;
                    var tag = this.tag;
                    if ((tag & 0x80000000) === 0) return;
                    var name = tagName(tag);
                    if (name.indexOf('vendor_0x') !== -1) return;

                    var val = '';
                    try {
                        var type = this.entry.add(12).readU8();
                        var count = this.entry.add(16).readUInt();
                        var dataPtr = this.entry.add(24).readPointer();
                        if (count > 0 && count <= 8) {
                            var vals = [];
                            for (var i = 0; i < Math.min(count, 4); i++)
                                vals.push(dataPtr.add(i).readU8());
                            val = vals.join(',');
                        }
                    } catch(e) {}

                    var key = 'r_' + name;
                    if (lastReadValues[key] !== val) {
                        lastReadValues[key] = val;
                        readCount++;
                        if (readCount <= 50) {
                            console.log('[READ] ' + name + '=' + val);
                        }
                    }
                } catch(e) {}
            }
        });
        console.log('[+] Hooked find (READ with dedup)');
    }
} else {
    console.log('[-] No camera metadata library found');
}

// ============================================================
// Hook 2: EXCAL IPC socket tracing
// Trace send/sendto/write on the EXCAL sockets (fd for ports 9000-9002)
// ============================================================

// Track which file descriptors are EXCAL sockets
var excalFds = {};

// Hook connect to identify EXCAL socket FDs
var connect_fn = Module.findExportByName('libc.so', 'connect');
if (connect_fn) {
    Interceptor.attach(connect_fn, {
        onEnter: function(args) {
            this.fd = args[0].toInt32();
            this.addr = args[1];
            this.len = args[2].toInt32();
        },
        onLeave: function(retval) {
            if (retval.toInt32() !== 0) return;
            try {
                // Check if it's a Unix domain socket with excal in the name
                var family = this.addr.readU16();
                if (family === 1) { // AF_UNIX
                    var path = this.addr.add(2).readCString();
                    if (path && path.indexOf('excal') !== -1) {
                        excalFds[this.fd] = path;
                        console.log('[EXCAL_SOCKET] Connected fd=' + this.fd + ' path=' + path);
                    }
                }
            } catch(e) {}
        }
    });
    console.log('[+] Hooked connect (EXCAL socket tracking)');
}

// Hook write/send to trace EXCAL IPC messages
var write_fn = Module.findExportByName('libc.so', 'write');
if (write_fn) {
    var excalWriteCount = 0;
    Interceptor.attach(write_fn, {
        onEnter: function(args) {
            var fd = args[0].toInt32();
            if (excalFds[fd]) {
                this.isExcal = true;
                this.fd = fd;
                this.buf = args[1];
                this.len = args[2].toInt32();
            }
        },
        onLeave: function(retval) {
            if (!this.isExcal) return;
            excalWriteCount++;
            if (excalWriteCount <= 100) {
                var preview = '';
                try {
                    var len = Math.min(this.len, 32);
                    var bytes = [];
                    for (var i = 0; i < len; i++)
                        bytes.push(('0' + this.buf.add(i).readU8().toString(16)).slice(-2));
                    preview = bytes.join(' ');
                } catch(e) {}
                console.log('[EXCAL_WRITE] fd=' + this.fd + ' (' + excalFds[this.fd] + ') len=' + this.len + ' data=' + preview);
            } else if (excalWriteCount === 101) {
                console.log('[EXCAL_WRITE] (suppressing further logs...)');
            }
        }
    });
    console.log('[+] Hooked write (EXCAL IPC)');
}

var read_fn = Module.findExportByName('libc.so', 'read');
if (read_fn) {
    var excalReadCount = 0;
    Interceptor.attach(read_fn, {
        onEnter: function(args) {
            var fd = args[0].toInt32();
            if (excalFds[fd]) {
                this.isExcal = true;
                this.fd = fd;
                this.buf = args[1];
            }
        },
        onLeave: function(retval) {
            if (!this.isExcal) return;
            var len = retval.toInt32();
            if (len <= 0) return;
            excalReadCount++;
            if (excalReadCount <= 100) {
                var preview = '';
                try {
                    var plen = Math.min(len, 32);
                    var bytes = [];
                    for (var i = 0; i < plen; i++)
                        bytes.push(('0' + this.buf.add(i).readU8().toString(16)).slice(-2));
                    preview = bytes.join(' ');
                } catch(e) {}
                console.log('[EXCAL_READ] fd=' + this.fd + ' (' + excalFds[this.fd] + ') len=' + len + ' data=' + preview);
            }
        }
    });
    console.log('[+] Hooked read (EXCAL IPC)');
}

console.log('\n=== Ready. Open a camera app and take a photo. ===');
console.log('Look for:');
console.log('  [WRITE] / [ADD] = HAL writing vendor tags to results (proves processing)');
console.log('  [EXCAL_SOCKET] / [EXCAL_WRITE] = EXCAL pipeline IPC traffic');
console.log('  [READ] = HAL reading tags from requests (deduped, first occurrence only)\n');
