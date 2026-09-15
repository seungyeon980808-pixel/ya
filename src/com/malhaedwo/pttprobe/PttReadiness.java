package com.malhaedwo.pttprobe;

import android.content.Context;

/** User intent, not a claim that Android currently permits a microphone FGS. */
public final class PttReadiness {
    private static final String PREFS = "ptt_readiness";
    private PttReadiness() {}
    public static boolean isEnabled(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("desired_enabled", false);
    }
    public static boolean setEnabled(Context context, boolean enabled) {
        // Explicit stop must reach disk before any queued/system restore is considered.
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
                .putBoolean("desired_enabled", enabled).commit();
    }
}
