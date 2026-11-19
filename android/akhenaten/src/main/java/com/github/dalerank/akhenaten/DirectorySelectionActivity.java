package com.github.dalerank.akhenaten;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SwitchCompat;

public class DirectorySelectionActivity extends AppCompatActivity {
    private static final String ARG_SKIP_INSTRUCTIONS = "arg_skip_instructions";
    private static final String ARG_FROM_GAME = "arg_from_game";
    private static final int RW_FLAGS_PERMISSION =
            Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;

    private TextView statusBadge;
    private TextView folderStatusText;
    private Button selectFolderBtn;
    private Button playGameBtn;
    private boolean launchedFromGame = false;

    public static Intent newIntent(Context context, boolean skipInstructions) {
        Intent intent = new Intent(context, DirectorySelectionActivity.class);
        intent.putExtra(ARG_SKIP_INSTRUCTIONS, skipInstructions);
        intent.putExtra(ARG_FROM_GAME, true);
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
                refreshFolderStatus();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        applyImmersiveDisplay();

        launchedFromGame = getIntent().getBooleanExtra(ARG_FROM_GAME, false);
        FileManager.restoreBaseUri(this);

        if (!launchedFromGame
                && LauncherPrefs.isAutoLaunch(this)
                && FileManager.hasAccessibleBaseUri(this)) {
            launchGameActivity();
            finish();
            return;
        }

        setContentView(R.layout.activity_directory_selection);

        statusBadge = findViewById(R.id.status_badge);
        folderStatusText = findViewById(R.id.folder_status_text);
        selectFolderBtn = findViewById(R.id.directory_selector_button);
        playGameBtn = findViewById(R.id.play_game_button);

        SwitchCompat switchImmersive = findViewById(R.id.switch_immersive);
        SwitchCompat switchShowLog = findViewById(R.id.switch_show_log);
        SwitchCompat switchAutoLaunch = findViewById(R.id.switch_auto_launch);
        SwitchCompat switchSideBar = findViewById(R.id.switch_side_bar);
        Button btnTouchMode = findViewById(R.id.btn_touch_mode);

        switchImmersive.setChecked(LauncherPrefs.isImmersiveFullscreen(this));
        switchShowLog.setChecked(LauncherPrefs.isShowLog(this));
        switchAutoLaunch.setChecked(LauncherPrefs.isAutoLaunch(this));
        switchSideBar.setChecked(LauncherPrefs.isSideBarEnabled(this));
        updateTouchModeButton(btnTouchMode);

        switchImmersive.setOnCheckedChangeListener((b, checked) -> {
            LauncherPrefs.setImmersiveFullscreen(this, checked);
            applyImmersiveDisplay();
        });
        switchShowLog.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setShowLog(this, checked));
        switchAutoLaunch.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setAutoLaunch(this, checked));
        switchSideBar.setOnCheckedChangeListener((b, checked) ->
                LauncherPrefs.setSideBarEnabled(this, checked));
        btnTouchMode.setOnClickListener(v -> {
            int next = (LauncherPrefs.getTouchMode(this) + 1) % 3;
            LauncherPrefs.setTouchMode(this, next);
            updateTouchModeButton(btnTouchMode);
        });

        selectFolderBtn.setOnClickListener(v -> launchDirectoryPicker());
        playGameBtn.setOnClickListener(v -> {
            launchGameActivity();
            finish();
        });

        refreshFolderStatus();

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

    private void refreshFolderStatus() {
        boolean ready = FileManager.hasAccessibleBaseUri(this);
        if (ready) {
            statusBadge.setText("READY TO PLAY");
            statusBadge.setTextColor(Color.parseColor("#80CBC4"));
            statusBadge.setBackgroundColor(Color.parseColor("#13302B"));
            folderStatusText.setText("Folder aktif tersimpan:\n" + FileManager.getDisplayName(this)
                    + "\n\nFolder ini diingat otomatis. Anda tidak perlu memilih ulang setiap membuka game.");
            selectFolderBtn.setText("Change Folder");
            playGameBtn.setEnabled(true);
            playGameBtn.setAlpha(1.0f);
        } else {
            statusBadge.setText("NEEDS GAME DATA");
            statusBadge.setTextColor(Color.parseColor("#D4AF37"));
            statusBadge.setBackgroundColor(Color.parseColor("#2B2112"));
            folderStatusText.setText(R.string.directory_selector_instructions);
            selectFolderBtn.setText("Select Pharaoh Folder");
            playGameBtn.setEnabled(false);
            playGameBtn.setAlpha(0.45f);
        }
    }

    private void updateTouchModeButton(Button btn) {
        int mode = LauncherPrefs.getTouchMode(this);
        String label = switch (mode) {
            case 1 -> "Touch: Trackpad Mode";
            case 2 -> "Touch: Direct Cursor";
            default -> "Touch: City-Builder (Original)";
        };
        btn.setText(label);
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
