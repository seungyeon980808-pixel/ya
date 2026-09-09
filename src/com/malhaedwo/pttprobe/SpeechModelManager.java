package com.malhaedwo.pttprobe;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.speech.ModelDownloadListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

public final class SpeechModelManager {
    public interface Listener { void onMessage(String message); }
    private static SpeechRecognizer active;

    private SpeechModelManager() {}

    public static void requestKorean(Context context, Listener listener) {
        destroyActive();
        try {
            active = SpeechRecognizer.createSpeechRecognizer(context);
            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true);
            if (Build.VERSION.SDK_INT >= 33) {
                active.triggerModelDownload(intent, context.getMainExecutor(), new ModelDownloadListener() {
                    @Override public void onProgress(int completedPercent) {
                        listener.onMessage("한국어 음성 모델 내려받는 중 " + completedPercent + "%");
                    }
                    @Override public void onSuccess() {
                        listener.onMessage("한국어 오프라인 음성 모델이 준비되었습니다");
                        destroyActive();
                    }
                    @Override public void onScheduled() {
                        listener.onMessage("한국어 음성 모델 다운로드를 예약했습니다");
                        destroyActive();
                    }
                    @Override public void onError(int error) {
                        listener.onMessage("음성 모델 준비 실패: " + SpeechFileRecognizer.errorMessage(error));
                        destroyActive();
                    }
                });
            } else {
                active.triggerModelDownload(intent);
                listener.onMessage("한국어 음성 모델 다운로드를 요청했습니다");
                destroyActive();
            }
        } catch (Throwable error) {
            listener.onMessage("음성 모델 요청 실패: " + (error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
            destroyActive();
        }
    }

    private static void destroyActive() {
        try { if (active != null) active.destroy(); } catch (Throwable ignored) {}
        active = null;
    }
}
