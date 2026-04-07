/*
 * Sony Camera Pro App Tracer
 *
 * Attach to: com.sonymobile.photopro
 * Traces all Camera2 API calls and vendor tag usage from Sony's own app
 * to understand exactly what parameters it sends.
 *
 * Usage:
 *   frida -U -f com.sonymobile.photopro -l trace_sony_app.js --no-pause
 */

'use strict';

setTimeout(function() {
    Java.perform(function() {
        console.log('[+] Tracing Sony Camera Pro');

        const Builder = Java.use('android.hardware.camera2.CaptureRequest$Builder');
        const CaptureRequest_Key = Java.use('android.hardware.camera2.CaptureRequest$Key');
        const CameraDevice = Java.use('android.hardware.camera2.CameraDevice');
        const CameraCharacteristics = Java.use('android.hardware.camera2.CameraCharacteristics');

        // ============================================================
        // Trace ALL set() calls on CaptureRequest.Builder
        // ============================================================
        Builder.set.overload('android.hardware.camera2.CaptureRequest$Key', 'java.lang.Object').implementation = function(key, value) {
            const name = key.getName();
            // Log Sony vendor tags with high priority
            if (name.indexOf('com.sonymobile') !== -1) {
                console.log('[SONY_SET] ' + name + ' = ' + value);
            }
            return this.set(key, value);
        };
        console.log('[+] Hooked CaptureRequest.Builder.set()');

        // ============================================================
        // Trace CameraDevice.createCaptureRequest (template type)
        // ============================================================
        CameraDevice.createCaptureRequest.overload('int').implementation = function(templateType) {
            const templates = {
                1: 'TEMPLATE_PREVIEW',
                2: 'TEMPLATE_STILL_CAPTURE',
                3: 'TEMPLATE_RECORD',
                4: 'TEMPLATE_VIDEO_SNAPSHOT',
                5: 'TEMPLATE_ZERO_SHUTTER_LAG',
                6: 'TEMPLATE_MANUAL',
            };
            console.log('[CREATE_REQUEST] template=' + (templates[templateType] || templateType));
            return this.createCaptureRequest(templateType);
        };
        console.log('[+] Hooked CameraDevice.createCaptureRequest()');

        // ============================================================
        // Trace session configuration
        // ============================================================
        const SessionConfiguration = Java.use('android.hardware.camera2.params.SessionConfiguration');
        try {
            SessionConfiguration.$init.overload('int', 'java.util.List', 'java.util.concurrent.Executor', 'android.hardware.camera2.CameraCaptureSession$StateCallback').implementation = function(sessionType, outputs, executor, callback) {
                const types = { 0: 'SESSION_REGULAR', 1: 'SESSION_HIGH_SPEED' };
                console.log('[SESSION_CONFIG] type=' + (types[sessionType] || sessionType) + ' outputs=' + outputs.size());
                const iter = outputs.iterator();
                while (iter.hasNext()) {
                    const config = iter.next();
                    console.log('  output: ' + config.toString());
                }
                return this.$init(sessionType, outputs, executor, callback);
            };
            console.log('[+] Hooked SessionConfiguration constructor');
        } catch(e) {
            console.log('[-] SessionConfiguration hook failed: ' + e.message);
        }

        // ============================================================
        // Trace CameraCharacteristics reads for Sony tags
        // ============================================================
        CameraCharacteristics.get.overload('android.hardware.camera2.CameraCharacteristics$Key').implementation = function(key) {
            const name = key.getName();
            const result = this.get(key);
            if (name.indexOf('com.sonymobile') !== -1) {
                console.log('[CHAR_READ] ' + name + ' = ' + result);
            }
            return result;
        };
        console.log('[+] Hooked CameraCharacteristics.get()');

        // ============================================================
        // Trace build() to log complete request summary
        // ============================================================
        let requestCount = 0;
        Builder.build.implementation = function() {
            requestCount++;
            if (requestCount % 30 === 1) { // Log every 30th to avoid spam
                console.log('[BUILD] Request #' + requestCount);
            }
            return this.build();
        };
        console.log('[+] Hooked build()');

        // ============================================================
        // Trace setRepeatingRequest to capture preview config
        // ============================================================
        const CameraCaptureSession = Java.use('android.hardware.camera2.CameraCaptureSession');
        try {
            CameraCaptureSession.setRepeatingRequest.overload(
                'android.hardware.camera2.CaptureRequest',
                'android.hardware.camera2.CameraCaptureSession$CaptureCallback',
                'android.os.Handler'
            ).implementation = function(request, callback, handler) {
                console.log('[SET_REPEATING] New repeating request');
                return this.setRepeatingRequest(request, callback, handler);
            };
        } catch(e) {}

        // Trace capture() for single capture (photo)
        try {
            CameraCaptureSession.capture.overload(
                'android.hardware.camera2.CaptureRequest',
                'android.hardware.camera2.CameraCaptureSession$CaptureCallback',
                'android.os.Handler'
            ).implementation = function(request, callback, handler) {
                console.log('[CAPTURE] === PHOTO CAPTURE ===');
                return this.capture(request, callback, handler);
            };
        } catch(e) {}

        console.log('\n=== Sony Camera Pro Tracer Ready ===');
        console.log('Use the camera app - vendor tag usage will be logged.\n');
    });
}, 2000);
