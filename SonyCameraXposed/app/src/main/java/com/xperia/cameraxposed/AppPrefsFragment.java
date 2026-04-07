package com.xperia.cameraxposed;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

public class AppPrefsFragment extends PreferenceFragmentCompat
        implements SharedPreferences.OnSharedPreferenceChangeListener {

    private static final String ARG_PKG = "pkg";
    private static final String ARG_LABEL = "label";

    private String appPkg;
    private String appLabel;
    private boolean isGlobal;
    private MaterialButton btnRestart;
    private java.util.Map<String, Object> savedSnapshot = new java.util.HashMap<>();

    public static AppPrefsFragment newInstance(String pkg, String label) {
        AppPrefsFragment f = new AppPrefsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PKG, pkg);
        args.putString(ARG_LABEL, label);
        f.setArguments(args);
        return f;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        appPkg = getArguments() != null ? getArguments().getString(ARG_PKG) : null;
        appLabel = getArguments() != null ? getArguments().getString(ARG_LABEL) : null;
        isGlobal = (appPkg == null);

        getPreferenceManager().setSharedPreferencesMode(Context.MODE_WORLD_READABLE);
        getPreferenceManager().setSharedPreferencesName("settings");
        setPreferencesFromResource(R.xml.preferences, rootKey);

        if (isGlobal) {
            SwitchPreference master = new SwitchPreference(requireContext());
            master.setKey("enabled");
            master.setTitle("Module Enabled");
            master.setSummary("Master switch. Disable to stop all vendor tag injection.");
            master.setDefaultValue(true);
            master.setOrder(-100);
            getPreferenceScreen().addPreference(master);
        } else {
            SwitchPreference useGlobal = new SwitchPreference(requireContext());
            useGlobal.setKey(appPkg + ".useGlobalDefaults");
            useGlobal.setTitle("Use Global Defaults");
            useGlobal.setSummary("Disable to customize settings specifically for " + appLabel);
            useGlobal.setDefaultValue(true);
            useGlobal.setOrder(-100);
            getPreferenceScreen().addPreference(useGlobal);

            prefixKeys(getPreferenceScreen(), appPkg + ".");

            SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
            boolean global = prefs.getBoolean(appPkg + ".useGlobalDefaults", true);
            setOverridesEnabled(!global);

            useGlobal.setOnPreferenceChangeListener((pref, newValue) -> {
                setOverridesEnabled(!(boolean) newValue);
                updateRestartButton();
                return true;
            });
        }

        updateAllListSummaries();

        // Snapshot current state so we can detect actual changes
        if (!isGlobal) takeSnapshot();
    }

    @NonNull
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        // Let PreferenceFragmentCompat create its normal view
        View prefView = super.onCreateView(inflater, container, savedInstanceState);

        if (isGlobal) return prefView;

        // Wrap in a FrameLayout and add floating restart button
        FrameLayout wrapper = new FrameLayout(requireContext());
        wrapper.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Add bottom padding to the preference RecyclerView so button doesn't overlap
        RecyclerView rv = prefView.findViewById(androidx.preference.R.id.recycler_view);
        if (rv != null) {
            rv.setPadding(rv.getPaddingLeft(), rv.getPaddingTop(),
                    rv.getPaddingRight(), 200); // 200px bottom padding
            rv.setClipToPadding(false);
        }

        wrapper.addView(prefView);

        // Create the restart button
        btnRestart = new MaterialButton(requireContext());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 160);
        lp.gravity = android.view.Gravity.BOTTOM;
        lp.setMargins(32, 0, 32, 32);
        btnRestart.setLayoutParams(lp);
        btnRestart.setText("⟳  Restart " + appLabel + " to Apply");
        btnRestart.setTextSize(15);
        btnRestart.setAllCaps(false);
        btnRestart.setBackgroundColor(0xFF6200EE);
        btnRestart.setTextColor(0xFFFFFFFF);
        btnRestart.setElevation(16);
        btnRestart.setVisibility(View.GONE);
        btnRestart.setOnClickListener(v -> {
            forceStopApp(appPkg);
            takeSnapshot(); // Reset baseline so button hides until next change
            btnRestart.setVisibility(View.GONE);
        });

        wrapper.addView(btnRestart);
        return wrapper;
    }

    private void setOverridesEnabled(boolean enabled) {
        int count = getPreferenceScreen().getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference p = getPreferenceScreen().getPreference(i);
            if (p instanceof PreferenceCategory) {
                PreferenceCategory cat = (PreferenceCategory) p;
                for (int j = 0; j < cat.getPreferenceCount(); j++) {
                    cat.getPreference(j).setEnabled(enabled);
                }
            } else {
                String key = p.getKey();
                if (key != null && key.endsWith(".useGlobalDefaults")) continue;
                p.setEnabled(enabled);
            }
        }
    }

    private void prefixKeys(PreferenceGroup group, String prefix) {
        for (int i = 0; i < group.getPreferenceCount(); i++) {
            Preference pref = group.getPreference(i);
            if (pref instanceof PreferenceGroup) {
                prefixKeys((PreferenceGroup) pref, prefix);
            } else if (pref.getKey() != null && !pref.getKey().startsWith(prefix)) {
                pref.setKey(prefix + pref.getKey());
            }
        }
    }

    private void takeSnapshot() {
        savedSnapshot.clear();
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        // Snapshot all keys that belong to this app
        for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (e.getKey().startsWith(appPkg + ".")) {
                savedSnapshot.put(e.getKey(), e.getValue());
            }
        }
    }

    private boolean hasChanges() {
        SharedPreferences prefs = getPreferenceManager().getSharedPreferences();
        for (java.util.Map.Entry<String, ?> e : prefs.getAll().entrySet()) {
            if (!e.getKey().startsWith(appPkg + ".")) continue;
            Object saved = savedSnapshot.get(e.getKey());
            Object current = e.getValue();
            if (saved == null && current != null) return true;
            if (saved != null && !saved.equals(current)) return true;
        }
        // Check for keys that were in snapshot but removed
        for (String key : savedSnapshot.keySet()) {
            if (!prefs.contains(key)) return true;
        }
        return false;
    }

    private void updateRestartButton() {
        if (btnRestart == null) return;
        btnRestart.setVisibility(hasChanges() ? View.VISIBLE : View.GONE);
    }

    private void forceStopApp(String pkg) {
        try {
            Runtime.getRuntime().exec(new String[]{"su", "-c", "am force-stop " + pkg});
            Toast.makeText(requireContext(),
                    appLabel + " stopped. Reopen it to apply settings.",
                    Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(requireContext(),
                    "Failed to stop " + appLabel, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        getPreferenceManager().getSharedPreferences()
                .registerOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onPause() {
        getPreferenceManager().getSharedPreferences()
                .unregisterOnSharedPreferenceChangeListener(this);
        super.onPause();
    }

    @Override
    public void onSharedPreferenceChanged(SharedPreferences prefs, String key) {
        if (key == null) return;
        updateListSummary(key);
        if (!isGlobal) updateRestartButton();
    }

    private void updateAllListSummaries() {
        String prefix = isGlobal ? "" : appPkg + ".";
        for (String k : new String[]{"colorToneProfile", "cinemaProfile", "eyeDetectMode",
                "stillHdrMode", "superResolutionZoomMode", "videoStabilizationMode"}) {
            updateListSummary(prefix + k);
        }
    }

    private void updateListSummary(String key) {
        Preference pref = findPreference(key);
        if (pref instanceof ListPreference) {
            ListPreference lp = (ListPreference) pref;
            CharSequence entry = lp.getEntry();
            if (entry != null) {
                String base = pref.getSummary() != null ?
                        pref.getSummary().toString().replaceAll("\nCurrent:.*$", "") : "";
                lp.setSummary(base + "\nCurrent: " + entry);
            }
        }
    }
}
