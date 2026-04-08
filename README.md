# Xperia Camera Mod (Sony Camera Unlocker) - LSPosed Module

[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/Y8Y81X7LER)

Unlocks the full potential of Sony Xperia's camera sensors (especially the Exmor T) by enabling Sony Xperia's EXCAL image processing pipeline via the Camera2 API.

Exposes Sony's proprietary camera features — Creative Looks, Eye AF, Night Mode, Cinema Profiles, MFNR, and more — for use in **any Camera2 app** on the Xperia 1 V (PDX234), and potentially others.

Implemented as an LSPosed module that injects Sony vendor tags transparently into any app's capture requests, with global and per-app configurations via the included settings UI app (Sony Camera Unlocker).

---

## Requirements

- **Device:** Tested on Sony Xperia 1 V (PDX234) — may work on other Xperia models with the same HAL
- **Android:** Rooted. Tested on LineageOS 23.2
- **Root:** KernelSU or Magisk with LSPosed installed. Tested with SukiSU + LSPosed IT v1.9.2

---

## Installation

**1. Install the APK**

Download `sony-camera-unlocker-vX.X.apk` from [Releases](../../releases) and install it like any normal app. LSPosed automatically detects it as a module via its manifest metadata.

**2. Enable and scope it in LSPosed Manager**

Open LSPosed Manager → Modules → enable **Sony Camera Unlocker**, then add every camera app you want to enhance to its scope (e.g. GCam, Open Camera, Instagram).

**3. Configure per-app settings**

Open the **Sony Camera Unlocker** app. You'll see a tab for Global Defaults plus one tab per scoped app. Global Defaults apply to all scoped apps unless you disable "Use Global Defaults" on a specific app's tab to override individually.

**4. Restart target apps**

Force-stop each scoped camera app and reopen it. A "Restart [App] to Apply" button appears in the settings UI whenever you change something — tap it to force-stop the app immediately.

> **KernelSU / SukiSU users:** The "Restart App to Apply" button requires root access. Open SukiSU Manager → SuperUser, find **Sony Camera Unlocker**, and enable root access for it (UID 0, default capabilities). Without this, the button will always fail with "Failed to stop".

---

## Features

| Feature | Values |
|---|---|
| Creative Look | Standard, Neutral, Vivid, Vivid2, FL, Instant, SH, Black & White |
| Cinema Profile | S-Cinetone, S-Log2, S-Log3, HLG, and 6 more |
| Eye AF | Off / Human / Animal |
| Night Mode | On / Off |
| Multi-Frame NR | On / Off |
| Still HDR | Off / Modes 1–3 |
| High Quality Snapshot | On / Off |
| Video HDR | On / Off |
| Super-Resolution Zoom | Off / Modes 1–2 |
| Video Stabilization | Multiple modes |
| Scene / Condition Detect | On / Off |

---

## How It Works

Sony's Camera HAL exposes proprietary features through `com.sonymobile.*` vendor tags in the Camera2 API. Analysis of `libsomc_camerahal.so` shows these are passed directly into the EXCAL processing pipeline with no caller identity checks — any app can set them.

The module hooks `CaptureRequest.Builder.build()` system-wide via LSPosed. Before each capture request is finalized, it injects the configured vendor tags for that app. Tags already set by the app itself are never overwritten, so app-level controls always take precedence. Sony's own camera apps are excluded automatically.

---

## For Developers

**`SonyCameraX`** (released as `sony-camerax-debug-vX.X.apk`) is a minimal Camera2 reference app that directly exercises Sony vendor tags. It is video-only for now and is intended as a testbed and starting point — not a general-purpose camera app. Use it to validate that vendor tags are working on your device or as a base to build on.

See [VENDOR_TAGS.md](VENDOR_TAGS.md) for the full vendor tag reference extracted from `dumpsys media.camera`.

### WIP: KernelSU / Magisk Native Shim (Abandoned)

`ksu-module/` and `magisk-module/` are earlier attempts at a fully native approach that ultimately proved too complex to maintain reliably.

**What it attempted:** Rather than hooking at the Java layer, the idea was to intercept vendor tag injection at the native HAL level. `local_libcamera_metadata.so` is a vendor library used directly by the camera HAL provider. The plan was to replace it via bind mount with a custom shim (`camera-shim/jni/camera_metadata_shim.c`) that forwarded all calls to the real `libcamera_metadata.so` while intercepting `find_camera_metadata_entry()` to inject vendor tag defaults before the HAL saw them — bypassing the Java layer entirely and working for any process including native camera clients.

**Why it was abandoned:**

- **Brittle deployment:** The shim had to be bind-mounted over the real library in `post-fs-data.sh`, then the camera HAL provider (`vendor.somc.hardware.camera.provider@1.0-service`) had to be killed so init would restart it with the new library in place. Timing was unreliable.
- **LD_PRELOAD rabbit hole:** The Magisk module's `service.sh` escalated through three fallback methods (wrap property → direct restart → init `.rc` override) trying to get `LD_PRELOAD` into the camera provider's environment, none of which worked consistently.
- **Full ABI re-export:** The shim had to manually re-export every symbol from the original library, including mapping all `local_*` prefixed functions to their unprefixed equivalents. Any HAL update would silently break it.
- **SELinux:** Required custom `sepolicy.rule` entries to allow the camera HAL domain to execute files from non-standard paths.
- **Wrong interception point:** `find_camera_metadata_entry()` is called when *reading* existing entries, not when *building* requests — injecting there was a hack that caused inconsistent behavior depending on call order.

The LSPosed module hooks `CaptureRequest.Builder.build()` at the Java framework level instead, which is clean, ABI-stable, and requires no native library replacement or bind mounts.

### Debugging with Frida

`tools/frida-server` is an arm64 Frida server binary for the Xperia 1 V. Push it to the device and use the included scripts to trace vendor tag flow through the HAL.

**Setup:**
```bash
adb push tools/frida-server /data/local/tmp/frida-server
adb shell "su -c 'chmod +x /data/local/tmp/frida-server'"
pip install frida-tools  # or: python -m venv .venv && source .venv/bin/activate && pip install frida-tools
```

**Quick start** — interactive launcher that handles starting the server and lets you pick a trace target:
```bash
./run_frida_trace.sh
```

Or start the server and run scripts manually:
```bash
./start-frida-server.sh
```

| Script | What it traces |
|---|---|
| `trace_camera_hal.js` | Vendor tags + binder UIDs hitting the HAL provider |
| `dump_vendor_tags_detailed.js` | Full vendor tag values as seen by cameraserver |
| `trace_sony_app.js` | Sony Camera Pro app-side behavior |
| `inject_vendor_tags.js` | Injects vendor tags into a target app via Frida |
| `test_vendor_tags.js` | Tests which vendor tags a target app accepts |
| `verify_processing.js` | Verifies EXCAL pipeline is processing injected tags |

---

## Camera Hardware (Xperia 1 V)

| Device | Sensor | Lens |
|---|---|---|
| 0 (logical) | IMX888 Exmor T | Main 24mm f/1.9 — fuses tele, UW, ToF |
| 1 | IMX663 | Front camera |
| 2 | IMX650 | Telephoto 85–125mm |
| 3 | IMX563 | Ultrawide 16mm |

Zoom range: 0.68x – 15.85x
