package com.malhaedwo.pttprobe;

import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;

import java.io.File;
import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class TranscriptionManager {
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final ArrayDeque<Job> QUEUE = new ArrayDeque<>();
    private static final Set<Long> QUEUED_IDS = new HashSet<>();
    private static SpeechFileRecognizer active;
    private static Context appContext;

    private TranscriptionManager() {}

    public static void enqueue(Context context, long captureId, File wavFile) {
        Context app = context.getApplicationContext();
        MAIN.post(() -> {
            appContext = app;
            CaptureItem item = CaptureDatabase.get(app).getItem(captureId);
            if (item != null && QUEUED_IDS.add(captureId)) {
                QUEUE.add(new Job(captureId, wavFile, item.version));
            }
            runNext();
        });
    }

    public static void retry(Context context, long captureId, File wavFile) {
        CaptureDatabase.get(context).beginTranscription(captureId);
        enqueue(context, captureId, wavFile);
    }

    public static void retryPending(Context context) {
        List<CaptureItem> items = CaptureDatabase.get(context).listPendingTranscriptions();
        for (CaptureItem item : items) {
            if (item.audioPath != null) {
                File file = new File(item.audioPath);
                if (file.isFile()) enqueue(context, item.id, file);
            }
        }
    }

    public static boolean isBusy() { return active != null || !QUEUE.isEmpty(); }

    private static void runNext() {
        if (active != null) return;
        Job job = QUEUE.poll();
        if (job == null || appContext == null) return;
        if (!job.wavFile.isFile()) {
            CaptureDatabase.get(appContext).markTranscriptionError(job.id, job.expectedVersion, "원본 음성 파일이 없습니다");
            QUEUED_IDS.remove(job.id);
            runNext();
            return;
        }
        active = new SpeechFileRecognizer(appContext);
        publish("음성인식 중: " + job.wavFile.getName());
        active.start(job.wavFile, new SpeechFileRecognizer.Callback() {
            @Override public void onSuccess(String transcript, float confidence, boolean onDevice) {
                try {
                    CaptureDatabase database = CaptureDatabase.get(appContext);
                    CaptureItem stored = database.getItem(job.id);
                    long referenceTime = stored == null || stored.createdAt <= 0 ? System.currentTimeMillis() : stored.createdAt;
                    ParsedCommand parsed = KoreanCommandParser.parse(transcript, referenceTime);
                    boolean saved = database.applyParsed(job.id, job.expectedVersion, transcript, parsed);
                    if (!saved) {
                        PttStore.append(appContext, "TRANSCRIPTION_STALE", "speech", "id=" + job.id);
                        publish("사용자가 이미 수정한 항목이라 늦게 도착한 인식 결과는 적용하지 않았습니다");
                        return;
                    }
                    PttStore.append(appContext, "TRANSCRIBED", "speech",
                            "id=" + job.id + ";on_device=" + onDevice + ";confidence=" + confidence + ";kind=" + parsed.kind);
                    publish("승인 대기: " + parsed.title);
                } catch (Throwable error) {
                    String message = "인식 결과 저장 실패: " + safe(error);
                    try { CaptureDatabase.get(appContext).markTranscriptionError(job.id, job.expectedVersion, message); } catch (Throwable ignored) {}
                    publish(message + ", 원본 음성은 보존했습니다");
                } finally {
                    finish(job.id);
                }
            }

            @Override public void onFailure(String message) {
                try {
                    try { CaptureDatabase.get(appContext).markTranscriptionError(job.id, job.expectedVersion, message); } catch (Throwable ignored) {}
                    try { PttStore.append(appContext, "TRANSCRIPTION_FAILED", "speech", "id=" + job.id + ";" + message); } catch (Throwable ignored) {}
                    publish(message + ", 원본 음성은 보존했습니다");
                } finally {
                    finish(job.id);
                }
            }
        });
    }

    private static void finish(long id) {
        QUEUED_IDS.remove(id);
        active = null;
        runNext();
    }

    private static void publish(String message) {
        if (appContext == null) return;
        Intent intent = new Intent(PttService.BROADCAST_STATUS).setPackage(appContext.getPackageName());
        intent.putExtra("message", message);
        appContext.sendBroadcast(intent);
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }

    private static final class Job {
        final long id;
        final File wavFile;
        final int expectedVersion;
        Job(long id, File wavFile, int expectedVersion) {
            this.id = id;
            this.wavFile = wavFile;
            this.expectedVersion = expectedVersion;
        }
    }
}
