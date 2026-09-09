package com.malhaedwo.pttprobe;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.SystemClock;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public final class WavRecorder {
    public static final int SAMPLE_RATE = 16000;
    private static final int CHANNELS = 1;
    private static final int BITS_PER_SAMPLE = 16;

    private final File file;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private AudioRecord audioRecord;
    private RandomAccessFile output;
    private Thread writerThread;
    private volatile long audioStartElapsed;
    private volatile long dataBytes;
    private volatile int peakAmplitude;
    private volatile Throwable writerError;
    private final CountDownLatch firstBufferReady = new CountDownLatch(1);

    public WavRecorder(File file) {
        this.file = file;
    }

    public long start() throws Exception {
        int channel = AudioFormat.CHANNEL_IN_MONO;
        int encoding = AudioFormat.ENCODING_PCM_16BIT;
        int minimum = AudioRecord.getMinBufferSize(SAMPLE_RATE, channel, encoding);
        if (minimum <= 0) minimum = 4096;
        int bufferSize = Math.max(8192, minimum * 2);

        output = new RandomAccessFile(file, "rw");
        output.setLength(0);
        output.write(new byte[44]);

        audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE, channel, encoding, bufferSize);
        if (audioRecord.getState() != AudioRecord.STATE_INITIALIZED) {
            throw new IllegalStateException("AudioRecord 초기화 실패");
        }
        audioRecord.startRecording();
        if (audioRecord.getRecordingState() != AudioRecord.RECORDSTATE_RECORDING) {
            throw new IllegalStateException("마이크 녹음 시작 실패");
        }
        running.set(true);
        writerThread = new Thread(() -> writeLoop(bufferSize), "PttWavWriter");
        writerThread.start();
        if (!firstBufferReady.await(600, TimeUnit.MILLISECONDS)) {
            throw new IllegalStateException("마이크 첫 오디오 버퍼 대기 시간 초과");
        }
        if (writerError != null || audioStartElapsed == 0) {
            throw new IllegalStateException("마이크 첫 오디오 버퍼 수신 실패");
        }
        return audioStartElapsed;
    }

    private void writeLoop(int bufferSize) {
        byte[] buffer = new byte[bufferSize];
        try {
            while (running.get()) {
                int read = audioRecord.read(buffer, 0, buffer.length);
                if (read > 0) {
                    boolean firstBuffer = dataBytes == 0;
                    if (firstBuffer) audioStartElapsed = SystemClock.uptimeMillis();
                    updatePeak(buffer, read);
                    output.write(buffer, 0, read);
                    dataBytes += read;
                    if (firstBuffer) firstBufferReady.countDown();
                } else if (read < 0 && running.get()) {
                    throw new IllegalStateException("AudioRecord read 오류: " + read);
                }
            }
        } catch (Throwable error) {
            writerError = error;
            firstBufferReady.countDown();
        }
    }

    private void updatePeak(byte[] buffer, int length) {
        int localPeak = peakAmplitude;
        for (int i = 0; i + 1 < length; i += 2) {
            int sample = (short) ((buffer[i] & 0xff) | (buffer[i + 1] << 8));
            int absolute = sample == Short.MIN_VALUE ? Short.MAX_VALUE : Math.abs(sample);
            if (absolute > localPeak) localPeak = absolute;
        }
        peakAmplitude = localPeak;
    }

    public synchronized Result stop() throws Exception {
        long stopRequested = SystemClock.uptimeMillis();
        running.set(false);
        AudioRecord record = audioRecord;
        if (record != null) {
            try { record.stop(); } catch (Throwable ignored) {}
        }
        Thread thread = writerThread;
        if (thread != null) thread.join(2000);
        if (thread != null && thread.isAlive()) {
            try { if (record != null) record.release(); } catch (Throwable ignored) {}
            audioRecord = null;
            try { if (output != null) output.close(); } catch (Throwable ignored) {}
            output = null;
            file.delete();
            throw new IllegalStateException("녹음 기록 스레드 종료 시간 초과");
        }
        if (record != null) {
            try { record.release(); } catch (Throwable ignored) {}
        }
        audioRecord = null;
        RandomAccessFile fileOutput = output;
        if (fileOutput != null) {
            writeHeader(fileOutput, dataBytes);
            fileOutput.getFD().sync();
            fileOutput.close();
        }
        output = null;
        if (writerError != null) throw new Exception("녹음 파일 기록 실패", writerError);
        long duration = Math.max(0, stopRequested - audioStartElapsed);
        return new Result(file, duration, dataBytes, peakAmplitude);
    }

    public synchronized void abort() {
        running.set(false);
        try { if (audioRecord != null) audioRecord.stop(); } catch (Throwable ignored) {}
        try { if (audioRecord != null) audioRecord.release(); } catch (Throwable ignored) {}
        try { if (output != null) output.close(); } catch (Throwable ignored) {}
        audioRecord = null;
        output = null;
        file.delete();
    }

    private static void writeHeader(RandomAccessFile out, long pcmBytes) throws Exception {
        long byteRate = SAMPLE_RATE * CHANNELS * BITS_PER_SAMPLE / 8;
        long totalDataLen = pcmBytes + 36;
        out.seek(0);
        out.writeBytes("RIFF");
        writeLittleEndianInt(out, totalDataLen);
        out.writeBytes("WAVE");
        out.writeBytes("fmt ");
        writeLittleEndianInt(out, 16);
        writeLittleEndianShort(out, 1);
        writeLittleEndianShort(out, CHANNELS);
        writeLittleEndianInt(out, SAMPLE_RATE);
        writeLittleEndianInt(out, byteRate);
        writeLittleEndianShort(out, CHANNELS * BITS_PER_SAMPLE / 8);
        writeLittleEndianShort(out, BITS_PER_SAMPLE);
        out.writeBytes("data");
        writeLittleEndianInt(out, pcmBytes);
    }

    private static void writeLittleEndianInt(RandomAccessFile out, long value) throws Exception {
        out.write((int) (value & 0xff));
        out.write((int) ((value >> 8) & 0xff));
        out.write((int) ((value >> 16) & 0xff));
        out.write((int) ((value >> 24) & 0xff));
    }

    private static void writeLittleEndianShort(RandomAccessFile out, int value) throws Exception {
        out.write(value & 0xff);
        out.write((value >> 8) & 0xff);
    }

    public static final class Result {
        public final File file;
        public final long durationMs;
        public final long pcmBytes;
        public final int peakAmplitude;
        Result(File file, long durationMs, long pcmBytes, int peakAmplitude) {
            this.file = file;
            this.durationMs = durationMs;
            this.pcmBytes = pcmBytes;
            this.peakAmplitude = peakAmplitude;
        }
    }
}
