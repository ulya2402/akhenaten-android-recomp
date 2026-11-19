package com.github.dalerank.akhenaten;

import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import org.libsdl.app.SDLActivity;

public class AkhenatenMainActivity extends SDLActivity {
    private static final int GET_FOLDER_RESULT = 500;
    private static final int IN_GAME_SETTINGS_RESULT = 501;
    private static final String TAG = "AkhenatenMainActivity";
    private static final int STARTUP_LOG_MAX_CHARS = 6000;

    // SDL2 Keycodes for Akhenaten hotkeys
    private static final int SDLK_P = 'p';
    private static final int SDLK_R = 'r';
    private static final int SDLK_F = 'f';
    private static final int SDLK_C = 'c';
    private static final int SDLK_V = 'v';
    private static final int SDLK_EQUALS = '=';
    private static final int SDLK_MINUS = '-';
    private static final int SDLK_LEFTBRACKET = '[';
    private static final int SDLK_RIGHTBRACKET = ']';
    private static final int SDLK_F5 = 0x4000003E;
    private static final int SDLK_F9 = 0x40000042;
    private static final int SDLK_1 = '1';

    private boolean directorySelectionInProgress = false;
    private boolean shouldLaunchInitialDirectorySelection = false;
    private TextView startupLogView;
    private ScrollView startupLogScroll;
    private Button startupLogToggleButton;
    private boolean startupLogVisible = false;
    private boolean startupLogFeatureEnabled = false;

    private FrameLayout loadingOverlay;
    private LinearLayout sideBarContainer;
    private LinearLayout quickBarButtons;
    private LinearLayout hotkeysDrawer;
    private Button collapseToggleBtn;
    private boolean sideBarExpanded = true;
    private boolean hotkeysVisible = false;

    @Override
    public void onStop() {
        super.onStop();
    }

    @Override
    protected String[] getLibraries() {
        return new String[]{"akhenaten"};
    }

    @SuppressWarnings("unused")
    public void showDirectorySelection(boolean again) {
        runOnUiThread(() -> {
            if (directorySelectionInProgress) {
                return;
            }
            try {
                appendStartupLog("Opening folder picker...");
                directorySelectionInProgress = true;
                startActivityForResult(DirectorySelectionActivity.newIntent(this, again), GET_FOLDER_RESULT);
            } catch (Exception e) {
                Log.e(TAG, "Unable to launch directory selection", e);
                appendStartupLog("Folder picker failed: " + e.getClass().getSimpleName());
                directorySelectionInProgress = false;
                FileManager.setBaseUri(this, Uri.EMPTY);
                gotDirectory();
            }
        });
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);

        startupLogFeatureEnabled = LauncherPrefs.isShowLog(this);
        startupLogVisible = startupLogFeatureEnabled;
        applyImmersiveMode();
        installStartupLogOverlay();
        installPharaohLoadingOverlay();
        installPharaohSideControls();
        applySideBarPreferences();

        try {
            nativeSetTouchMode(LauncherPrefs.getTouchMode(this));
        } catch (UnsatisfiedLinkError ignored) {
        }

        clearStartupLog();
        appendStartupLog("App started");
        FileManager.restoreBaseUri(this);
        if (FileManager.hasAccessibleBaseUri(this)) {
            appendStartupLog("Remembered folder: " + FileManager.getDisplayName(this));
            shouldLaunchInitialDirectorySelection = false;
        } else {
            appendStartupLog("No remembered folder");
            shouldLaunchInitialDirectorySelection = true;
        }

        // Safety fallback: auto-hide loading screen if game takes longer than 10s
        if (loadingOverlay != null) {
            loadingOverlay.postDelayed(this::hideLoadingOverlay, 10000);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveMode();
        }
    }

    private void applyImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        if (LauncherPrefs.isImmersiveFullscreen(this)) {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    @Override
    protected void onPostResume() {
        super.onPostResume();
        if (shouldLaunchInitialDirectorySelection && !directorySelectionInProgress) {
            shouldLaunchInitialDirectorySelection = false;
            showDirectorySelection(false);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        try {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        } catch (UnsatisfiedLinkError e) {
            Log.d(TAG, "SDL2 nativePermissionResult not available: " + e.getMessage());
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == GET_FOLDER_RESULT) {
            directorySelectionInProgress = false;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) {
                try {
                    getContext().getContentResolver().takePersistableUriPermission(data.getData(),
                            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                } catch (SecurityException e) {
                    Log.w(TAG, "Persistable URI permission was not granted", e);
                }
                FileManager.setBaseUri(this, data.getData());
            } else if (!FileManager.hasAccessibleBaseUri(this)) {
                FileManager.setBaseUri(this, Uri.EMPTY);
            }
            gotDirectory();
        } else if (requestCode == IN_GAME_SETTINGS_RESULT) {
            startupLogFeatureEnabled = LauncherPrefs.isShowLog(this);
            startupLogVisible = startupLogFeatureEnabled;
            updateStartupLogVisibility();
            applyImmersiveMode();
            applySideBarPreferences();
            try {
                nativeSetTouchMode(LauncherPrefs.getTouchMode(this));
            } catch (UnsatisfiedLinkError ignored) {
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public float getScreenDensity() {
        return getResources().getDisplayMetrics().density;
    }

    @SuppressWarnings("unused")
    public void hideLoadingOverlay() {
        runOnUiThread(() -> {
            if (loadingOverlay != null && loadingOverlay.getVisibility() == View.VISIBLE) {
                loadingOverlay.animate().alpha(0f).setDuration(300).withEndAction(() ->
                        loadingOverlay.setVisibility(View.GONE)).start();
            }
        });
    }

    @SuppressWarnings("unused")
    public void clearStartupLog() {
        runOnUiThread(() -> {
            if (startupLogView != null) {
                startupLogView.setText("");
            }
            updateStartupLogVisibility();
        });
    }

    @SuppressWarnings("unused")
    public void appendStartupLog(String message) {
        runOnUiThread(() -> {
            if (startupLogView == null) {
                return;
            }
            CharSequence existing = startupLogView.getText();
            String next = (existing == null || existing.length() == 0)
                    ? message
                    : existing + "\n" + message;
            if (next.length() > STARTUP_LOG_MAX_CHARS) {
                next = next.substring(next.length() - STARTUP_LOG_MAX_CHARS);
            }
            startupLogView.setText(next);
            if (startupLogScroll != null && startupLogVisible) {
                startupLogScroll.post(() -> startupLogScroll.fullScroll(ScrollView.FOCUS_DOWN));
            }
        });
    }

    @SuppressWarnings("unused")
    public void setStartupLogVisible(boolean visible) {
        startupLogVisible = visible;
        runOnUiThread(this::updateStartupLogVisibility);
    }

    private void updateStartupLogVisibility() {
        boolean show = startupLogFeatureEnabled && startupLogVisible;
        if (startupLogScroll != null) {
            startupLogScroll.setVisibility(show ? ScrollView.VISIBLE : ScrollView.GONE);
        }
        if (startupLogToggleButton != null) {
            startupLogToggleButton.setVisibility(startupLogFeatureEnabled ? Button.VISIBLE : Button.GONE);
            startupLogToggleButton.setText(show ? "Hide log" : "Log");
        }
    }

    private void installPharaohLoadingOverlay() {
        boolean id = LauncherPrefs.getLanguage(this).equals("id");
        loadingOverlay = new FrameLayout(this);
        loadingOverlay.setBackgroundColor(0xFF120E09);

        LinearLayout centerBox = new LinearLayout(this);
        centerBox.setOrientation(LinearLayout.VERTICAL);
        centerBox.setGravity(Gravity.CENTER);

        TextView title = new TextView(this);
        title.setText("AKHENATEN");
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setLetterSpacing(0.16f);
        title.setGravity(Gravity.CENTER);

        TextView subtitle = new TextView(this);
        subtitle.setText(id ? "Memuat Aset Pharaoh + Cleopatra..." : "Loading Pharaoh + Cleopatra Assets...");
        subtitle.setTextColor(0xFFE8D5B5);
        subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        subLp.topMargin = dp(8);
        subtitle.setLayoutParams(subLp);

        ProgressBar bar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        bar.setIndeterminate(true);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(dp(220), dp(12));
        barLp.topMargin = dp(16);
        bar.setLayoutParams(barLp);

        centerBox.addView(title);
        centerBox.addView(subtitle);
        centerBox.addView(bar);

        FrameLayout.LayoutParams centerLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        loadingOverlay.addView(centerBox, centerLp);

        addContentView(loadingOverlay, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void installStartupLogOverlay() {
        startupLogView = new TextView(this);
        startupLogView.setTextColor(Color.WHITE);
        startupLogView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        startupLogView.setPadding(dp(8), dp(8), dp(8), dp(8));

        startupLogScroll = new ScrollView(this);
        startupLogScroll.setFillViewport(true);
        startupLogScroll.setBackgroundColor(0xAA000000);
        startupLogScroll.addView(startupLogView, new ScrollView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(160),
                Gravity.TOP);
        params.leftMargin = dp(8);
        params.topMargin = dp(8);
        params.rightMargin = dp(8);
        addContentView(startupLogScroll, params);

        startupLogToggleButton = new Button(this);
        startupLogToggleButton.setText("Hide log");
        startupLogToggleButton.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        startupLogToggleButton.setOnClickListener(v -> setStartupLogVisible(!startupLogVisible));

        FrameLayout.LayoutParams buttonParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM | Gravity.START);
        buttonParams.leftMargin = dp(8);
        buttonParams.bottomMargin = dp(8);
        addContentView(startupLogToggleButton, buttonParams);
    }

    private void installPharaohSideControls() {
        sideBarContainer = new LinearLayout(this);
        sideBarContainer.setOrientation(LinearLayout.HORIZONTAL);
        sideBarContainer.setGravity(Gravity.CENTER_VERTICAL);

        // Hotkeys Drawer (hidden until ⌨ is tapped)
        hotkeysDrawer = new LinearLayout(this);
        hotkeysDrawer.setOrientation(LinearLayout.VERTICAL);
        hotkeysDrawer.setPadding(dp(8), dp(8), dp(8), dp(8));
        hotkeysDrawer.setVisibility(View.GONE);
        GradientDrawable drawerBg = new GradientDrawable();
        drawerBg.setColor(0xDD1A130C);
        drawerBg.setCornerRadius(dp(12));
        drawerBg.setStroke(dp(1), 0xFFD4AF37);
        hotkeysDrawer.setBackground(drawerBg);

        addHotkeyRow(hotkeysDrawer, "Map ↺", SDLK_LEFTBRACKET, "Map ↻", SDLK_RIGHTBRACKET);
        addHotkeyRow(hotkeysDrawer, "Copy", SDLK_C, "Overlays", SDLK_V);
        addHotkeyRow(hotkeysDrawer, "Q-Save", SDLK_F5, "Q-Load", SDLK_F9);
        addHotkeyRow(hotkeysDrawer, "Speed -", SDLK_MINUS, "Speed +", SDLK_EQUALS);
        addHotkeyRow(hotkeysDrawer, "Advisors", SDLK_1, "Launcher", -1);

        LinearLayout.LayoutParams drawerLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        drawerLp.rightMargin = dp(6);
        sideBarContainer.addView(hotkeysDrawer, drawerLp);

        // Minimalist Right Quick-Bar
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.VERTICAL);
        pill.setGravity(Gravity.CENTER_HORIZONTAL);
        pill.setPadding(dp(4), dp(6), dp(4), dp(6));

        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xCC16110B);
        bg.setCornerRadius(dp(14));
        bg.setStroke(dp(1), 0xAA8C6D2E);
        pill.setBackground(bg);

        collapseToggleBtn = createSideButton("▶", v -> {
            sideBarExpanded = !sideBarExpanded;
            quickBarButtons.setVisibility(sideBarExpanded ? View.VISIBLE : View.GONE);
            if (!sideBarExpanded) {
                hotkeysVisible = false;
                hotkeysDrawer.setVisibility(View.GONE);
            }
            collapseToggleBtn.setText(sideBarExpanded ? "▶" : "◀");
        });
        pill.addView(collapseToggleBtn);

        quickBarButtons = new LinearLayout(this);
        quickBarButtons.setOrientation(LinearLayout.VERTICAL);
        quickBarButtons.setGravity(Gravity.CENTER_HORIZONTAL);
        quickBarButtons.addView(createSideButton("▲", v -> nativeInjectScroll(1)));
        quickBarButtons.addView(createSideButton("▼", v -> nativeInjectScroll(-1)));
        quickBarButtons.addView(createSideButton("⏸", v -> nativeInjectKey(SDLK_P)));
        quickBarButtons.addView(createSideButton("↻", v -> nativeInjectKey(SDLK_R)));
        quickBarButtons.addView(createSideButton("👁", v -> nativeInjectKey(SDLK_F)));
        quickBarButtons.addView(createSideButton("✕", v -> nativeInjectRightClick()));
        quickBarButtons.addView(createSideButton("⌨", v -> {
            hotkeysVisible = !hotkeysVisible;
            hotkeysDrawer.setVisibility(hotkeysVisible ? View.VISIBLE : View.GONE);
        }));
        quickBarButtons.addView(createSideButton("⚙", v -> openInGameLauncher()));
        pill.addView(quickBarButtons);

        sideBarContainer.addView(pill);

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.END | Gravity.CENTER_VERTICAL);
        lp.rightMargin = dp(6);
        addContentView(sideBarContainer, lp);
    }

    private void addHotkeyRow(LinearLayout parent, String label1, int key1, String label2, int key2) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(createDrawerButton(label1, v -> {
            nativeInjectKey(key1);
            hotkeysVisible = false;
            hotkeysDrawer.setVisibility(View.GONE);
        }));
        row.addView(createDrawerButton(label2, v -> {
            if (key2 == -1) {
                openInGameLauncher();
            } else {
                nativeInjectKey(key2);
            }
            hotkeysVisible = false;
            hotkeysDrawer.setVisibility(View.GONE);
        }));
        parent.addView(row);
    }

    private Button createDrawerButton(String label, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(0xFFF3E5AB);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11);
        btn.setAllCaps(false);
        btn.setPadding(dp(6), dp(2), dp(6), dp(2));

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xFF2B2114);
        pill.setCornerRadius(dp(8));
        pill.setStroke(dp(1), 0x88D4AF37);
        btn.setBackground(pill);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(74), dp(34));
        lp.setMargins(dp(3), dp(3), dp(3), dp(3));
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private Button createSideButton(String symbol, View.OnClickListener listener) {
        Button btn = new Button(this);
        btn.setText(symbol);
        btn.setTextColor(0xFFD4AF37);
        btn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        btn.setAllCaps(false);
        btn.setPadding(0, 0, 0, 0);

        GradientDrawable pill = new GradientDrawable();
        pill.setColor(0xAA261D12);
        pill.setCornerRadius(dp(9));
        pill.setStroke(dp(1), 0x66D4AF37);
        btn.setBackground(pill);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(34), dp(32));
        lp.topMargin = dp(2);
        lp.bottomMargin = dp(2);
        btn.setLayoutParams(lp);
        btn.setOnClickListener(listener);
        return btn;
    }

    private void applySideBarPreferences() {
        if (sideBarContainer != null) {
            sideBarContainer.setVisibility(LauncherPrefs.isSideBarEnabled(this) ? View.VISIBLE : View.GONE);
        }
    }

    private void openInGameLauncher() {
        startActivityForResult(DirectorySelectionActivity.newInGameSettingsIntent(this), IN_GAME_SETTINGS_RESULT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private native void gotDirectory();
    private native void nativeInjectScroll(int delta);
    private native void nativeInjectRightClick();
    private native void nativeSetTouchMode(int modeIndex);
    private native void nativeInjectKey(int sdlKeycode);
}
