/*
 * Sony Vendor Tag Injection v3 - Session Parameter Injection
 *
 * Usage:
 *   frida -U -f net.sourceforge.opencamera -l inject_vendor_tags.js
 *
 * Injects vendor tags at BOTH session creation (session parameters)
 * and per-frame capture requests.
 */

Java.perform(function() {
    console.log('[+] Injector v3 - session parameter injection');

    var CaptureRequest_Key = Java.use('android.hardware.camera2.CaptureRequest$Key');
    var Builder = Java.use('android.hardware.camera2.CaptureRequest$Builder');
    var CameraDevice = Java.use('android.hardware.camera2.CameraDevice');
    var Byte = Java.use('java.lang.Byte');
    var Integer = Java.use('java.lang.Integer');

    // Cache vendor tag keys
    var tagCache = {};
    function vendorKey(name) {
        if (!tagCache[name]) {
            try {
                tagCache[name] = CaptureRequest_Key.$new(name, Byte.class);
            } catch(e) {
                console.log('[KEY_FAIL] ' + name);
                return null;
            }
        }
        return tagCache[name];
    }

    function setTag(builder, name, val) {
        var k = vendorKey(name);
        if (k) builder.set(k, Byte.valueOf(val));
    }

    // ============================================================
    // Hook 1: Intercept session creation to set session parameters
    // SessionConfiguration takes an initial OutputConfiguration list.
    // But the session params are set via setSessionParameters() on
    // the SessionConfiguration, OR via the CaptureRequest used as
    // session params in createCaptureSessionByOutputConfigurations.
    // ============================================================

    // Hook all createCaptureSession variants to inject session params
    // via the initial request
    var sessionCreated = false;

    // Hook createCaptureRequest to inject session-level tags
    // into the very first request (which becomes session params)
    CameraDevice.createCaptureRequest.overload('int').implementation = function(template) {
        var names = {1:'PREVIEW', 2:'STILL_CAPTURE', 3:'RECORD', 4:'VIDEO_SNAPSHOT', 5:'ZSL', 6:'MANUAL'};
        var tname = names[template] || String(template);
        console.log('[CREATE_REQUEST] template=' + tname);

        var builder = this.createCaptureRequest(template);

        // Inject session-level parameters into every builder created
        // The ones that matter for session config:
        setTag(builder, 'com.sonymobile.control.usecase', 0);                  // Still photo mode
        setTag(builder, 'com.sonymobile.control.multiFrameNrMode', 1);         // MFNR ON
        setTag(builder, 'com.sonymobile.control.highQualitySnapshotMode', 1);  // HQ ON
        setTag(builder, 'com.sonymobile.control.vagueControlMode', 0);         // No bokeh
        setTag(builder, 'com.sonymobile.control.videoMultiFrameHdrMode', 0);   // No video HDR
        setTag(builder, 'com.sonymobile.control.videoStabilizationMode', 0);   // No video stab

        if (!sessionCreated) {
            console.log('[SESSION_PARAMS] Injected session parameters:');
            console.log('  usecase=0 (still)');
            console.log('  multiFrameNrMode=1 (ON)');
            console.log('  highQualitySnapshotMode=1 (ON)');
            console.log('  vagueControlMode=0');
            console.log('  videoMultiFrameHdrMode=0');
            console.log('  videoStabilizationMode=0');
            sessionCreated = true;
        }

        return builder;
    };
    console.log('[+] Hooked createCaptureRequest (session param injection)');

    // ============================================================
    // Hook 2: Also try to hook setSessionParameters directly
    // ============================================================
    try {
        var SessionConfiguration = Java.use('android.hardware.camera2.params.SessionConfiguration');
        SessionConfiguration.setSessionParameters.implementation = function(params) {
            console.log('[SET_SESSION_PARAMS] Original session params being set');
            // Let the original params go through, our tags are already
            // in the builder from createCaptureRequest
            return this.setSessionParameters(params);
        };
        console.log('[+] Hooked SessionConfiguration.setSessionParameters()');
    } catch(e) {
        console.log('[-] SessionConfiguration hook: ' + e.message);
    }

    // ============================================================
    // Hook 3: Per-frame injection during build()
    // ============================================================
    var lastTemplate = -1;
    var previewCount = 0;
    var captureCount = 0;

    Builder.build.implementation = function() {
        // Inject per-frame tags on ALL requests
        // Preview-safe tags
        setTag(this, 'com.sonymobile.control.colorToneProfile', 3);     // Vivid
        setTag(this, 'com.sonymobile.statistics.eyeDetectMode', 1);     // Human eye AF
        setTag(this, 'com.sonymobile.statistics.sceneDetectMode', 1);   // Scene detect
        setTag(this, 'com.sonymobile.control.usecase', 0);              // Still
        setTag(this, 'com.sonymobile.statistics.conditionDetectMode', 1); // Condition detect
        setTag(this, 'com.sonymobile.control.aeMode', 1);               // AE on
        setTag(this, 'com.sonymobile.control.afDriveMode', 1);          // AF continuous
        setTag(this, 'com.sonymobile.control.wbMode', 0);               // Auto WB

        // Also inject session-level tags per-frame (belt and suspenders)
        setTag(this, 'com.sonymobile.control.multiFrameNrMode', 1);
        setTag(this, 'com.sonymobile.control.highQualitySnapshotMode', 1);

        previewCount++;
        if (previewCount === 1) {
            console.log('[FRAME #1] All tags injected:');
            console.log('  colorToneProfile=3 (Vivid)');
            console.log('  eyeDetectMode=1 (Human)');
            console.log('  sceneDetectMode=1');
            console.log('  multiFrameNrMode=1');
            console.log('  highQualitySnapshotMode=1');
            console.log('  usecase=0 (still)');
            console.log('  aeMode=1, afDriveMode=1, wbMode=0');
        } else if (previewCount % 200 === 0) {
            console.log('[FRAME #' + previewCount + '] Still injecting...');
        }

        return this.build();
    };
    console.log('[+] Hooked build() with per-frame injection');

    // ============================================================
    // Hook 4: Detect actual photo capture
    // ============================================================
    try {
        var CCS = Java.use('android.hardware.camera2.CameraCaptureSession');
        CCS.capture.overload(
            'android.hardware.camera2.CaptureRequest',
            'android.hardware.camera2.CameraCaptureSession$CaptureCallback',
            'android.os.Handler'
        ).implementation = function(req, cb, handler) {
            captureCount++;
            console.log('\n[=== PHOTO #' + captureCount + ' CAPTURED with vendor tags ===]\n');
            return this.capture(req, cb, handler);
        };
        console.log('[+] Hooked capture()');
    } catch(e) {}

    // Also hook captureBurst if Open Camera uses it
    try {
        var CCS = Java.use('android.hardware.camera2.CameraCaptureSession');
        CCS.captureBurst.overload(
            'java.util.List',
            'android.hardware.camera2.CameraCaptureSession$CaptureCallback',
            'android.os.Handler'
        ).implementation = function(requests, cb, handler) {
            console.log('\n[=== BURST CAPTURE with vendor tags ===]\n');
            return this.captureBurst(requests, cb, handler);
        };
    } catch(e) {}

    console.log('\n=== Injector v3 Ready ===');
    console.log('Session params + per-frame tags will be injected.');
    console.log('Take a photo, then compare with a normal Open Camera photo.\n');
});
