package com.rinigan.app;

import android.util.Log;

/**
 * Logging utility — uses XposedBridge.log in Xposed environments, falls back to android.util.Log otherwise.
 */
public class LogUtil {
    private static final String TAG = "[Rinigan]";

    private static volatile boolean xposedAvailable = false;
    private static volatile boolean xposedChecked = false;

    private static boolean isXposedAvailable() {
        if (!xposedChecked) {
            try {
                Class.forName("de.robv.android.xposed.XposedBridge");
                xposedAvailable = true;
            } catch (ClassNotFoundException e) {
                xposedAvailable = false;
            }
            xposedChecked = true;
        }
        return xposedAvailable;
    }

    public static void log(String msg) {
        if (msg != null) {
            if (isXposedAvailable()) {
                try {
                    de.robv.android.xposed.XposedBridge.log(TAG + msg);
                    return;
                } catch (Throwable ignored) {}
            }
            Log.d(TAG, msg);
        }
    }

    public static void log(String prefix, String msg) {
        log(prefix + msg);
    }
}