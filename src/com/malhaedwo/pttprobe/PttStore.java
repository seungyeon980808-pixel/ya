package com.malhaedwo.pttprobe;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Environment;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class PttStore {
    private static final String PREFS = "ptt_probe";
    private static final String LOG_NAME = "ptt-test-log.csv";
    private static final ExecutorService LOG_EXECUTOR = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "PttProbeLog");
        thread.setDaemon(true);
        return thread;
    });

    private PttStore() {}

    public static synchronized void append(Context context, String event, String source, String detail) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt("event_count", 0) + 1;
        prefs.edit().putInt("event_count", count)
                .putString("last_event", event + " | " + detail)
                .putLong("last_event_at", System.currentTimeMillis()).apply();
        Context appContext = context.getApplicationContext();
        LOG_EXECUTOR.execute(() -> writeLog(appContext, event, source, detail));
    }

    public static synchronized void markStarted(Context context, long latencyMs, String source) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt("start_count", 0) + 1;
        prefs.edit().putInt("start_count", count).putLong("last_latency", latencyMs).apply();
        append(context, "RECORDING_STARTED", source, "latency_ms=" + latencyMs);
    }

    public static synchronized void markSaved(Context context, File file, long durationMs, int peakAmplitude, String source, boolean autoStop) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt("saved_count", 0) + 1;
        prefs.edit().putInt("saved_count", count)
                .putString("last_file", file.getAbsolutePath())
                .putLong("last_duration", durationMs)
                .putInt("last_peak", peakAmplitude)
                .putBoolean("last_auto_stop", autoStop).apply();
        append(context, "RECORDING_SAVED", source,
                "duration_ms=" + durationMs + ";bytes=" + file.length() + ";peak=" + peakAmplitude + ";auto_stop=" + autoStop + ";file=" + file.getName());
    }

    public static synchronized void markError(Context context, String source, String message) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int count = prefs.getInt("error_count", 0) + 1;
        prefs.edit().putInt("error_count", count).putString("last_error", message).apply();
        append(context, "ERROR", source, message);
    }

    public static File getLastFile(Context context) {
        String path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("last_file", null);
        if (path == null) return null;
        File file = new File(path);
        return file.isFile() ? file : null;
    }

    public static String summary(Context context) {
        SharedPreferences p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int started = p.getInt("start_count", 0);
        int saved = p.getInt("saved_count", 0);
        int errors = p.getInt("error_count", 0);
        long latency = p.getLong("last_latency", -1);
        long duration = p.getLong("last_duration", -1);
        int peak = p.getInt("last_peak", -1);
        String lastEvent = p.getString("last_event", "없음");
        String lastError = p.getString("last_error", "없음");
        StringBuilder out = new StringBuilder();
        out.append("녹음 시작 ").append(started).append("회 / 저장 ").append(saved).append("회 / 오류 ").append(errors).append("회\n");
        if (latency >= 0) out.append("최근 시작 지연: ").append(latency).append("ms\n");
        if (duration >= 0) out.append("최근 녹음 길이: ").append(String.format(Locale.KOREA, "%.1f초", duration / 1000.0)).append("\n");
        if (peak >= 0) out.append("최근 음성 신호: ").append(peak).append(peak < 300 ? " (매우 약함)" : " (감지됨)").append("\n");
        out.append("최근 이벤트: ").append(lastEvent);
        if (errors > 0) out.append("\n최근 오류: ").append(lastError);
        return out.toString();
    }

    public static void clear(Context context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply();
        File root = context.getExternalFilesDir(null);
        if (root != null) new File(root, LOG_NAME).delete();
    }

    public static File recordingsDirectory(Context context) {
        File root = context.getExternalFilesDir(Environment.DIRECTORY_MUSIC);
        if (root == null) root = new File(context.getFilesDir(), "recordings");
        File dir = new File(root, "MalhaedwoPttProbe");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    private static void writeLog(Context context, String event, String source, String detail) {
        File root = context.getExternalFilesDir(null);
        if (root == null) root = context.getFilesDir();
        File log = new File(root, LOG_NAME);
        boolean header = !log.exists();
        try (FileWriter writer = new FileWriter(log, true)) {
            if (header) writer.write("time,event,source,detail\n");
            String time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.KOREA).format(new Date());
            writer.write(csv(time) + "," + csv(event) + "," + csv(source) + "," + csv(detail) + "\n");
        } catch (IOException ignored) {
        }
    }

    private static String csv(String value) {
        if (value == null) return "\"\"";
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
