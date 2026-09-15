package com.malhaedwo.pttprobe;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.os.PowerManager;
import android.view.KeyEvent;
import android.view.accessibility.AccessibilityEvent;

public final class VolumeKeyService extends AccessibilityService {
    @Override protected void onServiceConnected() {
        AccessibilityServiceInfo info = getServiceInfo();
        if (info != null) {
            info.eventTypes = AccessibilityEvent.TYPES_ALL_MASK;
            info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC;
            info.flags |= AccessibilityServiceInfo.FLAG_REQUEST_FILTER_KEY_EVENTS;
            setServiceInfo(info);
        }
        PttStore.append(this, "ACCESSIBILITY_CONNECTED", "hardware", "key_filter_ready");
        PttService.restoreIfEnabled(this);
    }

    @Override protected boolean onKeyEvent(KeyEvent event) {
        if (event.getKeyCode() != KeyEvent.KEYCODE_VOLUME_DOWN) return false;
        boolean screenOn = getSystemService(PowerManager.class).isInteractive();
        if (!PttService.isArmed()) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                PttStore.append(this, "KEY_DOWN_IGNORED", "hardware",
                        "ptt_not_armed;screen_on=" + screenOn + ";event_time=" + event.getEventTime());
            }
            return false;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getRepeatCount() == 0) {
                PttStore.append(this, "KEY_DOWN", "hardware",
                        "screen_on=" + screenOn + ";event_time=" + event.getEventTime());
                PttService.press(event.getEventTime(), "hardware");
            }
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_UP) {
            PttStore.append(this, "KEY_UP", "hardware", "held_ms=" + Math.max(0, event.getEventTime() - event.getDownTime()));
            PttService.release("hardware");
            return true;
        }
        return true;
    }

    @Override public void onAccessibilityEvent(AccessibilityEvent event) {}
    @Override public void onInterrupt() {
        if (PttService.isRecording()) PttService.release("accessibility_interrupted");
    }
}
