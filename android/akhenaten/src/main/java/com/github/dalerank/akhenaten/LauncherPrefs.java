package com.github.dalerank.akhenaten;

import android.content.Context;
import android.content.SharedPreferences;

public final class LauncherPrefs {
    private static final String PREF_NAME = "akhenaten_launcher_prefs";
    private static final String KEY_IMMERSIVE = "immersive_fullscreen";
    private static final String KEY_SHOW_LOG = "show_debug_log";
    private static final String KEY_AUTO_LAUNCH = "auto_launch";
    private static final String KEY_SIDE_BAR = "side_scroll_bar";
    private static final String KEY_TOUCH_MODE = "touch_mode";
    private static final String KEY_LANG = "ui_language";
    private static final String KEY_SCREEN_FIT = "screen_fit_mode";
    private static final String KEY_UI_SCALE = "ui_scale_idx";
    private static final String KEY_SHOW_FPS = "show_fps_hud";

    private LauncherPrefs() {}

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
    }

    public static boolean isImmersiveFullscreen(Context ctx) {
        return prefs(ctx).getBoolean(KEY_IMMERSIVE, true);
    }

    public static void setImmersiveFullscreen(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_IMMERSIVE, enabled).apply();
    }

    public static boolean isShowLog(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHOW_LOG, false);
    }

    public static void setShowLog(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SHOW_LOG, enabled).apply();
    }

    public static boolean isAutoLaunch(Context ctx) {
        return prefs(ctx).getBoolean(KEY_AUTO_LAUNCH, false);
    }

    public static void setAutoLaunch(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_AUTO_LAUNCH, enabled).apply();
    }

    public static boolean isSideBarEnabled(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SIDE_BAR, true);
    }

    public static void setSideBarEnabled(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SIDE_BAR, enabled).apply();
    }

    public static int getTouchMode(Context ctx) {
        return prefs(ctx).getInt(KEY_TOUCH_MODE, 0);
    }

    public static void setTouchMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_TOUCH_MODE, mode).apply();
    }

    public static String getLanguage(Context ctx) {
        return prefs(ctx).getString(KEY_LANG, "id");
    }

    public static void setLanguage(Context ctx, String lang) {
        prefs(ctx).edit().putString(KEY_LANG, lang).apply();
    }

    public static int getScreenFitMode(Context ctx) {
        return prefs(ctx).getInt(KEY_SCREEN_FIT, 0);
    }

    public static void setScreenFitMode(Context ctx, int mode) {
        prefs(ctx).edit().putInt(KEY_SCREEN_FIT, mode).apply();
    }

    public static int getUiScaleIdx(Context ctx) {
        return prefs(ctx).getInt(KEY_UI_SCALE, 0);
    }

    public static void setUiScaleIdx(Context ctx, int idx) {
        prefs(ctx).edit().putInt(KEY_UI_SCALE, idx).apply();
    }

    public static boolean isShowFps(Context ctx) {
        return prefs(ctx).getBoolean(KEY_SHOW_FPS, false);
    }

    public static void setShowFps(Context ctx, boolean enabled) {
        prefs(ctx).edit().putBoolean(KEY_SHOW_FPS, enabled).apply();
    }
}
