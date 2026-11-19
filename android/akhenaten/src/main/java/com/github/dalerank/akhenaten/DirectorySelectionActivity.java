package com.github.dalerank.akhenaten;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class DirectorySelectionActivity extends AppCompatActivity {
    private static final String ARG_SKIP_INSTRUCTIONS = "arg_skip_instructions";
    private static final String ARG_FROM_GAME = "arg_from_game";
    private static final String ARG_IN_GAME_SETTINGS = "arg_in_game_settings";
    private static final int RW_FLAGS_PERMISSION =
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private TextView statusBadge;
    private TextView folderStatusText;
    private TextView headerSubtitle;
    private TextView playCardTitle;
    private TextView gfxCardTitle;
    private TextView ctrlCardTitle;
    private TextView controlsGuideText;

    private Button tabPlay;
    private Button tabGraphics;
    private Button tabControls;
    private Button btnLanguage;
    private Button selectFolderBtn;
    private Button playGameBtn;
    private Button btnScreenFit;
    private Button btnUiScale;
    private Button btnTouchMode;

    private SwitchCompat switchImmersive;
    private SwitchCompat switchShowFps;
    private SwitchCompat switchShowLog;
    private SwitchCompat switchSideBar;
    private SwitchCompat switchAutoLaunch;

    private LinearLayout panelPlay;
    private LinearLayout panelGraphics;
    private LinearLayout panelControls;

    private boolean launchedFromGame = false;
    private boolean inGameSettings = false;
    private int activeTab = 0;

    public static Intent newIntent(Context context, boolean skipInstructions) {
        Intent intent = new Intent(context, DirectorySelectionActivity.class);
        intent.putExtra(ARG_SKIP_INSTRUCTIONS, skipInstructions);
        intent.putExtra(ARG_FROM_GAME, true);
        return intent;
    }

    public static Intent newInGameSettingsIntent(Context context) {
        Intent intent = new Intent(context, DirectorySelectionActivity.class);
        intent.putExtra(ARG_IN_GAME_SETTINGS, true);
        return intent;
    }

    private final ActivityResultLauncher<Uri> directorySelectionLauncher = registerForActivityResult(
            new ActivityResultContracts.OpenDocumentTree() {
                @Override
                @NonNull
                public Intent createIntent(@NonNull Context context, Uri input) {
                    Intent intent = super.createIntent(context, input);
                    intent.addFlags(RW_FLAGS_PERMISSION
                            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION
                            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);

                    Uri initialUri = FileManager.getBaseUri(context);
                    if (initialUri != null && !Uri.EMPTY.equals(initialUri)
                            && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, initialUri);
                    }

                    intent.putExtra("android.content.extra.SHOW_ADVANCED", true);
                    intent.putExtra("android.content.extra.FANCY", true);
                    intent.putExtra("android.content.extra.SHOW_FILESIZE", true);
                    return intent;
                }
            },
            uri -> {
                if (uri != null) {
                    try {
                        getContentResolver().takePersistableUriPermission(uri, RW_FLAGS_PERMISSION);
                    } catch (SecurityException ignored) {
                    }
                    FileManager.setBaseUri(this, uri);
                    if (launchedFromGame) {
                        Intent result = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                        result.addCategory(Intent.CATEGORY_OPENABLE);
                        result.setType("*/*");
                        result.setData(uri);
                        setResult(RESULT_OK, result);
                        finish();
                        return;
                    }
                } else if (launchedFromGame) {
                    setResult(RESULT_CANCELED);
                    finish();
                    return;
                }
                applyLanguageTexts();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        super.onCreate(savedInstanceState);
        applyImmersiveDisplay();

        launchedFromGame = getIntent().getBooleanExtra(ARG_FROM_GAME, false);
        inGameSettings = getIntent().getBooleanExtra(ARG_IN_GAME_SETTINGS, false);
        FileManager.restoreBaseUri(this);

        if (!launchedFromGame
                && !inGameSettings
                && LauncherPrefs.isAutoLaunch(this)
                && FileManager.hasAccessibleBaseUri(this)) {
            launchGameActivity();
            finish();
            return;
        }

        setContentView(R.layout.activity_directory_selection);
        bindViews();
        bindListeners();
        selectTab(inGameSettings ? 1 : 0);
        applyLanguageTexts();

        if (savedInstanceState == null && getIntent().getBooleanExtra(ARG_SKIP_INSTRUCTIONS, false)) {
            launchDirectoryPicker();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            applyImmersiveDisplay();
        }
    }

    private void bindViews() {
        statusBadge = findViewById(R.id.status_badge);
        folderStatusText = findViewById(R.id.folder_status_text);
        headerSubtitle = findViewById(R.id.header_subtitle);
        playCardTitle = findViewById(R.id.play_card_title);
        gfxCardTitle = findViewById(R.id.gfx_card_title);
        ctrlCardTitle = findViewById(R.id.ctrl_card_title);
        controlsGuideText = findViewById(R.id.controls_guide_text);

        tabPlay = findViewById(R.id.tab_play);
        tabGraphics = findViewById(R.id.tab_graphics);
        tabControls = findViewById(R.id.tab_controls);
        btnLanguage = findViewById(R.id.btn_language);
        selectFolderBtn = findViewById(R.id.directory_selector_button);
        playGameBtn = findViewById(R.id.play_game_button);
        btnScreenFit = findViewById(R.id.btn_screen_fit);
        btnUiScale = findViewById(R.id.btn_ui_scale);
        btnTouchMode = findViewById(R.id.btn_touch_mode);

        switchImmersive = findViewById(R.id.switch_immersive);
        switchShowFps = findViewById(R.id.switch_show_fps);
        switchShowLog = findViewById(R.id.switch_show_log);
        switchSideBar = findViewById(R.id.switch_side_bar);
        switchAutoLaunch = findViewById(R.id.switch_auto_launch);

        panelPlay = findViewById(R.id.panel_play);
        panelGraphics = findViewById(R.id.panel_graphics);
        panelControls = findViewById(R.id.panel_controls);

        switchImmersive.setChecked(LauncherPrefs.isImmersiveFullscreen(this));
        switchShowFps.setChecked(LauncherPrefs.isShowFps(this));
        switchShowLog.setChecked(LauncherPrefs.isShowLog(this));
        switchSideBar.setChecked(LauncherPrefs.isSideBarEnabled(this));
        switchAutoLaunch.setChecked(LauncherPrefs.isAutoLaunch(this));
    }

    private void bindListeners() {
        tabPlay.setOnClickListener(v -> selectTab(0));
        tabGraphics.setOnClickListener(v -> selectTab(1));
        tabControls.setOnClickListener(v -> selectTab(2));

        btnLanguage.setOnClickListener(v -> {
            String next = LauncherPrefs.getLanguage(this).equals("id") ? "en" : "id";
            LauncherPrefs.setLanguage(this, next);
            applyLanguageTexts();
        });

        switchImmersive.setOnCheckedChangeListener((b, checked) -> {
            LauncherPrefs.setImmersiveFullscreen(this, checked);
            applyImmersiveDisplay();
        });
        switchShowFps.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setShowFps(this, checked));
        switchShowLog.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setShowLog(this, checked));
        switchSideBar.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setSideBarEnabled(this, checked));
        switchAutoLaunch.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setAutoLaunch(this, checked));

        btnScreenFit.setOnClickListener(v -> {
            int next = (LauncherPrefs.getScreenFitMode(this) + 1) % 3;
            LauncherPrefs.setScreenFitMode(this, next);
            applyLanguageTexts();
        });

        btnUiScale.setOnClickListener(v -> {
            int next = (LauncherPrefs.getUiScaleIdx(this) + 1) % 4;
            LauncherPrefs.setUiScaleIdx(this, next);
            applyLanguageTexts();
        });

        btnTouchMode.setOnClickListener(v -> {
            int next = (LauncherPrefs.getTouchMode(this) + 1) % 3;
            LauncherPrefs.setTouchMode(this, next);
            applyLanguageTexts();
        });

        selectFolderBtn.setOnClickListener(v -> launchDirectoryPicker());
        playGameBtn.setOnClickListener(v -> {
            if (inGameSettings) {
                setResult(RESULT_OK);
                finish();
            } else {
                launchGameActivity();
                finish();
            }
        });
    }

    private void selectTab(int idx) {
        activeTab = idx;
        panelPlay.setVisibility(idx == 0 ? View.VISIBLE : View.GONE);
        panelGraphics.setVisibility(idx == 1 ? View.VISIBLE : View.GONE);
        panelControls.setVisibility(idx == 2 ? View.VISIBLE : View.GONE);

        styleTabButton(tabPlay, idx == 0);
        styleTabButton(tabGraphics, idx == 1);
        styleTabButton(tabControls, idx == 2);
    }

    private void styleTabButton(Button btn, boolean active) {
        btn.setBackgroundColor(active ? Color.parseColor("#D4AF37") : Color.parseColor("#261D12"));
        btn.setTextColor(active ? Color.parseColor("#120E09") : Color.parseColor("#E8D5B5"));
    }

    private void applyLanguageTexts() {
        boolean id = LauncherPrefs.getLanguage(this).equals("id");
        boolean ready = FileManager.hasAccessibleBaseUri(this);

        btnLanguage.setText(id ? "ID" : "EN");
        headerSubtitle.setText(id
                ? "Pharaoh + Cleopatra • Engine ARM64 Native"
                : "Pharaoh + Cleopatra • Native ARM64 Engine");

        tabPlay.setText(id ? "Main & Data" : "Play & Data");
        tabGraphics.setText(id ? "Grafis & Layar" : "Graphics & Display");
        tabControls.setText(id ? "Kontrol & Hotkey" : "Controls & Hotkeys");

        playCardTitle.setText(id ? "Data Game Pharaoh (Android SAF)" : "Pharaoh Game Data (Android SAF)");
        gfxCardTitle.setText(id ? "Pengaturan Grafis & Layar Penuh" : "Graphics & Fullscreen Display");
        ctrlCardTitle.setText(id ? "Kontrol Sentuh, Hotkeys & Panduan" : "Touch Controls, Hotkeys & Guide");

        switchImmersive.setText(id
                ? "Fullscreen Immersive (Sembunyikan Notch & Bar)"
                : "Immersive Fullscreen (Hide Notch & Bars)");
        switchShowFps.setText(id
                ? "Tampilkan Info Performa (FPS)"
                : "Show Performance HUD (FPS)");
        switchShowLog.setText(id
                ? "Tampilkan Log Debug (Troubleshooting)"
                : "Show Debug Log Overlay");
        switchSideBar.setText(id
                ? "Bar Quick-Action & Hotkey di Samping Layar"
                : "Minimalist Side Quick-Bar & Hotkeys");
        switchAutoLaunch.setText(id
                ? "Langsung Masuk Game Saat Aplikasi Dibuka"
                : "Auto-Launch Game on Startup");

        int fit = LauncherPrefs.getScreenFitMode(this);
        String fitName = switch (fit) {
            case 1 -> id ? "Layar: Tajam (Pixel)" : "Display: Crisp Pixel";
            case 2 -> id ? "Layar: Widescreen 20:9" : "Display: Widescreen 20:9";
            default -> id ? "Layar: Fullscreen Fit" : "Display: Fullscreen Fit";
        };
        btnScreenFit.setText(fitName);

        int scaleIdx = LauncherPrefs.getUiScaleIdx(this);
        String scaleVal = switch (scaleIdx) {
            case 1 -> "1.0x";
            case 2 -> "1.25x";
            case 3 -> "1.5x";
            default -> "Auto";
        };
        btnUiScale.setText((id ? "Skala UI: " : "UI Scale: ") + scaleVal);

        int mode = LauncherPrefs.getTouchMode(this);
        String modeName = switch (mode) {
            case 1 -> id ? "Mode Sentuh: Trackpad" : "Touch: Trackpad Mode";
            case 2 -> id ? "Mode Sentuh: Kursor Langsung" : "Touch: Direct Cursor";
            default -> id ? "Mode Sentuh: City-Builder" : "Touch: City-Builder";
        };
        btnTouchMode.setText(modeName);

        controlsGuideText.setText(id
                ? "PANDUAN KONTROL SENTUH:\n"
                + "• Tap 1 Jari: Klik / Bangun / Pilih menu\n"
                + "• Drag 1 Jari: Geser peta kota / tarik jalan & rumah\n"
                + "• Cubit 2 Jari (Pinch): Zoom In / Zoom Out peta\n"
                + "• Geser 2 Jari: Scroll daftar / menu\n"
                + "• Quick-Bar Kanan: Pause (⏸), Speed (⏩), Putar (↻), Flat (👁), Hotkeys (⌨), Launcher (⚙)"
                : "TOUCH CONTROLS GUIDE:\n"
                + "• 1-Finger Tap: Click / Build / Select menu\n"
                + "• 1-Finger Drag: Pan city map / drag roads & houses\n"
                + "• 2-Finger Pinch: Zoom In / Zoom Out map\n"
                + "• 2-Finger Scroll: Scroll lists & menus\n"
                + "• Right Quick-Bar: Pause (⏸), Speed (⏩), Rotate (↻), Flat (👁), Hotkeys (⌨), Launcher (⚙)");

        if (ready) {
            statusBadge.setText(id ? "SIAP MAIN" : "READY TO PLAY");
            statusBadge.setTextColor(Color.parseColor("#80CBC4"));
            statusBadge.setBackgroundColor(Color.parseColor("#13302B"));
            folderStatusText.setText((id
                    ? "Folder data tersimpan:\n"
                    : "Saved data folder:\n")
                    + FileManager.getDisplayName(this)
                    + (id
                    ? "\n\nFolder ini diingat otomatis. Anda tidak perlu memilih ulang setiap kali membuka aplikasi."
                    : "\n\nThis folder is remembered automatically across launches."));
            selectFolderBtn.setText(id ? "Ganti Folder" : "Change Folder");
            playGameBtn.setText(inGameSettings
                    ? (id ? "KEMBALI KE GAME" : "RESUME GAME")
                    : (id ? "MAIN SEKARANG" : "PLAY PHARAOH"));
            playGameBtn.setEnabled(true);
            playGameBtn.setAlpha(1.0f);
        } else {
            statusBadge.setText(id ? "BUTUH DATA" : "NEEDS DATA");
            statusBadge.setTextColor(Color.parseColor("#D4AF37"));
            statusBadge.setBackgroundColor(Color.parseColor("#2B2112"));
            folderStatusText.setText(id
                    ? "Pilih folder instalasi asli Pharaoh + Cleopatra di HP Anda menggunakan Android SAF."
                    : getString(R.string.directory_selector_instructions));
            selectFolderBtn.setText(id ? "Pilih Folder Pharaoh" : "Select Pharaoh Folder");
            playGameBtn.setText(id ? "MAIN SEKARANG" : "PLAY PHARAOH");
            playGameBtn.setEnabled(false);
            playGameBtn.setAlpha(0.45f);
        }
    }

    private void launchDirectoryPicker() {
        Uri initial = FileManager.getBaseUri(this);
        directorySelectionLauncher.launch(Uri.EMPTY.equals(initial) ? null : initial);
    }

    private void launchGameActivity() {
        Intent intent = new Intent(this, AkhenatenMainActivity.class);
        startActivity(intent);
    }

    private void applyImmersiveDisplay() {
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
}
