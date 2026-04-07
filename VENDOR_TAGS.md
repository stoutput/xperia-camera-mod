# Sony Xperia 1 V (PDX234) — Camera Vendor Tag Reference

Extracted from `dumpsys media.camera` on LineageOS 23.2.

## Camera Devices

| Device | Sensor Name | Type | Public |
|--------|-------------|------|--------|
| 0 | SMC12BX4 (IMX888 Exmor T) | Back Main 24mm f/1.9 | Yes |
| 1 | SUN12BS0 (IMX663 Front) | Front | Yes |
| 2 | SEM52BG0 (IMX650 Tele) | Back Telephoto 85-125mm | No (physical) |
| 3 | LGI12BC0 (IMX563 UW) | Back Ultrawide 16mm | No (physical) |
| 4 | SEM12BC9 | Back (ToF/secondary?) | No (physical) |

Device 0 is a LOGICAL_MULTI_CAMERA that fuses devices 2, 3, 4.
Zoom ratio range: 0.68 - 15.85x

## Vendor Tag Sections

All tags use the `com.sonymobile.*` namespace.

### com.sonymobile.statistics (0x8000xxxx) — Detection/Tracking

| Tag ID | Name | Type | Values (Device 0) | Description |
|--------|------|------|--------------------|-------------|
| 0x80000000 | objectSelectTrigger | byte | request key | Trigger object tracking |
| 0x80000001 | objectSelectTriggerArea | int32 | request key | Area for object selection |
| 0x80000002 | faceSelectTrigger | byte | request key | Trigger face selection |
| 0x80000003 | faceSelectTriggerArea | int32 | request key | Area for face selection |
| 0x80000004 | faceSmileScoresMode | byte | request key | Enable smile detection |
| 0x80000005 | sceneDetectMode | byte | request key | AI scene detection |
| 0x80000006 | conditionDetectMode | byte | request key | Condition detection |
| 0x80000007 | eyeDetectMode | byte | request key | Eye AF mode |
| 0x8000000b | info.availableObjectTracking | byte | [1] | Object tracking supported |
| 0x80000010 | info.availableEyeDetection | byte | [1] | Eye AF supported |
| 0x80000011 | info.availableEyeDetectMode | byte | [0, 1, 2] | 0=off, 1=human, 2=animal |
| 0x80000012 | info.availableHistogramModes | byte | [0, 1] | Histogram overlay |

### com.sonymobile.control (0x8001xxxx) — Processing Controls

| Tag ID | Name | Type | Available Values | Description |
|--------|------|------|-----------------|-------------|
| 0x80010000 | aeMode | byte | request key | Sony extended AE mode |
| 0x80010001 | aeRegionMode | byte | request key | AE region mode |
| 0x80010002 | afRegionMode | byte | request key | AF region mode |
| 0x80010003 | afDriveMode | byte | request key | AF drive mode |
| 0x80010004 | awbColorCompensationAbGm | int32 | request key | WB A-B/G-M shift |
| 0x80010007 | stillSkinSmoothLevel | int32 | request key | Skin smoothing level |
| 0x80010008 | stillHdrMode | byte | request key | Still HDR mode |
| 0x80010009 | powerSaveMode | byte | request key | Power save |
| 0x8001000a | sensitivityLimit | int32 | request key | ISO limit |
| 0x8001000b | exposureTimeLimit | int64 | request key | Shutter speed limit |
| 0x8001000d | multiFrameNrMode | byte | request key | MFNR mode |
| 0x8001000e | videoStabilizationMode | byte | request key | Sony extended stab |
| 0x8001000f | intelligentActiveTrigger | byte | request key | Intelligent active |
| 0x80010010 | highQualitySnapshotMode | byte | request key | HQ snapshot |
| 0x80010011 | vagueControlMode | byte | request key | Bokeh blur mode |
| 0x80010012 | colorToneProfile | byte | request key | **Creative Look** |
| 0x80010013 | cinemaProfile | byte | request key | **Cinema Profile** |
| 0x80010014 | wbMode | byte | request key | Sony extended WB |
| 0x80010015 | wbCustomTrigger | byte | request key | Custom WB trigger |
| 0x80010016 | snapshotPrepare | byte | request key | Pre-capture prepare |
| 0x80010017 | prepareBurstTrigger | byte | request key | Burst prepare |
| 0x80010018 | targetBurstFrameRate | int32 | request key | Target burst FPS |
| 0x80010019 | yuvFrameDrawMode | byte | request key | YUV frame overlay |
| 0x8001001a | yuvFrameDrawOrientation | byte | request key | Frame overlay orient |
| 0x8001001b | usecase | byte | request key | **Use case selector** |
| 0x8001001c | videoMultiFrameHdrMode | byte | request key | Video HDR |
| 0x8001001d | videoSensitivitySmoothingMode | byte | request key | Video ISO smooth |
| 0x8001001e | afSpeed | int32 | request key | AF speed control |
| 0x8001001f | productReviewMode | byte | request key | Product review mode |
| 0x80010020 | lowLightShotMode | byte | request key | **Night mode** |
| 0x80010021 | fps | int32 | request key | FPS control |
| 0x80010023 | hybridZoomMode | byte | request key | Hybrid zoom |
| 0x8001002c | availableStillHdrModes | byte | **[0, 1, 2, 3]** | 0=off, 1-3=modes |
| 0x80010031 | availableMultiFrameNrModes | byte | **[0, 1]** | 0=off, 1=on |
| 0x80010033 | availableHighQualitySnapshotModes | byte | **[0, 1]** | 0=off, 1=on |
| 0x80010035 | availableColorToneProfiles | byte | **[0,1,3,4,6,7,8,11]** | Creative Looks |
| 0x80010036 | availableCinemaProfiles | byte | **[0,1,2,3,4,5,6,7,8,9]** | Cinema profiles |
| 0x8001003a | availableUsecases | byte | **[0, 1, 2]** | Use cases |
| 0x8001003b | availableVideoMultiFrameHdrModes | byte | **[0, 1]** | 0=off, 1=on |
| 0x80010041 | availableLowLightShotModes | byte | **[0, 1]** | 0=off, 1=on |
| 0x80010044 | availableHybridZoomModes | byte | [0] | Hybrid zoom modes |

### Creative Look Profile IDs (colorToneProfile)
Based on Sony Alpha naming convention:
| ID | Likely Profile |
|----|---------------|
| 0 | Standard (ST) |
| 1 | Neutral (NT) |
| 3 | Vivid (VV) |
| 4 | Vivid 2 (VV2) |
| 6 | Film (FL) |
| 7 | Instant (IN) |
| 8 | Sunset (SH) |
| 11 | Black & White (BW) |

### Cinema Profile IDs (cinemaProfile)
| ID | Likely Profile |
|----|---------------|
| 0 | Default |
| 1-4 | Venice CS/Venice 2K/etc. |
| 5-9 | S-Cinetone variants / S-Log2 / S-Log3 / HLG |

### com.sonymobile.scaler (0x8002xxxx) — Stream Configuration

| Tag ID | Name | Type | Values | Description |
|--------|------|------|--------|-------------|
| 0x8002000e | availableSuperResolutionZoomModes | byte | [0, 1, 2] | Super-res zoom modes |
| 0x80020013 | availableBokehBurstConfigurations | int32 | [33, 4000, 3000, 10] | Bokeh burst: JPEG 4000x3000 @10fps |

### com.sonymobile.sensor (0x8003xxxx) — Sensor Hints

| Tag ID | Name | Type | Description |
|--------|------|------|-------------|
| 0x80030000 | sensitivityHint | int32 | ISO hint for processing |
| 0x80030001 | exposureTimeHint | int64 | Exposure hint for processing |

### com.sonymobile.info (0x8006xxxx) — Device Info

| Tag ID | Name | Type | Description |
|--------|------|------|-------------|
| 0x80060000 | sensorName | byte[] | Internal sensor codename |

### com.sonymobile.logicalMultiCamera (0x8007xxxx) — Multi-Camera

| Tag ID | Name | Type | Values | Description |
|--------|------|------|--------|-------------|
| 0x80070000 | mode | byte | request key | Multi-cam fusion mode |
| 0x80070001 | availableModes | byte | [modes] | Available fusion modes |
| 0x80070004 | availableBokehZoomRatioRanges | float | [1.0,1.0,3.68,3.68,5.22,5.22] | Bokeh zoom ranges per lens |

### com.sonymobile.flash (0x8008xxxx) — Flash

| Tag ID | Name | Type | Description |
|--------|------|------|-------------|
| 0x80080000 | displayFlashColor | int32 | Display flash (front camera) color |
| 0x80080001 | displayFlashLightShieldingArea | int32 | Flash shielding area |

## Session Keys (HAL Session Parameters)
These are set when creating a CameraCaptureSession and affect pipeline configuration:
- `aeTargetFpsRange`
- `videoStabilizationMode` (standard)
- `zoomRatio`
- `aperture`
- `com.sonymobile.logicalMultiCamera.mode`
- `com.sonymobile.control.multiFrameNrMode`
- `com.sonymobile.control.videoStabilizationMode`
- `com.sonymobile.control.highQualitySnapshotMode`
- `com.sonymobile.control.vagueControlMode`
- `com.sonymobile.control.usecase`
- `com.sonymobile.control.videoMultiFrameHdrMode`

## Key Insight
The vendor tags ARE listed in `availableRequestKeys` — meaning the Camera2 API framework
will pass them through to the HAL from ANY app. The question is whether the HAL processes
them or silently ignores them based on the calling app's identity.

## EXCAL Plugin Library Map
Sony's proprietary image processing framework (`/vendor/lib64/camera/`):
- `libexcal_hdr_plugin.so` — HDR processing
- `libexcal_raw_proc_plugin.so` — RAW processing
- `libexcal_raw_conv_plugin.so` — RAW conversion
- `libexcal_raw_dump_plugin.so` — RAW dump
- `libexcal_dual_bokeh_comp_plugin.so` — Dual-camera bokeh
- `libexcal_single_bokeh_comp_plugin.so` — Single-camera bokeh
- `libexcal_food_comp_plugin.so` — Food scene optimization
- `libexcal_recognize_map_comp_plugin.so` — Scene recognition
- `libexcal_object_detector_plugin.so` — Object detection (for AF)
- `libexcal_object_tracker_plugin.so` — Object tracking (for AF)
- `libexcal_face_detector_plugin.so` — Face detection
- `libexcal_eye_stabilizer_plugin.so` — Eye-based stabilization
- `libexcal_exposure_analyzer_plugin.so` — AE analysis
- `libexcal_exposure_ctrl_plugin.so` — AE control
- `libexcal_color_ctrl_plugin.so` — Color/tone processing
- `libexcal_iq_ctrl_plugin.so` — Image quality control
- `libexcal_motion_detector_plugin.so` — Motion detection
- `libexcal_motion_estimation_plugin.so` — Motion estimation (for stacking)
- `libexcal_lens_ctrl_plugin.so` — Lens (AF motor) control
- `libexcal_hal_ctrl_plugin.so` — HAL control bridge
- `libexcal_snapshot_ctrl_plugin.so` — Snapshot pipeline control
- `libexcal_stream_ctrl_plugin.so` — Stream configuration
- `libexcal_req_ctrl_plugin.so` — Request routing
- `libexcal_offline_process_ctrl_plugin.so` — Offline (post-capture) processing
- `libexcal_splitter_plugin.so` — Stream splitting
- `libexcal_image_conv_plugin.so` — Image format conversion
- `libexcal_jpeg_enc_plugin.so` — JPEG encoding
- `libexcal_flicker_detector_plugin.so` — Anti-flicker
- `libexcal_scene_detector_plugin.so` — AI scene detection
- `libexcal_debug_display_plugin.so` — Debug overlay
- `libexcal_draw_comp_plugin.so` — Drawing compositor
- `libexcal_ckb_service_plugin.so` — CKB service bridge
- `libexcal_prc_image_conv_plugin.so` — PRC image conversion
