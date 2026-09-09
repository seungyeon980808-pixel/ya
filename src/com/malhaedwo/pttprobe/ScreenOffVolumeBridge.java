package com.malhaedwo.pttprobe;

import android.content.Context;
import android.media.AudioManager;
import android.media.VolumeProvider;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;

public final class ScreenOffVolumeBridge {
    private static final long RELEASE_FALLBACK_MS = 1_200L;
    private final Context context;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final MediaSession session;
    private volatile boolean enabled;
    private volatile boolean disposed;
    private boolean lowerActive;

    private final Runnable inferredRelease = () -> releaseNow("repeat_timeout");

    private final VolumeProvider volumeProvider = new VolumeProvider(
            VolumeProvider.VOLUME_CONTROL_RELATIVE, 100, 50) {
        @Override public void onAdjustVolume(int direction) {
            main.post(() -> handleAdjustment(direction));
        }
    };

    public ScreenOffVolumeBridge(Context context) {
        this.context = context.getApplicationContext();
        session = new MediaSession(context, "MalhaedwoScreenOffPtt");
        session.setFlags(MediaSession.FLAG_HANDLES_MEDIA_BUTTONS | MediaSession.FLAG_HANDLES_TRANSPORT_CONTROLS);
        session.setPlaybackToRemote(volumeProvider);
        session.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY | PlaybackState.ACTION_PAUSE | PlaybackState.ACTION_STOP)
                .setState(PlaybackState.STATE_PLAYING, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());
    }

    public void setEnabled(boolean value) {
        if (disposed) return;
        runOnMain(() -> {
            if (disposed || enabled == value) return;
            enabled = value;
            if (!enabled) releaseNow("screen_on");
            session.setActive(enabled);
            PttStore.append(context, enabled ? "SCREEN_OFF_MEDIA_SESSION_ON" : "SCREEN_OFF_MEDIA_SESSION_OFF",
                    "media_session", "active=" + enabled);
        });
    }

    public boolean isEnabled() { return enabled; }

    private void handleAdjustment(int direction) {
        if (disposed || !enabled) return;
        volumeProvider.setCurrentVolume(50);
        if (direction == AudioManager.ADJUST_LOWER) {
            main.removeCallbacks(inferredRelease);
            if (!lowerActive) {
                lowerActive = PttService.press(SystemClock.uptimeMillis(), "screen_off_media_session");
                PttStore.append(context, "MEDIA_VOLUME_DOWN", "media_session", "accepted=" + lowerActive);
            }
            if (lowerActive) main.postDelayed(inferredRelease, RELEASE_FALLBACK_MS);
        } else if (lowerActive) {
            releaseNow(direction == AudioManager.ADJUST_SAME ? "adjust_same" : "non_lower_adjustment");
        } else if (direction == AudioManager.ADJUST_RAISE) {
            PttStore.append(context, "MEDIA_VOLUME_UP", "media_session", "reserved_while_screen_off");
        }
    }

    private void releaseNow(String reason) {
        main.removeCallbacks(inferredRelease);
        if (!lowerActive) return;
        lowerActive = false;
        PttService.release("screen_off_media_session");
        PttStore.append(context, "MEDIA_VOLUME_RELEASE", "media_session", reason);
    }

    public void release() {
        disposed = true;
        enabled = false;
        runOnMain(() -> {
            main.removeCallbacks(inferredRelease);
            releaseNow("bridge_release");
            try { session.setActive(false); } catch (Throwable ignored) {}
            try { session.release(); } catch (Throwable ignored) {}
        });
    }

    private void runOnMain(Runnable action) {
        if (Looper.myLooper() == Looper.getMainLooper()) action.run();
        else main.post(action);
    }
}
