package com.malhaedwo.pttprobe;

import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public final class SpeechFileRecognizer {
    public interface Callback {
        void onSuccess(String transcript, float confidence, boolean onDevice);
        void onFailure(String message);
    }

    private static final int PCM_BYTES_PER_SECOND = WavRecorder.SAMPLE_RATE * 2;
    private static final int STREAM_CHUNK_BYTES = PCM_BYTES_PER_SECOND / 10;
    private static final ExecutorService FILE_EXECUTOR = Executors.newSingleThreadExecutor();
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AtomicBoolean finished = new AtomicBoolean(false);
    private SpeechRecognizer recognizer;
    private ParcelFileDescriptor audioSource;
    private ParcelFileDescriptor audioWriter;
    private File pcmFile;
    private Callback callback;
    private Runnable attemptTimeout;
    private Runnable partialFinalize;
    private boolean onDevice;
    private int attemptToken;
    private int streamEndedToken = -1;
    private String bestPartial = "";
    private String segmentedText = "";
    private float bestConfidence = -1f;
    private String previousFailure = "";

    public SpeechFileRecognizer(Context context) {
        this.context = context.getApplicationContext();
    }

    public void start(File wavFile, Callback callback) {
        this.callback = callback;
        FILE_EXECUTOR.execute(() -> {
            try {
                File raw = new File(context.getCacheDir(), "speech-" + System.nanoTime() + ".pcm");
                copyPcm(wavFile, raw);
                main.post(() -> startPrepared(raw));
            } catch (Throwable error) {
                main.post(() -> finishFailure("음성 파일 준비 실패: " + safe(error)));
            }
        });
    }

    private void startPrepared(File raw) {
        if (finished.get()) { raw.delete(); return; }
        pcmFile = raw;
        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
            finishFailure("휴대전화에 음성인식 서비스가 없습니다");
            return;
        }
        boolean deviceAvailable = false;
        try { deviceAvailable = SpeechRecognizer.isOnDeviceRecognitionAvailable(context); }
        catch (Throwable ignored) {}
        startAttempt(deviceAvailable);
    }

    private void startAttempt(boolean useOnDevice) {
        if (finished.get()) return;
        int token = ++attemptToken;
        releaseAttemptResources();
        onDevice = useOnDevice;
        bestPartial = "";
        segmentedText = "";
        bestConfidence = -1f;
        streamEndedToken = -1;
        try {
            recognizer = useOnDevice
                    ? SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
                    : SpeechRecognizer.createSpeechRecognizer(context);
            recognizer.setRecognitionListener(listenerFor(token));

            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            audioSource = pipe[0];
            audioWriter = pipe[1];

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ko-KR");
            intent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3);
            intent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
            intent.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, useOnDevice);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE, audioSource);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_CHANNEL_COUNT, 1);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_ENCODING, AudioFormat.ENCODING_PCM_16BIT);
            intent.putExtra(RecognizerIntent.EXTRA_AUDIO_SOURCE_SAMPLING_RATE, WavRecorder.SAMPLE_RATE);
            recognizer.startListening(intent);

            ParcelFileDescriptor writer = audioWriter;
            FILE_EXECUTOR.execute(() -> streamPcm(pcmFile, writer, token));
            attemptTimeout = () -> {
                if (isCurrent(token)) handleAttemptFailure(token, "음성인식 시간 초과");
            };
            main.postDelayed(attemptTimeout, 90_000L);
        } catch (Throwable error) {
            handleAttemptFailure(token, "음성인식 시작 실패: " + safe(error));
        }
    }

    private RecognitionListener listenerFor(int token) {
        return new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {}
            @Override public void onBeginningOfSpeech() {}
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {}
            @Override public void onEvent(int eventType, Bundle params) {}

            @Override public void onPartialResults(Bundle partialResults) {
                if (!isCurrent(token)) return;
                String text = firstText(partialResults);
                if (!text.isEmpty() && text.length() >= bestPartial.length()) {
                    bestPartial = text;
                    bestConfidence = firstConfidence(partialResults);
                }
                if (streamEndedToken == token && !bestPartial.isEmpty()) schedulePartialFinalize(token);
            }

            @Override public void onSegmentResults(Bundle segmentResults) {
                if (!isCurrent(token)) return;
                String text = firstText(segmentResults);
                if (!text.isEmpty()) {
                    segmentedText = segmentedText.isEmpty() ? text : segmentedText + " " + text;
                    bestPartial = segmentedText.trim();
                    bestConfidence = firstConfidence(segmentResults);
                }
                if (streamEndedToken == token && !bestPartial.isEmpty()) schedulePartialFinalize(token);
            }

            @Override public void onEndOfSegmentedSession() {
                if (!isCurrent(token)) return;
                if (!bestPartial.isEmpty()) finishSuccess(bestPartial, bestConfidence);
                else handleAttemptFailure(token, "분할 음성인식 결과가 없습니다");
            }

            @Override public void onError(int error) {
                if (!isCurrent(token)) return;
                handleAttemptFailure(token, errorMessage(error) + " (코드 " + error + ")");
            }

            @Override public void onResults(Bundle results) {
                if (!isCurrent(token)) return;
                String text = firstText(results);
                if (text.isEmpty()) text = bestPartial;
                if (text.isEmpty()) {
                    handleAttemptFailure(token, "음성을 문장으로 인식하지 못했습니다");
                    return;
                }
                float confidence = firstConfidence(results);
                if (confidence < 0) confidence = bestConfidence;
                finishSuccess(text, confidence);
            }
        };
    }

    private void streamPcm(File raw, ParcelFileDescriptor writer, int token) {
        long started = SystemClock.elapsedRealtime();
        long written = 0;
        boolean completed = false;
        try (FileInputStream input = new FileInputStream(raw);
             ParcelFileDescriptor.AutoCloseOutputStream output = new ParcelFileDescriptor.AutoCloseOutputStream(writer)) {
            byte[] buffer = new byte[STREAM_CHUNK_BYTES];
            int count;
            while ((count = input.read(buffer)) != -1) {
                if (finished.get() || token != attemptToken || Thread.currentThread().isInterrupted()) return;
                output.write(buffer, 0, count);
                output.flush();
                written += count;
                long target = started + written * 1000L / PCM_BYTES_PER_SECOND;
                long wait = target - SystemClock.elapsedRealtime();
                if (wait > 0) Thread.sleep(wait);
            }
            completed = true;
        } catch (Throwable error) {
            if (!finished.get() && token == attemptToken) {
                main.post(() -> {
                    if (isCurrent(token)) handleAttemptFailure(token, "음성 데이터 전달 실패: " + safe(error));
                });
            }
        }
        if (completed) {
            main.post(() -> {
                if (!isCurrent(token)) return;
                streamEndedToken = token;
                if (!bestPartial.isEmpty()) schedulePartialFinalize(token);
            });
        }
    }

    private void schedulePartialFinalize(int token) {
        if (partialFinalize != null) main.removeCallbacks(partialFinalize);
        partialFinalize = () -> {
            if (isCurrent(token) && streamEndedToken == token && !bestPartial.trim().isEmpty()) {
                finishSuccess(bestPartial.trim(), bestConfidence);
            }
        };
        main.postDelayed(partialFinalize, 1_500L);
    }

    private void handleAttemptFailure(int token, String message) {
        if (!isCurrent(token)) return;
        if (!bestPartial.trim().isEmpty()) {
            finishSuccess(bestPartial.trim(), bestConfidence);
            return;
        }
        if (onDevice) {
            previousFailure = "온디바이스 " + message;
            attemptToken++;
            releaseAttemptResources();
            main.postDelayed(() -> {
                if (!finished.get()) startAttempt(false);
            }, 250L);
            return;
        }
        String detail = previousFailure.isEmpty() ? message : previousFailure + "; 기본 인식 " + message;
        finishFailure(detail);
    }

    public void cancel() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(this::cancel);
            return;
        }
        if (!finished.compareAndSet(false, true)) return;
        attemptToken++;
        releaseAttemptResources();
        cleanupFinal();
    }

    private void finishSuccess(String transcript, float confidence) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> finishSuccess(transcript, confidence));
            return;
        }
        if (!finished.compareAndSet(false, true)) return;
        attemptToken++;
        Callback result = callback;
        boolean resultOnDevice = onDevice;
        releaseAttemptResources();
        cleanupFinal();
        if (result != null) result.onSuccess(transcript.trim(), confidence, resultOnDevice);
    }

    private void finishFailure(String message) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            main.post(() -> finishFailure(message));
            return;
        }
        if (!finished.compareAndSet(false, true)) return;
        attemptToken++;
        Callback result = callback;
        releaseAttemptResources();
        cleanupFinal();
        if (result != null) result.onFailure(message);
    }

    private void releaseAttemptResources() {
        if (attemptTimeout != null) main.removeCallbacks(attemptTimeout);
        if (partialFinalize != null) main.removeCallbacks(partialFinalize);
        attemptTimeout = null;
        partialFinalize = null;
        try { if (recognizer != null) recognizer.cancel(); } catch (Throwable ignored) {}
        try { if (recognizer != null) recognizer.destroy(); } catch (Throwable ignored) {}
        try { if (audioWriter != null) audioWriter.close(); } catch (Throwable ignored) {}
        try { if (audioSource != null) audioSource.close(); } catch (Throwable ignored) {}
        recognizer = null;
        audioWriter = null;
        audioSource = null;
    }

    private void cleanupFinal() {
        if (pcmFile != null) pcmFile.delete();
        pcmFile = null;
        callback = null;
    }

    private boolean isCurrent(int token) {
        return !finished.get() && token == attemptToken;
    }

    private static String firstText(Bundle results) {
        if (results == null) return "";
        ArrayList<String> texts = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        return texts == null || texts.isEmpty() || texts.get(0) == null ? "" : texts.get(0).trim();
    }

    private static float firstConfidence(Bundle results) {
        if (results == null) return -1f;
        float[] confidences = results.getFloatArray(SpeechRecognizer.CONFIDENCE_SCORES);
        return confidences != null && confidences.length > 0 ? confidences[0] : -1f;
    }

    private static void copyPcm(File wav, File raw) throws Exception {
        try (FileInputStream input = new FileInputStream(wav); FileOutputStream output = new FileOutputStream(raw)) {
            long remaining = 44;
            while (remaining > 0) {
                long skipped = input.skip(remaining);
                if (skipped <= 0) {
                    if (input.read() == -1) throw new IllegalStateException("WAV 헤더가 손상되었습니다");
                    skipped = 1;
                }
                remaining -= skipped;
            }
            byte[] buffer = new byte[16 * 1024];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            output.getFD().sync();
        }
        if (raw.length() == 0) throw new IllegalStateException("음성 데이터가 비어 있습니다");
    }

    public static String errorMessage(int error) {
        switch (error) {
            case SpeechRecognizer.ERROR_AUDIO: return "음성인식 오디오 오류";
            case SpeechRecognizer.ERROR_CLIENT: return "음성인식 요청 오류";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "음성인식 권한 부족";
            case SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED: return "한국어 음성인식 미지원";
            case SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE: return "한국어 음성 모델 미설치";
            case SpeechRecognizer.ERROR_NETWORK: return "음성인식 네트워크 오류";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "음성인식 네트워크 시간 초과";
            case SpeechRecognizer.ERROR_NO_MATCH: return "음성을 문장으로 인식하지 못했습니다";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "음성인식기가 사용 중입니다";
            case SpeechRecognizer.ERROR_SERVER: return "음성인식 서비스 오류";
            case SpeechRecognizer.ERROR_SERVER_DISCONNECTED: return "음성인식 서비스 연결 끊김";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "인식할 음성이 없습니다";
            case SpeechRecognizer.ERROR_TOO_MANY_REQUESTS: return "음성인식 요청이 너무 많습니다";
            default: return "음성인식 오류 " + error;
        }
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
