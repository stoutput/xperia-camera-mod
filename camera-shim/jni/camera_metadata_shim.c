/*
 * Sony Camera2 API Vendor Tag Injector (v5 - full replacement)
 *
 * Replaces /vendor/lib64/local_libcamera_metadata.so via bind mount.
 * Forwards ALL calls to libcamera_metadata.so (system copy) via RTLD_NEXT.
 * The local_* prefixed functions are mapped to their non-prefixed equivalents.
 * find_camera_metadata_entry is intercepted to inject vendor tag defaults.
 */

#define _GNU_SOURCE
#include <dlfcn.h>
#include <stdint.h>
#include <string.h>
#include <stdlib.h>
#include <stdio.h>
#include <android/log.h>

#define TAG "SonyCamShim"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)

typedef void camera_metadata_t;
typedef struct camera_metadata_entry {
    size_t index; uint32_t tag; uint8_t type; size_t count;
    union { uint8_t *u8; int32_t *i32; float *f; int64_t *i64; double *d; void *raw; } data;
} camera_metadata_entry_t;
typedef struct camera_metadata_ro_entry {
    size_t index; uint32_t tag; uint8_t type; size_t count;
    union { const uint8_t *u8; const int32_t *i32; const float *f; const int64_t *i64; const double *d; const void *raw; } data;
} camera_metadata_ro_entry_t;

/* Resolve symbol from libcamera_metadata.so via explicit dlopen */
static void *_real_lib = NULL;
static void _ensure_lib(void) {
    if (_real_lib) return;
    _real_lib = dlopen("libcamera_metadata.so", RTLD_NOW | RTLD_GLOBAL);
    if (!_real_lib) {
        LOGW("FATAL: cannot dlopen libcamera_metadata.so: %s", dlerror());
    } else {
        LOGI("Opened libcamera_metadata.so: %p", _real_lib);
    }
}
static void* resolve(const char *name) {
    _ensure_lib();
    if (!_real_lib) return NULL;
    void *sym = dlsym(_real_lib, name);
    if (!sym) LOGW("Cannot resolve: %s: %s", name, dlerror());
    return sym;
}

/* Constructor: load the real lib at library load time */
__attribute__((constructor))
static void _shim_constructor(void) {
    _ensure_lib();
    LOGI("Shim v5 constructor - lib loaded");
}

/* Cache a resolved function pointer - use void* to avoid type issues */
#define RESOLVE(name) \
    static void *_fn = NULL; \
    if (!_fn) _fn = resolve(name); \
    if (!_fn) return 0;

/* ====================================================================
 * Vendor tag injection config
 * ==================================================================== */

#define CONFIG_PATH "/data/local/tmp/sony-camera-shim.conf"

typedef struct { uint32_t tag; uint8_t val; uint8_t on; const char *name; } tag_def_t;
static tag_def_t defs[] = {
    { 0x80000007, 1, 1, "eyeDetectMode" },
    { 0x80000005, 1, 1, "sceneDetectMode" },
    { 0x80000006, 1, 1, "conditionDetectMode" },
    { 0x80010000, 1, 1, "aeMode" },
    { 0x80010003, 1, 1, "afDriveMode" },
    { 0x80010014, 0, 1, "wbMode" },
    { 0x80010012, 0, 1, "colorToneProfile" },
    { 0x80010013, 0, 1, "cinemaProfile" },
    { 0x8001000d, 0, 1, "multiFrameNrMode" },
    { 0x80010008, 0, 1, "stillHdrMode" },
    { 0x80010010, 0, 1, "highQualitySnapshotMode" },
    { 0x8001001c, 0, 1, "videoMultiFrameHdrMode" },
    { 0x8001000e, 0, 1, "videoStabilizationMode" },
    { 0x8001001d, 1, 1, "videoSensitivitySmoothing" },
    { 0x80010009, 0, 1, "powerSaveMode" },
    { 0x8001001b, 0, 1, "usecase" },
    { 0x80000004, 0, 1, "faceSmileScoresMode" },
    { 0x80020002, 0, 1, "superResolutionZoomMode" },
    { 0x80070000, 0, 1, "logicalMultiCamera.mode" },
    { 0, 0, 0, NULL }
};
static int cfg_loaded = 0;
static void load_cfg(void) {
    if (cfg_loaded) return; cfg_loaded = 1;
    FILE *f = fopen(CONFIG_PATH, "r");
    if (!f) { LOGI("No config, using defaults"); return; }
    LOGI("Loading config");
    char line[256];
    while (fgets(line, sizeof(line), f)) {
        if (line[0]=='#'||line[0]=='\n') continue;
        char *eq=strchr(line,'='); if(!eq) continue;
        char *nl=strchr(line,'\n'); if(nl)*nl=0;
        *eq=0; int v=atoi(eq+1);
        for (int i=0;defs[i].name;i++)
            if (strcmp(defs[i].name,line)==0) { defs[i].val=(uint8_t)v; defs[i].on=1; break; }
    }
    fclose(f);
}
static tag_def_t* find_def(uint32_t tag) {
    for (int i=0;defs[i].name;i++) if (defs[i].tag==tag&&defs[i].on) return &defs[i];
    return NULL;
}

/* ====================================================================
 * Intercepted: find_camera_metadata_entry
 * ==================================================================== */

typedef int (*find_fn)(camera_metadata_t*, uint32_t, camera_metadata_entry_t*);
typedef int (*add_fn)(camera_metadata_t*, uint32_t, const void*, size_t);
static find_fn real_find = NULL;
static add_fn real_add = NULL;
static int inited = 0;

static void do_init(void) {
    if (inited) return; inited = 1;
    real_find = (find_fn)resolve("find_camera_metadata_entry");
    real_add = (add_fn)resolve("add_camera_metadata_entry");
    LOGI("v5 init: find=%p add=%p", real_find, real_add);
    load_cfg();
}

int find_camera_metadata_entry(camera_metadata_t *src, uint32_t tag,
                                camera_metadata_entry_t *entry) {
    do_init();
    if (!real_find) return -1;
    int ret = real_find(src, tag, entry);
    if (ret == 0 || (tag & 0x80000000) == 0) return ret;
    tag_def_t *d = find_def(tag);
    if (!d || !real_add) return ret;
    uint8_t val = d->val;
    if (real_add(src, tag, &val, 1) == 0)
        return real_find(src, tag, entry);
    return ret;
}

/* ====================================================================
 * All other functions: forward to libcamera_metadata.so via RTLD_NEXT
 *
 * local_X functions forward to X (their non-prefixed equivalent)
 * Standard functions forward to their same name
 * ==================================================================== */

/* --- Forwarding wrapper macro --- */
#define FWD(rtype, fname, real_name, params, args) \
    rtype fname params { \
        static rtype (*_f)params = NULL; \
        if (!_f) _f = (rtype(*)params)resolve(real_name); \
        if (!_f) return (rtype)0; \
        return _f args; \
    }
#define FWD_VOID(fname, real_name, params, args) \
    void fname params { \
        static void (*_f)params = NULL; \
        if (!_f) _f = (void(*)params)resolve(real_name); \
        if (_f) _f args; \
    }

/* --- Standard pass-through --- */
FWD(int, find_camera_metadata_ro_entry, "find_camera_metadata_ro_entry",
    (const camera_metadata_t *a, uint32_t b, camera_metadata_ro_entry_t *c), (a,b,c))
FWD(camera_metadata_t*, allocate_camera_metadata, "allocate_camera_metadata",
    (size_t a, size_t b), (a,b))
FWD(camera_metadata_t*, allocate_copy_camera_metadata_checked, "allocate_copy_camera_metadata_checked",
    (const camera_metadata_t *a), (a))
FWD(int, append_camera_metadata, "append_camera_metadata",
    (camera_metadata_t *a, const camera_metadata_t *b), (a,b))
FWD(camera_metadata_t*, clone_camera_metadata, "clone_camera_metadata",
    (const camera_metadata_t *a), (a))
FWD(int, copy_camera_metadata, "copy_camera_metadata",
    (void *a, const camera_metadata_t *b), (a,b))
FWD_VOID(free_camera_metadata, "free_camera_metadata",
    (camera_metadata_t *a), (a))
FWD(size_t, get_camera_metadata_size, "get_camera_metadata_size",
    (const camera_metadata_t *a), (a))
FWD(size_t, get_camera_metadata_compact_size, "get_camera_metadata_compact_size",
    (const camera_metadata_t *a), (a))
FWD(size_t, get_camera_metadata_entry_count, "get_camera_metadata_entry_count",
    (const camera_metadata_t *a), (a))
FWD(size_t, get_camera_metadata_entry_capacity, "get_camera_metadata_entry_capacity",
    (const camera_metadata_t *a), (a))
FWD(size_t, get_camera_metadata_data_count, "get_camera_metadata_data_count",
    (const camera_metadata_t *a), (a))
FWD(size_t, get_camera_metadata_data_capacity, "get_camera_metadata_data_capacity",
    (const camera_metadata_t *a), (a))
FWD(size_t, calculate_camera_metadata_size, "calculate_camera_metadata_size",
    (size_t a, size_t b), (a,b))
FWD(size_t, calculate_camera_metadata_entry_data_size, "calculate_camera_metadata_entry_data_size",
    (uint8_t a, size_t b), (a,b))
FWD(size_t, get_camera_metadata_alignment, "get_camera_metadata_alignment",
    (void), ())
FWD(int, get_camera_metadata_entry, "get_camera_metadata_entry",
    (camera_metadata_t *a, size_t b, camera_metadata_entry_t *c), (a,b,c))
FWD(int, get_camera_metadata_ro_entry, "get_camera_metadata_ro_entry",
    (const camera_metadata_t *a, size_t b, camera_metadata_ro_entry_t *c), (a,b,c))
FWD(int, update_camera_metadata_entry, "update_camera_metadata_entry",
    (camera_metadata_t *a, uint32_t b, const void *c, size_t d, camera_metadata_entry_t *e), (a,b,c,d,e))
FWD(int, add_camera_metadata_entry, "add_camera_metadata_entry",
    (camera_metadata_t *a, uint32_t b, const void *c, size_t d), (a,b,c,d))
FWD(int, delete_camera_metadata_entry, "delete_camera_metadata_entry",
    (camera_metadata_t *a, uint32_t b), (a,b))
FWD(int, camera_metadata_enum_snprint, "camera_metadata_enum_snprint",
    (uint32_t a, uint32_t b, char *c), (a,b,c))
FWD(int, camera_metadata_enum_value, "camera_metadata_enum_value",
    (uint32_t a, const char *b, uint32_t *c), (a,b,c))
FWD(int, get_camera_metadata_permission_needed, "get_camera_metadata_permission_needed",
    (uint32_t *a), (a))
FWD(uint32_t, get_camera_metadata_vendor_id, "get_camera_metadata_vendor_id",
    (const camera_metadata_t *a), (a))
FWD(camera_metadata_t*, place_camera_metadata, "place_camera_metadata",
    (void *a, size_t b), (a,b))
FWD(int, sort_camera_metadata, "sort_camera_metadata",
    (camera_metadata_t *a), (a))

/* --- local_X -> X mappings --- */
FWD(int, local_add_camera_metadata_entry, "add_camera_metadata_entry",
    (camera_metadata_t *a, uint32_t b, const void *c, size_t d), (a,b,c,d))
FWD_VOID(local_dump_camera_metadata, "dump_camera_metadata",
    (const camera_metadata_t *a, int b, int c), (a,b,c))
FWD_VOID(local_dump_indented_camera_metadata, "dump_indented_camera_metadata",
    (const camera_metadata_t *a, int b, int c, int d), (a,b,c,d))
FWD(const char*, local_get_camera_metadata_section_name, "get_camera_metadata_section_name",
    (uint32_t a), (a))
FWD(const char*, local_get_camera_metadata_tag_name, "get_camera_metadata_tag_name",
    (uint32_t a), (a))
FWD(int, local_get_camera_metadata_tag_type, "get_camera_metadata_tag_type",
    (uint32_t a), (a))
/* local_get_local_X(tag, vid) -> get_local_X(tag, vid) */
FWD(const char*, local_get_local_camera_metadata_section_name, "get_local_camera_metadata_section_name",
    (uint32_t a, uint32_t b), (a,b))
FWD(const char*, local_get_local_camera_metadata_tag_name, "get_local_camera_metadata_tag_name",
    (uint32_t a, uint32_t b), (a,b))
FWD(int, local_get_local_camera_metadata_tag_type, "get_local_camera_metadata_tag_type",
    (uint32_t a, uint32_t b), (a,b))
/* 3-arg vendor_id variant */
FWD(int, local_get_local_camera_metadata_tag_type_vendor_id, "get_local_camera_metadata_tag_type_vendor_id",
    (uint32_t a, uint32_t b, uint32_t *c), (a,b,c))

/* Vendor ops */
typedef void* vendor_tag_ops_t;
FWD(int, local_set_camera_metadata_vendor_ops, "set_camera_metadata_vendor_ops",
    (const vendor_tag_ops_t *a), (a))
FWD(int, local_set_camera_metadata_vendor_cache_ops, "set_camera_metadata_vendor_cache_ops",
    (const vendor_tag_ops_t *a), (a))
FWD(int, set_camera_metadata_vendor_id, "set_camera_metadata_vendor_id",
    (camera_metadata_t *a, uint32_t b), (a,b))
FWD(int, set_camera_metadata_vendor_tag_ops, "set_camera_metadata_vendor_tag_ops",
    (const vendor_tag_ops_t *a), (a))
FWD(int, local_validate_camera_metadata_structure, "validate_camera_metadata_structure",
    (const camera_metadata_t *a, const size_t *b), (a,b))
