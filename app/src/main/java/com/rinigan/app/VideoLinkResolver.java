package com.rinigan.app;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * v6 — Video Link support.
 *
 * Resolves a remote video link (http/https) from the stream settings into a
 * local cache file so that every existing camera pipeline (Camera1 MediaPlayer,
 * VideoToFrames decoder, Camera2 relay, VideoProvider PFD) keeps working
 * unchanged. The download happens once per URL; subsequent camera sessions
 * reuse the cached file until the link changes.
 *
 * Fail-soft design: any problem returns null and the caller falls back to the
 * local video (stream_local_fallback behavior).
 */
public final class VideoLinkResolver {

    private VideoLinkResolver() {}

    /** Marker file suffix that stores the source URL of a cached video. */
    public static final String LINK_MARKER_SUFFIX = ".url";

    private static final String CACHE_PREFIX = "link_cache_";
    private static final String PART_SUFFIX = ".part";
    private static final int CONNECT_TIMEOUT_MS = 10_000;
    private static final int READ_TIMEOUT_MS = 30_000;
    private static final long MAX_SIZE_BYTES = 2L * 1024L * 1024L * 1024L; // 2 GB safety cap

    /** per-cache-name download locks so parallel camera sessions never double-download */
    private static final ConcurrentHashMap<String, Object> LOCKS = new ConcurrentHashMap<>();

    // ======================== Public API ========================

    /**
     * Returns a playable local file for the active media source.
     * Stream mode + http(s) URL → cached download (downloads on first use).
     * Anything else → null (caller falls back to the local video file).
     */
    public static File resolveStreamCache() {
        try {
            ConfigManager config = HookGuards.getConfig();
            String type = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE,
                    ConfigManager.MEDIA_SOURCE_LOCAL);
            if (!ConfigManager.MEDIA_SOURCE_STREAM.equals(type)) return null;

            String url = config.getString(ConfigManager.KEY_STREAM_URL, "");
            if (url == null || url.trim().isEmpty()) return null;
            url = url.trim();

            String lower = url.toLowerCase(Locale.US);
            if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
                // rtsp/rtmp/other schemes are not downloadable — leave them to the local fallback
                LogUtil.log("[Link]non-http stream URL, no cache: " + url);
                return null;
            }

            return resolveUrlToCache(url);
        } catch (Throwable t) {
            LogUtil.log("[Link]resolve failed: " + t);
            return null;
        }
    }

    /**
     * Downloads (or reuses the cache for) one URL inside the Camera1 directory.
     * Blocking while downloading; concurrent callers for the same URL share the wait.
     */
    public static File resolveUrlToCache(String url) {
        String cacheName = CACHE_PREFIX + md5(url) + guessExtension(url);
        File dir = new File(SharedState.video_path);
        if (!dir.exists()) dir.mkdirs();
        File cacheFile = new File(dir, cacheName);
        File marker = new File(dir, cacheName + LINK_MARKER_SUFFIX);

        Object lock = LOCKS.computeIfAbsent(cacheName, k -> new Object());
        synchronized (lock) {
            try {
                // 1) valid cache for the exact same URL?
                if (cacheFile.exists() && cacheFile.length() > 0 && marker.exists()) {
                    String savedUrl = readTextFile(marker).trim();
                    if (url.equals(savedUrl)) {
                        LogUtil.log("[Link]cache hit: " + cacheName);
                        return cacheFile;
                    }
                    LogUtil.log("[Link]URL changed → re-downloading " + cacheName);
                }
                // 2) download to .part then atomically rename
                LogUtil.log("[Link]downloading " + url + " → " + cacheName);
                File part = new File(dir, cacheName + PART_SUFFIX);
                downloadToFile(url, part);
                if (part.length() <= 0) {
                    part.delete();
                    LogUtil.log("[Link]download produced an empty file");
                    return null;
                }
                if (cacheFile.exists()) cacheFile.delete();
                if (!part.renameTo(cacheFile)) {
                    LogUtil.log("[Link]rename failed, copying instead");
                    copyFile(part, cacheFile);
                    part.delete();
                }
                // 3) world-readable + marker
                try {
                    cacheFile.setReadable(true, false);
                    cacheFile.setWritable(true, false);
                } catch (Throwable ignored) {}
                writeTextFile(marker, url);
                LogUtil.log("[Link]cached OK: " + cacheName + " (" + cacheFile.length() + " bytes)");
                return cacheFile;
            } catch (Throwable t) {
                LogUtil.log("[Link]download failed for " + url + ": " + t);
                return null;
            }
        }
    }

    /** True when the given file name was produced by the link cache. */
    public static boolean isLinkCacheFile(File f) {
        return f != null && f.getName().startsWith(CACHE_PREFIX);
    }

    /** Reads the source URL stored in "<name>.url" next to a video, or null. */
    public static String readSourceLink(File videoFile) {
        try {
            File marker = new File(videoFile.getParentFile(),
                    videoFile.getName() + LINK_MARKER_SUFFIX);
            if (!marker.exists()) return null;
            String url = readTextFile(marker);
            return (url == null || url.trim().isEmpty()) ? null : url.trim();
        } catch (Throwable t) {
            return null;
        }
    }

    /** Writes a source-URL marker "<name>.url" next to a downloaded video. */
    public static boolean writeSourceLink(File videoFile, String url) {
        try {
            File marker = new File(videoFile.getParentFile(),
                    videoFile.getName() + LINK_MARKER_SUFFIX);
            writeTextFile(marker, url);
            try { marker.setReadable(true, false); } catch (Throwable ignored) {}
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ======================== Internals ========================

    private static void downloadToFile(String url, File out) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setInstanceFollowRedirects(true);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent",
                    "Mozilla/5.0 (Linux; Android) AppleWebKit/537.36 Rinigan/6.0");
            int code = conn.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("HTTP " + code);
            }
            long total = conn.getContentLength();
            InputStream in = conn.getInputStream();
            FileOutputStream fos = new FileOutputStream(out);
            byte[] buf = new byte[64 * 1024];
            long read = 0;
            int n;
            try {
                while ((n = in.read(buf)) != -1) {
                    fos.write(buf, 0, n);
                    read += n;
                    if (read > MAX_SIZE_BYTES) {
                        throw new IOException("file exceeds 2GB limit");
                    }
                }
            } finally {
                try { fos.flush(); fos.close(); } catch (Throwable ignored) {}
                try { in.close(); } catch (Throwable ignored) {}
            }
            LogUtil.log("[Rinigan][Link]downloaded " + read + " bytes" +
                    (total > 0 ? " / " + total : ""));
        } finally {
            if (conn != null) try { conn.disconnect(); } catch (Throwable ignored) {}
        }
    }

    private static String guessExtension(String url) {
        try {
            String clean = url.substring(0, url.indexOf('?') > 0 ? url.indexOf('?') : url.length());
            String lower = clean.toLowerCase(Locale.US);
            String[] exts = { ".mp4", ".mov", ".avi", ".mkv" };
            for (String ext : exts) {
                if (lower.endsWith(ext)) return ext;
            }
            // try last path segment
            int slash = clean.lastIndexOf('/');
            String last = slash >= 0 ? clean.substring(slash) : clean;
            for (String ext : exts) {
                if (last.endsWith(ext)) return ext;
            }
        } catch (Throwable ignored) {}
        return ".mp4";
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] d = md.digest(s.getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : d) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Throwable t) {
            return String.valueOf(s.hashCode());
        }
    }

    private static String readTextFile(File f) {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = fis.read(buf)) != -1) bos.write(buf, 0, n);
            return new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
        } catch (Throwable t) {
            return "";
        } finally {
            try { if (fis != null) fis.close(); } catch (Throwable ignored) {}
        }
    }

    private static void writeTextFile(File f, String text) throws IOException {
        FileOutputStream fos = new FileOutputStream(f);
        try {
            fos.write(text.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            fos.flush();
        } finally {
            fos.close();
        }
    }

    private static void copyFile(File src, File dst) throws IOException {
        FileInputStream in = new FileInputStream(src);
        FileOutputStream out = new FileOutputStream(dst);
        byte[] buf = new byte[64 * 1024];
        int n;
        try {
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        } finally {
            try { in.close(); } catch (Throwable ignored) {}
            try { out.flush(); out.close(); } catch (Throwable ignored) {}
        }
    }
}
