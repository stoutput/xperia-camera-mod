package com.xperia.camerax;

import android.Manifest;
import android.content.pm.PackageManager;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.*;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.*;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {
    private static final String TAG = "SonyCameraX";
    private static final int PERMISSION_REQUEST = 100;

    // UI
    private TextureView textureView;
    private Button btnRecord;
    private TextView tvStatus, tvIso, tvShutter, tvEv;
    private Spinner spinnerLens, spinnerResolutionFps, spinnerCinemaProfile,
            spinnerColorTone, spinnerStabilization;
    private SeekBar seekIso, seekShutter, seekEv;
    private SwitchMaterial switchVideoHdr, switchEyeAf, switchSceneDetect;

    // Camera
    private CameraManager cameraManager;
    private CameraDevice cameraDevice;
    private CameraCaptureSession captureSession;
    private CaptureRequest.Builder previewBuilder;
    private String currentCameraId = "0";

    // Threading
    private HandlerThread cameraThread;
    private Handler cameraHandler;
    private Executor executor;

    // Recording
    private MediaRecorder mediaRecorder;
    private boolean isRecording = false;
    private String currentVideoPath;

    // Settings
    private SonyVendorTags.VideoSettings videoSettings = new SonyVendorTags.VideoSettings();

    // Lens options
    private static final String[] LENS_NAMES = {"Wide (24mm)", "Ultrawide (16mm)", "Tele (85-125mm)"};
    private static final float[] LENS_ZOOM = {1.0f, 0.68f, 3.7f};
    private int selectedLens = 0;

    // Resolution/FPS combos (populated from camera characteristics)
    private List<String> resFpsNames = new ArrayList<>();
    private List<int[]> resFpsValues = new ArrayList<>(); // [width, height, fps]
    private int selectedResFps = 0;

    // Exposure
    private Range<Integer> isoRange;
    private Range<Long> exposureRange;
    private int evMin, evMax;
    private boolean manualIso = false;
    private boolean manualShutter = false;
    private int currentIso = 0;
    private long currentShutter = 0;

    // Preview size
    private static final int PREVIEW_W = 1920;
    private static final int PREVIEW_H = 1080;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        executor = Executors.newSingleThreadExecutor();

        enumerateCapabilities();
        setupSpinners();
        setupListeners();

        if (checkPermissions()) {
            startCameraThread();
        } else {
            requestPermissions();
        }
        textureView.setSurfaceTextureListener(surfaceTextureListener);
    }

    private void initViews() {
        textureView = findViewById(R.id.textureView);
        btnRecord = findViewById(R.id.btnRecord);
        tvStatus = findViewById(R.id.tvStatus);
        tvIso = findViewById(R.id.tvIso);
        tvShutter = findViewById(R.id.tvShutter);
        tvEv = findViewById(R.id.tvEv);
        spinnerLens = findViewById(R.id.spinnerLens);
        spinnerResolutionFps = findViewById(R.id.spinnerResolutionFps);
        spinnerCinemaProfile = findViewById(R.id.spinnerCinemaProfile);
        spinnerColorTone = findViewById(R.id.spinnerColorTone);
        spinnerStabilization = findViewById(R.id.spinnerStabilization);
        seekIso = findViewById(R.id.seekIso);
        seekShutter = findViewById(R.id.seekShutter);
        seekEv = findViewById(R.id.seekEv);
        switchVideoHdr = findViewById(R.id.switchVideoHdr);
        switchEyeAf = findViewById(R.id.switchEyeAf);
        switchSceneDetect = findViewById(R.id.switchSceneDetect);
    }

    private void enumerateCapabilities() {
        try {
            CameraCharacteristics chars = cameraManager.getCameraCharacteristics("0");

            // ISO range
            isoRange = chars.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            if (isoRange == null) isoRange = new Range<>(50, 12800);

            // Exposure time range
            exposureRange = chars.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            if (exposureRange == null) exposureRange = new Range<>(100000L, 1000000000L);

            // EV compensation range
            Range<Integer> evRange = chars.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE);
            if (evRange != null) { evMin = evRange.getLower(); evMax = evRange.getUpper(); }
            else { evMin = -24; evMax = 24; }

            // Enumerate video resolutions and FPS combos
            StreamConfigurationMap map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            Range<Integer>[] fpsRanges = chars.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES);

            // Collect unique FPS values
            Set<Integer> fpsSet = new TreeSet<>(Collections.reverseOrder());
            if (fpsRanges != null) {
                for (Range<Integer> r : fpsRanges) {
                    if (r.getLower().equals(r.getUpper())) fpsSet.add(r.getUpper());
                }
            }
            // Always include common values
            fpsSet.add(30); fpsSet.add(24); fpsSet.add(60);

            // Video sizes from MediaRecorder output
            Size[] videoSizes = map != null ? map.getOutputSizes(MediaRecorder.class) : null;
            if (videoSizes == null) videoSizes = new Size[]{
                    new Size(3840, 2160), new Size(1920, 1080), new Size(1280, 720)};

            // Build combos: filter to common resolutions
            int[][] targetRes = {{3840, 2160}, {1920, 1080}, {1280, 720}};
            String[] resLabels = {"4K", "1080p", "720p"};

            for (int ri = 0; ri < targetRes.length; ri++) {
                int w = targetRes[ri][0], h = targetRes[ri][1];
                boolean hasSize = false;
                for (Size s : videoSizes) {
                    if (s.getWidth() == w && s.getHeight() == h) { hasSize = true; break; }
                }
                if (!hasSize) continue;

                for (int fps : fpsSet) {
                    // Check if this FPS is achievable at this resolution
                    if (map != null) {
                        long minDuration = map.getOutputMinFrameDuration(MediaRecorder.class, new Size(w, h));
                        if (minDuration > 0) {
                            int maxFps = (int) (1_000_000_000L / minDuration);
                            if (fps > maxFps) continue;
                        }
                    }
                    resFpsNames.add(resLabels[ri] + " @ " + fps + "fps");
                    resFpsValues.add(new int[]{w, h, fps});
                }
            }

            // Also add high speed modes
            if (map != null) {
                try {
                    Size[] hsSizes = map.getHighSpeedVideoSizes();
                    if (hsSizes != null) {
                        for (Size s : hsSizes) {
                            Range<Integer>[] hsRanges = map.getHighSpeedVideoFpsRangesFor(s);
                            if (hsRanges != null) {
                                for (Range<Integer> r : hsRanges) {
                                    if (r.getLower().equals(r.getUpper()) && r.getUpper() > 60) {
                                        String label = s.getWidth() + "x" + s.getHeight()
                                                + " @ " + r.getUpper() + "fps (HS)";
                                        resFpsNames.add(label);
                                        resFpsValues.add(new int[]{
                                                s.getWidth(), s.getHeight(), r.getUpper()});
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception e) {
                    Log.w(TAG, "High speed enum failed", e);
                }
            }

            if (resFpsNames.isEmpty()) {
                resFpsNames.add("1080p @ 30fps");
                resFpsValues.add(new int[]{1920, 1080, 30});
            }

            Log.i(TAG, "Enumerated " + resFpsNames.size() + " resolution/fps modes");
            Log.i(TAG, "ISO range: " + isoRange + ", Exposure: " + exposureRange);
            Log.i(TAG, "EV range: " + evMin + " to " + evMax);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to enumerate", e);
        }
    }

    private void setupSpinners() {
        spinnerLens.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, LENS_NAMES));
        spinnerResolutionFps.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, resFpsNames));
        spinnerCinemaProfile.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SonyVendorTags.CINEMA_NAMES));
        spinnerColorTone.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SonyVendorTags.LOOK_NAMES));
        spinnerStabilization.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, SonyVendorTags.VSTAB_NAMES));

        // Default to 1080p@30
        for (int i = 0; i < resFpsValues.size(); i++) {
            int[] v = resFpsValues.get(i);
            if (v[0] == 1920 && v[1] == 1080 && v[2] == 30) {
                spinnerResolutionFps.setSelection(i);
                selectedResFps = i;
                break;
            }
        }

        // Exposure seekbars
        seekIso.setMax(100); // 0=auto, 1-100 maps to ISO range
        seekIso.setProgress(0);
        seekShutter.setMax(100); // 0=auto, 1-100 maps to exposure range
        seekShutter.setProgress(0);
        seekEv.setMax(evMax - evMin);
        seekEv.setProgress(-evMin); // center = 0 EV
    }

    private void setupListeners() {
        btnRecord.setOnClickListener(v -> {
            if (isRecording) stopRecording(); else startRecording();
        });

        spinnerLens.setOnItemSelectedListener(new SpinnerL() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedLens = pos;
                updateRepeatingRequest();
            }
        });

        spinnerResolutionFps.setOnItemSelectedListener(new SpinnerL() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                selectedResFps = pos;
                if (!isRecording && cameraDevice != null) {
                    closeSession();
                    createCaptureSession();
                }
            }
        });

        spinnerCinemaProfile.setOnItemSelectedListener(new SpinnerL() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                videoSettings.cinemaProfile = (byte) pos;
                updateRepeatingRequest();
            }
        });

        spinnerColorTone.setOnItemSelectedListener(new SpinnerL() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                videoSettings.colorToneProfile = SonyVendorTags.LOOK_VALUES[pos];
                updateRepeatingRequest();
            }
        });

        spinnerStabilization.setOnItemSelectedListener(new SpinnerL() {
            public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                videoSettings.stabilizationMode = (byte) pos;
                // Session param - needs session recreation
                if (!isRecording && cameraDevice != null) {
                    closeSession();
                    createCaptureSession();
                }
            }
        });

        switchVideoHdr.setOnCheckedChangeListener((b, c) -> {
            videoSettings.videoHdr = c;
            if (!isRecording && cameraDevice != null) {
                closeSession();
                createCaptureSession();
            }
        });

        switchEyeAf.setOnCheckedChangeListener((b, c) -> {
            videoSettings.eyeAf = c;
            updateRepeatingRequest();
        });

        switchSceneDetect.setOnCheckedChangeListener((b, c) -> {
            videoSettings.sceneDetect = c;
            updateRepeatingRequest();
        });

        // ISO seekbar
        seekIso.setOnSeekBarChangeListener(new SeekL() {
            public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                if (!user) return;
                if (progress == 0) {
                    manualIso = false;
                    tvIso.setText("ISO: Auto");
                } else {
                    manualIso = true;
                    int lo = isoRange.getLower(), hi = isoRange.getUpper();
                    // Logarithmic scale
                    double logLo = Math.log(lo), logHi = Math.log(hi);
                    currentIso = (int) Math.exp(logLo + (logHi - logLo) * progress / 100.0);
                    tvIso.setText("ISO: " + currentIso);
                }
                updateRepeatingRequest();
            }
        });

        // Shutter speed seekbar
        seekShutter.setOnSeekBarChangeListener(new SeekL() {
            public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                if (!user) return;
                if (progress == 0) {
                    manualShutter = false;
                    tvShutter.setText("Shutter: Auto");
                } else {
                    manualShutter = true;
                    long lo = exposureRange.getLower(), hi = exposureRange.getUpper();
                    double logLo = Math.log(lo), logHi = Math.log(hi);
                    currentShutter = (long) Math.exp(logLo + (logHi - logLo) * progress / 100.0);
                    tvShutter.setText("Shutter: " + formatShutter(currentShutter));
                }
                updateRepeatingRequest();
            }
        });

        // EV compensation
        seekEv.setOnSeekBarChangeListener(new SeekL() {
            public void onProgressChanged(SeekBar sb, int progress, boolean user) {
                if (!user) return;
                int ev = progress + evMin;
                tvEv.setText("EV: " + (ev >= 0 ? "+" : "") + String.format("%.1f", ev / 12.0));
                updateRepeatingRequest();
            }
        });
    }

    private String formatShutter(long ns) {
        if (ns >= 1_000_000_000L) return String.format("%.1fs", ns / 1e9);
        if (ns >= 100_000_000L) return "1/" + (1_000_000_000L / ns);
        if (ns >= 1_000_000L) return "1/" + (1_000_000_000L / ns);
        return "1/" + (1_000_000_000L / ns);
    }

    // ========== Camera lifecycle ==========

    private final TextureView.SurfaceTextureListener surfaceTextureListener =
            new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture st, int w, int h) {
            configureTransform(w, h);
            if (checkPermissions()) openCamera();
        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture st, int w, int h) {
            configureTransform(w, h);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture st) { return true; }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture st) {}
    };

    private void configureTransform(int viewWidth, int viewHeight) {
        if (textureView == null) return;

        int sensorOrientation = 90; // Xperia 1 V main camera
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0, 0, viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, PREVIEW_H, PREVIEW_W); // Swapped because sensor is 90°
        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        // For landscape activity with 90° sensor, we need to rotate the buffer
        bufferRect.offset(centerX - bufferRect.centerX(), centerY - bufferRect.centerY());
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.FILL);
        float scale = Math.max(
                (float) viewHeight / PREVIEW_H,
                (float) viewWidth / PREVIEW_W);
        matrix.postScale(scale, scale, centerX, centerY);
        matrix.postRotate(360 - sensorOrientation, centerX, centerY);

        textureView.setTransform(matrix);
        Log.d(TAG, "Transform: view=" + viewWidth + "x" + viewHeight + " rot=" + sensorOrientation);
    }

    private void openCamera() {
        try {
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) return;

            cameraManager.openCamera(currentCameraId, new CameraDevice.StateCallback() {
                @Override
                public void onOpened(@NonNull CameraDevice camera) {
                    Log.i(TAG, "Camera opened: " + currentCameraId);
                    cameraDevice = camera;
                    createCaptureSession();
                }
                @Override
                public void onDisconnected(@NonNull CameraDevice camera) {
                    camera.close(); cameraDevice = null;
                }
                @Override
                public void onError(@NonNull CameraDevice camera, int error) {
                    Log.e(TAG, "Camera error: " + error);
                    camera.close(); cameraDevice = null;
                    runOnUiThread(() -> status("Camera error: " + error));
                }
            }, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Failed to open camera", e);
        }
    }

    private void createCaptureSession() {
        if (cameraDevice == null || !textureView.isAvailable()) return;
        try {
            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(PREVIEW_W, PREVIEW_H);
            Surface previewSurface = new Surface(texture);

            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.addTarget(previewSurface);
            applyAllSettings(previewBuilder);

            List<OutputConfiguration> outputs = new ArrayList<>();
            outputs.add(new OutputConfiguration(previewSurface));

            SessionConfiguration config = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR, outputs, executor,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            updateRepeatingRequest();
                            runOnUiThread(() -> status("Preview active"));
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            runOnUiThread(() -> status("Session failed!"));
                        }
                    });

            config.setSessionParameters(previewBuilder.build());
            cameraDevice.createCaptureSession(config);

        } catch (CameraAccessException e) {
            Log.e(TAG, "Session creation failed", e);
        }
    }

    private void applyAllSettings(CaptureRequest.Builder builder) {
        // Sony vendor tags
        SonyVendorTags.applyVideoTags(builder, videoSettings);
        SonyVendorTags.applySessionParams(builder, videoSettings);

        // Zoom (lens selection)
        builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, LENS_ZOOM[selectedLens]);

        // FPS
        int[] resFps = resFpsValues.get(selectedResFps);
        int fps = resFps[2];
        builder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<>(fps, fps));

        // Exposure
        if (manualIso || manualShutter) {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_OFF);
            builder.set(CaptureRequest.SENSOR_SENSITIVITY,
                    manualIso ? currentIso : isoRange.getLower());
            builder.set(CaptureRequest.SENSOR_EXPOSURE_TIME,
                    manualShutter ? currentShutter : 33333333L); // 1/30
        } else {
            builder.set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON);
        }

        // EV compensation (only in auto AE mode)
        if (!manualIso && !manualShutter) {
            int ev = seekEv.getProgress() + evMin;
            builder.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, ev);
        }

        // Standard video stabilization
        if (videoSettings.stabilizationMode > 0) {
            builder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                    CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
        }

        // AF continuous video
        builder.set(CaptureRequest.CONTROL_AF_MODE,
                CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO);
    }

    private void updateRepeatingRequest() {
        if (captureSession == null || previewBuilder == null) return;
        try {
            applyAllSettings(previewBuilder);
            captureSession.setRepeatingRequest(previewBuilder.build(), null, cameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "Update repeating failed", e);
        }
    }

    // ========== Recording ==========

    private void startRecording() {
        if (cameraDevice == null) return;
        try {
            closeSession();
            setupMediaRecorder();

            SurfaceTexture texture = textureView.getSurfaceTexture();
            texture.setDefaultBufferSize(PREVIEW_W, PREVIEW_H);
            Surface previewSurface = new Surface(texture);
            Surface recSurface = mediaRecorder.getSurface();

            previewBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            previewBuilder.addTarget(previewSurface);
            previewBuilder.addTarget(recSurface);
            applyAllSettings(previewBuilder);

            List<OutputConfiguration> outputs = new ArrayList<>();
            outputs.add(new OutputConfiguration(previewSurface));
            outputs.add(new OutputConfiguration(recSurface));

            SessionConfiguration config = new SessionConfiguration(
                    SessionConfiguration.SESSION_REGULAR, outputs, executor,
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(@NonNull CameraCaptureSession session) {
                            captureSession = session;
                            try {
                                applyAllSettings(previewBuilder);
                                captureSession.setRepeatingRequest(
                                        previewBuilder.build(), null, cameraHandler);
                                mediaRecorder.start();
                                isRecording = true;
                                runOnUiThread(() -> {
                                    btnRecord.setText("■ STOP");
                                    btnRecord.setBackgroundTintList(
                                            android.content.res.ColorStateList.valueOf(0xFF444444));
                                    status("REC: " + resFpsNames.get(selectedResFps));
                                });
                            } catch (Exception e) {
                                Log.e(TAG, "Rec start failed", e);
                            }
                        }
                        @Override
                        public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                            runOnUiThread(() -> status("Rec session failed!"));
                        }
                    });

            config.setSessionParameters(previewBuilder.build());
            cameraDevice.createCaptureSession(config);

        } catch (Exception e) {
            Log.e(TAG, "Recording failed", e);
            status("Rec error: " + e.getMessage());
        }
    }

    private void stopRecording() {
        try {
            captureSession.stopRepeating();
            mediaRecorder.stop();
            mediaRecorder.reset();
        } catch (Exception e) {
            Log.e(TAG, "Stop rec error", e);
        }
        isRecording = false;
        runOnUiThread(() -> {
            btnRecord.setText("● REC");
            btnRecord.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFFCC0000));
            status("Saved: " + currentVideoPath);
        });
        createCaptureSession();
    }

    private void setupMediaRecorder() throws IOException {
        int[] resFps = resFpsValues.get(selectedResFps);
        int w = resFps[0], h = resFps[1], fps = resFps[2];

        String ts = new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date());
        File dir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_MOVIES), "SonyCameraX");
        dir.mkdirs();
        currentVideoPath = new File(dir, "VID_" + ts + ".mp4").getAbsolutePath();

        mediaRecorder = new MediaRecorder(this);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setOutputFile(currentVideoPath);
        mediaRecorder.setVideoEncodingBitRate(w >= 3840 ? 50_000_000 : 20_000_000);
        mediaRecorder.setVideoFrameRate(fps);
        mediaRecorder.setVideoSize(w, h);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.HEVC);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mediaRecorder.setAudioEncodingBitRate(256_000);
        mediaRecorder.setAudioSamplingRate(48_000);
        mediaRecorder.setOrientationHint(90); // Sensor is 90° rotated
        mediaRecorder.prepare();
    }

    // ========== Helpers ==========

    private void closeSession() {
        if (captureSession != null) {
            try { captureSession.stopRepeating(); } catch (Exception e) {}
            captureSession.close();
            captureSession = null;
        }
    }

    private void status(String msg) {
        tvStatus.setText(msg);
    }

    private void startCameraThread() {
        cameraThread = new HandlerThread("CameraThread");
        cameraThread.start();
        cameraHandler = new Handler(cameraThread.getLooper());
    }

    private boolean checkPermissions() {
        return ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
                && ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void requestPermissions() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO},
                PERMISSION_REQUEST);
    }

    @Override
    public void onRequestPermissionsResult(int code, @NonNull String[] perms, @NonNull int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == PERMISSION_REQUEST && checkPermissions()) {
            startCameraThread();
            if (textureView.isAvailable()) openCamera();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (cameraThread == null) startCameraThread();
        if (textureView.isAvailable() && cameraDevice == null && checkPermissions()) openCamera();
    }

    @Override
    protected void onPause() {
        if (isRecording) stopRecording();
        closeSession();
        if (cameraDevice != null) { cameraDevice.close(); cameraDevice = null; }
        if (cameraThread != null) {
            cameraThread.quitSafely();
            try { cameraThread.join(); } catch (InterruptedException e) {}
            cameraThread = null; cameraHandler = null;
        }
        super.onPause();
    }

    abstract static class SpinnerL implements AdapterView.OnItemSelectedListener {
        public void onNothingSelected(AdapterView<?> p) {}
    }

    abstract static class SeekL implements SeekBar.OnSeekBarChangeListener {
        public void onStartTrackingTouch(SeekBar sb) {}
        public void onStopTrackingTouch(SeekBar sb) {}
    }
}
