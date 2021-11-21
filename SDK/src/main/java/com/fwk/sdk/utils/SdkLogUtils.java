package com.fwk.sdk.utils;

import android.util.Log;

public class SdkLogUtils {

    public static final String TAG_FWK = "FWK_SDK_";

    private static boolean VERBOSE = true;

    public static void init(boolean verbose) {
        VERBOSE = verbose;
    }

    /**
     * Print verbose log info.
     *
     * @param tag  title
     * @param info description
     */
    public static void logV(String tag, String info) {
        if (VERBOSE) {
            Log.v(tag, "[thread:" + Thread.currentThread().getName() + "] - " + info);
        }
    }
}
