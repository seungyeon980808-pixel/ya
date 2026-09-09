package com.malhaedwo.pttprobe;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public final class SyncState {
    public static final String ACTION_CHANGED = "com.malhaedwo.pttprobe.SYNC_STATE_CHANGED";
    private static final String PREFS = "malhaedwo_sync_runtime";
    private static final String LAST_SUCCESS = "last_success";
    private static final String LAST_ERROR = "last_error";
    private static final String RUNNING = "running";
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm")
            .withZone(ZoneId.systemDefault());

    private SyncState() {}

    public static void begin(Context context) {
        prefs(context).edit().putBoolean(RUNNING, true).putString(LAST_ERROR, "").apply();
        broadcast(context);
    }

    public static void success(Context context) {
        prefs(context).edit().putBoolean(RUNNING, false)
                .putLong(LAST_SUCCESS, System.currentTimeMillis()).putString(LAST_ERROR, "").apply();
        broadcast(context);
    }

    public static void failure(Context context, String error) {
        prefs(context).edit().putBoolean(RUNNING, false)
                .putString(LAST_ERROR, error == null ? "동기화 오류" : error).apply();
        broadcast(context);
    }

    public static String summary(Context context) {
        if (!GoogleOAuth.isConnected(context)) return "Google 동기화: 연결되지 않음";
        SharedPreferences p = prefs(context);
        StringBuilder out = new StringBuilder("Google 동기화: ")
                .append(GoogleOAuth.accountEmail(context));
        if (p.getBoolean(RUNNING, false)) out.append("\n현재 상태: 동기화 중");
        else {
            long last = p.getLong(LAST_SUCCESS, 0);
            out.append("\n최근 완료: ").append(last > 0 ? FORMAT.format(Instant.ofEpochMilli(last)) : "아직 없음");
        }
        int pending = CaptureDatabase.get(context).pendingSyncCount();
        out.append("\n대기 작업: ").append(pending).append("개");
        String error = p.getString(LAST_ERROR, "");
        if (error != null && !error.isEmpty()) out.append("\n최근 오류: ").append(error);
        return out.toString();
    }

    public static String lastError(Context context) {
        return prefs(context).getString(LAST_ERROR, "");
    }

    private static SharedPreferences prefs(Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static void broadcast(Context context) {
        context.getApplicationContext().sendBroadcast(new Intent(ACTION_CHANGED).setPackage(context.getPackageName()));
    }
}
