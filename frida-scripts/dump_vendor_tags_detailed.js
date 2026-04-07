/*
 * Detailed Sony Vendor Tag Value Dumper
 *
 * Attach to: cameraserver
 * Dumps all Sony vendor tag values with proper type decoding when a camera opens.
 *
 * Usage:
 *   frida -U -n cameraserver -l dump_vendor_tags_detailed.js
 */

'use strict';

// Track metadata operations to build a complete picture
const tagValues = {};

const TYPE_BYTE = 0;
const TYPE_INT32 = 1;
const TYPE_FLOAT = 2;
const TYPE_INT64 = 3;
const TYPE_DOUBLE = 4;
const TYPE_RATIONAL = 5;

function readTagValue(dataPtr, type, count) {
    const values = [];
    try {
        for (let i = 0; i < Math.min(count, 32); i++) {
            switch (type) {
                case TYPE_BYTE:
                    values.push(dataPtr.add(i).readU8());
                    break;
                case TYPE_INT32:
                    values.push(dataPtr.add(i * 4).readS32());
                    break;
                case TYPE_FLOAT:
                    values.push(dataPtr.add(i * 4).readFloat().toFixed(4));
                    break;
                case TYPE_INT64:
                    values.push(dataPtr.add(i * 8).readS64().toString());
                    break;
                case TYPE_DOUBLE:
                    values.push(dataPtr.add(i * 8).readDouble().toFixed(6));
                    break;
                case TYPE_RATIONAL:
                    const num = dataPtr.add(i * 8).readS32();
                    const den = dataPtr.add(i * 8 + 4).readS32();
                    values.push(num + '/' + den);
                    break;
            }
        }
    } catch(e) {
        values.push('ERROR: ' + e.message);
    }
    return values;
}

// Hook get_camera_metadata_tag_type to learn tag types
const tagTypes = {};
const get_tag_type = Module.findExportByName(null, 'get_camera_metadata_tag_type');
if (get_tag_type) {
    Interceptor.attach(get_tag_type, {
        onEnter: function(args) {
            this.tag = args[0].toUInt32();
        },
        onLeave: function(retval) {
            tagTypes[this.tag] = retval.toInt32();
        }
    });
    console.log('[+] Hooked get_camera_metadata_tag_type');
}

// Hook find_camera_metadata_entry for comprehensive tag reading
const find_entry = Module.findExportByName(null, 'find_camera_metadata_entry');
if (find_entry) {
    Interceptor.attach(find_entry, {
        onEnter: function(args) {
            this.metadata = args[0];
            this.tag = args[1].toUInt32();
            this.entryPtr = args[2]; // camera_metadata_entry_t*
        },
        onLeave: function(retval) {
            if (retval.toInt32() !== 0) return;
            const tag = this.tag;
            if ((tag & 0x80000000) === 0) return; // Skip standard tags

            try {
                // camera_metadata_entry_t struct:
                //   uint32_t index;     // +0
                //   uint32_t tag;       // +4
                //   uint8_t  type;      // +8
                //   size_t   count;     // +16 (aligned)
                //   union data {
                //     uint8_t *u8;      // +24
                //     int32_t *i32;
                //     float   *f;
                //     int64_t *i64;
                //     double  *d;
                //     camera_metadata_rational_t *r;
                //   };
                const entry = this.entryPtr;
                const type = entry.add(8).readU8();
                const count = entry.add(16).readUInt();
                const dataPtr = entry.add(24).readPointer();

                const values = readTagValue(dataPtr, type, count);
                const key = '0x' + tag.toString(16);

                if (!tagValues[key] || JSON.stringify(tagValues[key]) !== JSON.stringify(values)) {
                    tagValues[key] = values;
                    console.log('[VENDOR_TAG] 0x' + tag.toString(16) +
                        ' type=' + type + ' count=' + count +
                        ' values=[' + values.join(', ') + ']');
                }
            } catch(e) {
                // Some entries use inline data for small values
            }
        }
    });
    console.log('[+] Hooked find_camera_metadata_entry (detailed)');
}

console.log('\n=== Sony Vendor Tag Detailed Dumper Ready ===');
console.log('Open a camera app to see vendor tag values.\n');
