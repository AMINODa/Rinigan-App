package com.rinigan.app;

import android.os.Environment;

import java.io.File;

public class HookGuards {
    private HookGuards() {}

    private static ConfigManager configManager;
    private static volatile boolean configDirty = true;

    /**
     * True when running inside the Rinigan app itself (UI / provider process).
     * The module never hooks its own package, but this guard also protects the
     * transformation engine from resolving a profile for our own camera use.
     */
    public static boolean isAppProcess() {
        try {
            return BuildConfig.APPLICATION_ID.equals(currentProcessPackage());
        } catch (Throwable t) {
            return false;
        }
    }

    private static String currentProcessPackage() {
        // in hooked processes SharedState.toast_content is the host app context
        try {
            if (SharedState.toast_content != null) {
                return SharedState.toast_content.getPackageName();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    public static void setConfigManager(ConfigManager cm) {
        configManager = cm;
        configDirty = false;
    }

    /** Marks config as stale; the next getConfig() forces a reload */
    public static void invalidateConfig() {
        configDirty = true;
        // v6: forget the memoized video-link cache so a changed URL re-resolves
        linkMemoUrl = null;
        linkMemoFile = null;
    }

    /** Ensures the ConfigManager holds a valid Context (for cross-process config reads via Provider). */
    public static void ensureConfigHasContext(android.content.Context context) {
        if (context == null) {
            LogUtil.log("[DIAG]ensureConfigHasContext → context is null, skipping");
            return;
        }
        LogUtil.log("[DIAG]ensureConfigHasContext → injecting context: " + context.getPackageName());
        if (configManager == null) {
            configManager = new ConfigManager(false);
            LogUtil.log("[DIAG]ensureConfigHasContext → creating new ConfigManager");
        }
        configManager.setSkipProviderReload(false);
        configManager.setContext(context);
        LogUtil.log("[DIAG]ensureConfigHasContext → done, configManager ready");
    }

    private static long configFileLastModified = 0;

    private static final String[] VIDEO_EXTENSIONS = { ".mp4", ".mov", ".avi", ".mkv" };

    public static ConfigManager getConfig() {
        if (configManager == null) {
            LogUtil.log("[DIAG]HookGuards.getConfig → first-time creation");
            configManager = new ConfigManager(false);
            configManager.reload();
            configFileLastModified = getConfigFileMtime();
            configDirty = false;
        } else if (configDirty) {
            LogUtil.log("[DIAG]HookGuards.getConfig → configDirty, forceReload");
            configManager.forceReload();
            configFileLastModified = getConfigFileMtime();
            configDirty = false;
        } else {
            long currentMtime = getConfigFileMtime();
            if (currentMtime > configFileLastModified) {
                LogUtil.log("[DIAG]HookGuards.getConfig → mtime changed, forceReload");
                configManager.forceReload();
                configFileLastModified = currentMtime;
            }
        }
        return configManager;
    }

    private static long getConfigFileMtime() {
        try {
            File f = new File(ConfigManager.DEFAULT_CONFIG_DIR, ConfigManager.CONFIG_FILE_NAME);
            return f.lastModified();
        } catch (Exception e) {
            return 0;
        }
    }

    // ===== v6: memo for the resolved video-link cache (avoids marker I/O per frame) =====
    private static volatile String linkMemoUrl = null;
    private static volatile File linkMemoFile = null;

    public static File getVideoFile() {
        ConfigManager config = getConfig();

        // ===== v6: video link (network stream) mode =====
        try {
            String sourceType = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE,
                    ConfigManager.MEDIA_SOURCE_LOCAL);
            if (ConfigManager.MEDIA_SOURCE_STREAM.equals(sourceType)) {
                String url = config.getString(ConfigManager.KEY_STREAM_URL, "");
                if (url != null && !url.trim().isEmpty()) {
                    String trimmed = url.trim();
                    File memo = linkMemoFile;
                    if (memo != null && trimmed.equals(linkMemoUrl) && memo.exists()) {
                        LogUtil.log("[DIAG]getVideoFile → link memo hit: " + memo.getName());
                        return memo;
                    }
                    File linkFile = VideoLinkResolver.resolveStreamCache();
                    if (linkFile != null && linkFile.exists()) {
                        linkMemoUrl = trimmed;
                        linkMemoFile = linkFile;
                        LogUtil.log("[DIAG]getVideoFile → link cache resolved: " + linkFile.getName());
                        return linkFile;
                    }
                    // download failed — respect the local-fallback switch
                    if (!config.getBoolean(ConfigManager.KEY_STREAM_LOCAL_FALLBACK, true)) {
                        LogUtil.log("[DIAG]getVideoFile → link unavailable and local fallback disabled");
                        return new File(SharedState.video_path, "virtual.mp4");
                    }
                    LogUtil.log("[DIAG]getVideoFile → link unavailable, falling back to local video");
                }
            }
        } catch (Throwable t) {
            LogUtil.log("[DIAG]getVideoFile → link resolution error: " + t);
        }

        String selectedName = config.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
        File dir = new File(SharedState.video_path);
        LogUtil.log("[DIAG]getVideoFile → dir=" + dir.getAbsolutePath() + " selected=" + selectedName);
        if (selectedName != null && !selectedName.isEmpty()) {
            // uniformly use the Camera1 directory + file name
            String name = selectedName.contains("/") ? new File(selectedName).getName() : selectedName;
            File selected = new File(dir, name);
            LogUtil.log("[DIAG]getVideoFile → trying: " + selected.getAbsolutePath());
            try {
                if (selected.exists() && !selected.isDirectory()) {
                    LogUtil.log("[DIAG]getVideoFile → hit: " + selected.getName());
                    return selected;
                }
                LogUtil.log("[DIAG]getVideoFile → not found: " + selected.getAbsolutePath());
            } catch (SecurityException e) {
                LogUtil.log("[DIAG]getVideoFile → SecurityException, trusting path: " + e);
                return selected;
            }
        } else {
            LogUtil.log("[DIAG]getVideoFile → selectedName empty, using fallback");
        }
        // fallback: any video in the directory (four formats supported)
        try {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                for (String ext : VIDEO_EXTENSIONS) {
                    if (lower.endsWith(ext)) return true;
                }
                return false;
            });
            if (files != null && files.length > 0) {
                LogUtil.log("[DIAG]getVideoFile → fallback found " + files.length + " videos, taking the first: " + files[0].getName());
                return files[0];
            }
            LogUtil.log("[DIAG]getVideoFile → fallback listFiles returned empty");
        } catch (SecurityException e) {
            LogUtil.log("[DIAG]getVideoFile → fallback listFiles threw: " + e);
        }
        LogUtil.log("[DIAG]getVideoFile → finally falling back to virtual.mp4");
        return new File(dir, "virtual.mp4");
    }

    /** JSON config takes priority; falls back to file markers when no JSON exists. */
    public static boolean isDisabled() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false)) return true;
        File controlFile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/disable.jpg");
        return controlFile.exists();
    }

    /** JSON config takes priority; falls back to file markers when no JSON exists. */
    public static boolean shouldShowToast() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false)) return false;
        File toastControl = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no_toast.jpg");
        return !toastControl.exists();
    }

    /** JSON config takes priority; falls back to file markers when no JSON exists. */
    public static boolean shouldPlaySound() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false)) return true;
        File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no-silent.jpg");
        return sfile.exists();
    }
}