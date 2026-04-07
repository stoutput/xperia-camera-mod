/*
 * Sony Vendor Tag Injection Test
 *
 * This Frida script hooks into a 3rd party camera app and injects
 * Sony vendor tags into its capture requests to test if the HAL
 * honors them from non-Sony apps.
 *
 * Attach to: any camera app (e.g., com.google.android.apps.googlecamera.fishfood)
 *
 * Usage:
 *   frida -U -f com.google.android.apps.googlecamera.fishfood -l test_vendor_tags.js
 */

'use strict';

// Wait for the app to fully start
setTimeout(function() {
    Java.perform(function() {
        console.log('[+] Java.perform entered');

        // Get the CameraManager
        const CameraManager = Java.use('android.hardware.camera2.CameraManager');
        const CameraCharacteristics = Java.use('android.hardware.camera2.CameraCharacteristics');
        const CameraMetadata = Java.use('android.hardware.camera2.CameraMetadata');
        const CaptureRequest = Java.use('android.hardware.camera2.CaptureRequest');

        // ============================================================
        // Step 1: Enumerate all vendor tags visible to this app
        // ============================================================
        console.log('\n=== Enumerating Sony Vendor Tags ===\n');

        const CameraCharacteristics_Key = Java.use('android.hardware.camera2.CameraCharacteristics$Key');
        const CaptureRequest_Key = Java.use('android.hardware.camera2.CaptureRequest$Key');

        // Hook CameraCharacteristics.get() to log vendor tag reads
        CameraCharacteristics.get.overload('android.hardware.camera2.CameraCharacteristics$Key').implementation = function(key) {
            const name = key.getName();
            const result = this.get(key);
            if (name.indexOf('com.sonymobile') !== -1) {
                console.log('[CHAR_GET] ' + name + ' = ' + result);
            }
            return result;
        };
        console.log('[+] Hooked CameraCharacteristics.get()');

        // ============================================================
        // Step 2: Hook CaptureRequest.Builder to inject vendor tags
        // ============================================================

        const Builder = Java.use('android.hardware.camera2.CaptureRequest$Builder');

        // Log all set() calls to see what the app normally sets
        Builder.set.overload('android.hardware.camera2.CaptureRequest$Key', 'java.lang.Object').implementation = function(key, value) {
            const name = key.getName();
            if (name.indexOf('com.sonymobile') !== -1) {
                console.log('[REQUEST_SET] ' + name + ' = ' + value);
            }
            return this.set(key, value);
        };

        // ============================================================
        // Step 3: Try to inject Sony vendor tags into build()
        // ============================================================

        Builder.build.implementation = function() {
            console.log('[BUILD] CaptureRequest.Builder.build() called');

            // Try injecting Sony vendor tags
            try {
                // Create vendor tag keys
                const Byte = Java.use('java.lang.Byte');

                // Try: colorToneProfile = 3 (Vivid)
                const colorToneKey = CaptureRequest_Key.$new(
                    'com.sonymobile.control.colorToneProfile',
                    Java.use('java.lang.Byte').class
                );
                this.set(colorToneKey, Byte.valueOf(3));
                console.log('[INJECT] Set colorToneProfile = 3 (Vivid)');
            } catch(e) {
                console.log('[INJECT_FAIL] colorToneProfile: ' + e.message);
            }

            try {
                const Byte = Java.use('java.lang.Byte');
                const multiFrameNrKey = CaptureRequest_Key.$new(
                    'com.sonymobile.control.multiFrameNrMode',
                    Java.use('java.lang.Byte').class
                );
                this.set(multiFrameNrKey, Byte.valueOf(1));
                console.log('[INJECT] Set multiFrameNrMode = 1 (ON)');
            } catch(e) {
                console.log('[INJECT_FAIL] multiFrameNrMode: ' + e.message);
            }

            try {
                const Byte = Java.use('java.lang.Byte');
                const hdrKey = CaptureRequest_Key.$new(
                    'com.sonymobile.control.stillHdrMode',
                    Java.use('java.lang.Byte').class
                );
                this.set(hdrKey, Byte.valueOf(1));
                console.log('[INJECT] Set stillHdrMode = 1');
            } catch(e) {
                console.log('[INJECT_FAIL] stillHdrMode: ' + e.message);
            }

            try {
                const Byte = Java.use('java.lang.Byte');
                const eyeKey = CaptureRequest_Key.$new(
                    'com.sonymobile.statistics.eyeDetectMode',
                    Java.use('java.lang.Byte').class
                );
                this.set(eyeKey, Byte.valueOf(1));
                console.log('[INJECT] Set eyeDetectMode = 1 (human)');
            } catch(e) {
                console.log('[INJECT_FAIL] eyeDetectMode: ' + e.message);
            }

            try {
                const Byte = Java.use('java.lang.Byte');
                const usecaseKey = CaptureRequest_Key.$new(
                    'com.sonymobile.control.usecase',
                    Java.use('java.lang.Byte').class
                );
                this.set(usecaseKey, Byte.valueOf(1));
                console.log('[INJECT] Set usecase = 1');
            } catch(e) {
                console.log('[INJECT_FAIL] usecase: ' + e.message);
            }

            try {
                const Byte = Java.use('java.lang.Byte');
                const hqKey = CaptureRequest_Key.$new(
                    'com.sonymobile.control.highQualitySnapshotMode',
                    Java.use('java.lang.Byte').class
                );
                this.set(hqKey, Byte.valueOf(1));
                console.log('[INJECT] Set highQualitySnapshotMode = 1');
            } catch(e) {
                console.log('[INJECT_FAIL] highQualitySnapshotMode: ' + e.message);
            }

            try {
                const Byte = Java.use('java.lang.Byte');
                const lowLightKey = CaptureRequest_Key.$new(
                    'com.sonymobile.control.lowLightShotMode',
                    Java.use('java.lang.Byte').class
                );
                this.set(lowLightKey, Byte.valueOf(1));
                console.log('[INJECT] Set lowLightShotMode = 1');
            } catch(e) {
                console.log('[INJECT_FAIL] lowLightShotMode: ' + e.message);
            }

            return this.build();
        };
        console.log('[+] Hooked CaptureRequest.Builder.build() with vendor tag injection');

        // ============================================================
        // Step 4: Monitor CaptureResult for vendor tag responses
        // ============================================================

        const CaptureResult = Java.use('android.hardware.camera2.CaptureResult');
        const TotalCaptureResult = Java.use('android.hardware.camera2.TotalCaptureResult');

        TotalCaptureResult.get.overload('android.hardware.camera2.CaptureResult$Key').implementation = function(key) {
            const name = key.getName();
            const result = this.get(key);
            if (name.indexOf('com.sonymobile') !== -1 && result !== null) {
                console.log('[RESULT] ' + name + ' = ' + result);
            }
            return result;
        };
        console.log('[+] Hooked TotalCaptureResult.get()');

        console.log('\n=== Vendor Tag Injection Test Ready ===');
        console.log('Take a photo to test if vendor tags are honored.\n');
    });
}, 3000);
