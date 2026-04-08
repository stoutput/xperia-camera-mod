package com.xperia.cameraxposed;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;

import java.util.Map;

/**
 * Exposes this module's SharedPreferences to the Xposed hook via Binder IPC.
 *
 * Binder crosses SELinux boundaries correctly; direct file access from a hooked
 * app's domain is blocked even if the file is world-readable (DAC 644).
 *
 * The hook queries content://com.xperia.cameraxposed.prefs/settings and gets
 * back all key/type/value rows from the "settings" SharedPreferences file.
 */
public class PrefsProvider extends ContentProvider {

    static final String AUTHORITY = "com.xperia.cameraxposed.prefs";
    private static final String[] COLUMNS = {"key", "type", "value"};

    @Override public boolean onCreate() { return true; }

    @Override
    public Cursor query(Uri uri, String[] proj, String sel, String[] args, String sort) {
        SharedPreferences prefs = getContext()
                .getSharedPreferences("settings", Context.MODE_PRIVATE);
        MatrixCursor cursor = new MatrixCursor(COLUMNS);
        for (Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            Object v = e.getValue();
            if (v instanceof Boolean)
                cursor.addRow(new Object[]{e.getKey(), "boolean", v.toString()});
            else if (v instanceof String)
                cursor.addRow(new Object[]{e.getKey(), "string", v});
        }
        return cursor;
    }

    @Override public String getType(Uri uri) { return null; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
