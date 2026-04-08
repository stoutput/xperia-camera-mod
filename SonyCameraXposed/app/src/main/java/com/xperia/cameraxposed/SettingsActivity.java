package com.xperia.cameraxposed;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public class SettingsActivity extends AppCompatActivity {

    private static final String TAG = "SonyCamXposed";
    private static final String OUR_PKG = "com.xperia.cameraxposed";
    private static final String[] SONY_APPS = {
            "com.sonymobile.photopro", "jp.co.sony.mc.videopro",
            "jp.co.sony.mc.camera.app", "jp.co.sony.mc.cameraapq"
    };

    List<AppInfo> scopedApps = new ArrayList<>();

    static class AppInfo {
        String packageName;
        String label;
        AppInfo(String pkg, String label) {
            this.packageName = pkg;
            this.label = label;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        grantWriteSettingsIfNeeded();
        scopedApps = loadScopedApps();

        ViewPager2 pager = findViewById(R.id.viewPager);
        TabLayout tabs = findViewById(R.id.tabLayout);

        pager.setAdapter(new PagerAdapter(this));
        new TabLayoutMediator(tabs, pager, (tab, pos) -> {
            if (pos == 0) tab.setText("Global Defaults");
            else tab.setText(scopedApps.get(pos - 1).label);
        }).attach();
    }

    private void grantWriteSettingsIfNeeded() {
        if (checkSelfPermission("android.permission.WRITE_SECURE_SETTINGS")
                == android.content.pm.PackageManager.PERMISSION_GRANTED) return;
        new Thread(() -> {
            try {
                Process su = Runtime.getRuntime().exec("su");
                java.io.DataOutputStream os =
                        new java.io.DataOutputStream(su.getOutputStream());
                os.writeBytes("pm grant " + getPackageName() + " android.permission.WRITE_SECURE_SETTINGS\n");
                os.writeBytes("exit\n");
                os.flush();
                su.waitFor();
            } catch (Exception e) {
                Log.w(TAG, "Could not grant WRITE_SECURE_SETTINGS: " + e.getMessage());
            }
        }).start();
    }

    private List<AppInfo> loadScopedApps() {
        Set<String> pkgs = new LinkedHashSet<>();
        PackageManager pm = getPackageManager();

        // Read LSPosed scope via su (DB is root-only)
        try {
            Process p = Runtime.getRuntime().exec(new String[]{
                    "su", "-c",
                    "sqlite3 /data/adb/lspd/config/modules_config.db " +
                    "\"SELECT app_pkg_name FROM scope WHERE module_pkg_name = '" + OUR_PKG + "'\""
            });
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (!line.isEmpty()) pkgs.add(line);
            }
            reader.close();
            p.waitFor();
            Log.i(TAG, "Read " + pkgs.size() + " scoped apps from LSPosed DB");
        } catch (Exception e) {
            Log.w(TAG, "Cannot read LSPosed DB via su: " + e.getMessage());
        }

        // Fallback if DB read failed
        if (pkgs.isEmpty()) {
            Log.i(TAG, "Using fallback app list");
            String[] fallback = {
                    "net.sourceforge.opencamera", "com.instagram.android",
                    "com.xperia.camerax", "com.helgeapps.backgroundvideorecorder"
            };
            for (String pkg : fallback) {
                try { pm.getPackageInfo(pkg, 0); pkgs.add(pkg); }
                catch (PackageManager.NameNotFoundException ignored) {}
            }
        }

        // Build AppInfo list, filtering out system/sony/ourselves
        List<AppInfo> apps = new ArrayList<>();
        for (String pkg : pkgs) {
            if (pkg.equals("system") || pkg.equals("android") || pkg.equals(OUR_PKG))
                continue;
            boolean isSony = false;
            for (String s : SONY_APPS) if (s.equals(pkg)) { isSony = true; break; }
            if (isSony) continue;

            String label = pkg;
            try {
                ApplicationInfo ai = pm.getApplicationInfo(pkg, 0);
                label = pm.getApplicationLabel(ai).toString();
            } catch (PackageManager.NameNotFoundException ignored) {}

            apps.add(new AppInfo(pkg, label));
            Log.i(TAG, "Scoped app: " + pkg + " (" + label + ")");
        }

        return apps;
    }

    class PagerAdapter extends FragmentStateAdapter {
        PagerAdapter(@NonNull AppCompatActivity a) { super(a); }

        @Override
        public int getItemCount() { return 1 + scopedApps.size(); }

        @NonNull
        @Override
        public Fragment createFragment(int pos) {
            if (pos == 0) return AppPrefsFragment.newInstance(null, null);
            AppInfo app = scopedApps.get(pos - 1);
            return AppPrefsFragment.newInstance(app.packageName, app.label);
        }
    }
}
