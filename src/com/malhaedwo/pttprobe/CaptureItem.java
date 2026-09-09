package com.malhaedwo.pttprobe;

public final class CaptureItem {
    public long id;
    public String uuid;
    public String audioPath;
    public String sourceType;
    public String sourceMime;
    public String area;
    public String transcript;
    public String kind;
    public String title;
    public long startAt;
    public long endAt;
    public long reminderAt;
    public String status;
    public long calendarEventId;
    public long approvedAt;
    public long updatedAt;
    public int version;
    public String error;
    public long createdAt;
    public long durationMs;
    public int peakAmplitude;
    public String source;
    public int remoteSyncedVersion;
    public long remoteSyncedAt;
    public String driveFileId;
    public String audioSha256;
    public String syncError;
    public boolean remoteCalendarEnabled;
    public String remoteCalendarId;
    public String remoteCalendarEventId;
    public String calendarSyncState;
    public String calendarSyncError;
    public int calendarAttempts;
    public long calendarNextAttemptAt;
    public String calendarIdempotencyKey;
    public long calendarSelectedId;
    public String calendarTimezone;
    public boolean calendarDeletePending;
    public String reminderState;
    public long reminderGeneration;
    public long scheduledReminderAt;
    public String alarmLastError;
    public long deletedAt;

    public boolean isSchedule() { return "SCHEDULE".equals(kind); }
    public boolean isTodo() { return "TODO".equals(kind); }
    public boolean hasTime() { return startAt > 0; }
    public boolean isDone() { return "DONE".equals(status); }
    public boolean isDeletePending() {
        return "DELETE_PENDING".equals(status) || calendarDeletePending ||
                CalendarReliability.CALENDAR_DELETE_PENDING.equals(calendarSyncState) ||
                CalendarReliability.CALENDAR_DELETE_FAILED.equals(calendarSyncState);
    }
    public boolean isPendingApproval() {
        return "PENDING".equals(status) || "AUDIO_ONLY".equals(status) || "TRANSCRIBING".equals(status);
    }
    public boolean isApproved() { return "APPROVED".equals(status) || "DONE".equals(status); }
    public boolean isReminderEligible(long now) {
        return CalendarReliability.isReminderEligible(status, kind, reminderAt, now);
    }
    public boolean needsCalendarRetry(long now) {
        return CalendarReliability.needsCalendarRetry(calendarSyncState, calendarNextAttemptAt, now, false);
    }
    public boolean hasCalendarFailure() {
        return CalendarReliability.CALENDAR_FAILED.equals(calendarSyncState) ||
                CalendarReliability.CALENDAR_DELETE_FAILED.equals(calendarSyncState) ||
                CalendarReliability.hasText(calendarSyncError);
    }
    public boolean hasLocalCalendarWork() {
        return calendarEventId > 0 || CalendarReliability.isCalendarRetryState(calendarSyncState);
    }
    public String currentReminderToken() {
        return id + ":" + reminderAt + ":" + reminderGeneration;
    }
    public boolean hasRemoteCalendarLink() {
        return remoteCalendarEnabled && remoteCalendarEventId != null && !remoteCalendarEventId.isEmpty();
    }
}
