package com.malhaedwo.pttprobe;

import android.content.Context;
import android.os.CombinedVibration;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationAttributes;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;

public final class Haptics {
    private static final VibrationAttributes ATTRIBUTES = new VibrationAttributes.Builder()
            .setUsage(VibrationAttributes.USAGE_ALARM)
            .build();

    private Haptics() {}

    public static void start(Context context) {
        vibrate(context, VibrationEffect.createOneShot(80, 255));
    }

    public static void stop(Context context) {
        vibrate(context, VibrationEffect.createWaveform(
                new long[]{0, 70, 90, 70}, new int[]{0, 255, 0, 255}, -1));
    }

    public static void warning(Context context) {
        vibrate(context, VibrationEffect.createOneShot(130, 255));
    }

    public static void error(Context context) {
        vibrate(context, VibrationEffect.createOneShot(500, 255));
    }

    public static void runTest(Context context) {
        Context app = context.getApplicationContext();
        start(app);
        new Handler(Looper.getMainLooper()).postDelayed(() -> stop(app), 900L);
    }

    public static String diagnostic(Context context) {
        Vibrator vibrator = defaultVibrator(context);
        if (vibrator == null) return "진동기 서비스를 찾을 수 없음";
        if (!vibrator.hasVibrator()) return "기기에 진동기 없음";
        int intensity;
        try { intensity = vibrator.getDefaultVibrationIntensity(VibrationAttributes.USAGE_ALARM); }
        catch (Throwable ignored) { intensity = -1; }
        return "진동기 있음 · 진폭 제어 " + (vibrator.hasAmplitudeControl() ? "지원" : "미지원") +
                " · 알림 진동 강도 " + intensity;
    }

    private static void vibrate(Context context, VibrationEffect effect) {
        try {
            VibratorManager manager = context.getSystemService(VibratorManager.class);
            if (manager != null) {
                manager.vibrate(CombinedVibration.createParallel(effect), ATTRIBUTES);
                return;
            }
            Vibrator vibrator = defaultVibrator(context);
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(effect, ATTRIBUTES);
        } catch (Throwable error) {
            PttStore.append(context, "HAPTIC_ERROR", "vibrator",
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private static Vibrator defaultVibrator(Context context) {
        VibratorManager manager = context.getSystemService(VibratorManager.class);
        if (manager != null) return manager.getDefaultVibrator();
        return context.getSystemService(Vibrator.class);
    }
}
