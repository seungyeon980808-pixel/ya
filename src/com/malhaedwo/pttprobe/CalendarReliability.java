package com.malhaedwo.pttprobe;

import java.util.UUID;

public final class CalendarReliability {
    public static final String CALENDAR_NONE = "NONE";
    public static final String CALENDAR_PENDING = "PENDING";
    public static final String CALENDAR_SYNCED = "SYNCED";
    public static final String CALENDAR_FAILED = "FAILED";
    public static final String CALENDAR_DELETE_PENDING = "DELETE_PENDING";
    public static final String CALENDAR_DELETE_FAILED = "DELETE_FAILED";

    public static final String REMINDER_NONE = "NONE";
    public static final String REMINDER_PENDING = "PENDING";
    public static final String REMINDER_EXACT = "EXACT";
    public static final String REMINDER_INEXACT_DEGRADED = "INEXACT_DEGRADED";
    public static final String REMINDER_BLOCKED = "BLOCKED";

    private static final long MIN_RETRY_DELAY_MS = 60_000L;
    private static final long MAX_RETRY_DELAY_MS = 60L * 60_000L;

    private CalendarReliability() {}

    public static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    public static String newIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public static String stableKey(String current, String itemUuid) {
        if (hasText(current)) return current.trim();
        if (hasText(itemUuid)) return itemUuid.trim();
        return newIdempotencyKey();
    }

    public static String calendarMarker(String itemUuid, String key) {
        return "말해둬-ID:" + safeToken(itemUuid) + ":" + safeToken(key);
    }

    public static String customAppUri(String itemUuid, String key) {
        return "malhaedwo://calendar-event/" + safeToken(itemUuid) + "/" + safeToken(key);
    }

    public static String descriptionWithMarker(String body, String itemUuid, String key) {
        String marker = calendarMarker(itemUuid, key);
        String text = body == null ? "" : body.trim();
        if (text.contains(marker)) return text;
        return text.isEmpty() ? marker : text + "\n\n" + marker;
    }

    public static long chooseCalendarSnapshot(long existingSnapshot, long currentSelection) {
        return existingSnapshot > 0 ? existingSnapshot : Math.max(0, currentSelection);
    }

    public static boolean isCalendarRetryState(String state) {
        return CALENDAR_PENDING.equals(state) || CALENDAR_FAILED.equals(state) ||
                CALENDAR_DELETE_PENDING.equals(state) || CALENDAR_DELETE_FAILED.equals(state);
    }

    public static boolean needsCalendarRetry(String state, long nextAttemptAt, long now, boolean explicit) {
        return isCalendarRetryState(state) && (explicit || nextAttemptAt <= 0 || now >= nextAttemptAt);
    }

    public static long nextCalendarAttemptAt(long now, int attemptsAfterFailure) {
        int bounded = Math.max(1, Math.min(7, attemptsAfterFailure));
        long delay = MIN_RETRY_DELAY_MS << (bounded - 1);
        if (delay < 0 || delay > MAX_RETRY_DELAY_MS) delay = MAX_RETRY_DELAY_MS;
        return now + delay;
    }

    public static boolean isReminderEligible(String status, String kind, long reminderAt, long now) {
        return "APPROVED".equals(status) && "SCHEDULE".equals(kind) && reminderAt > now;
    }

    public static boolean shouldPostReminder(String status, String kind, long currentReminderAt,
                                             long currentGeneration, long firedReminderAt,
                                             long firedGeneration, boolean notificationAllowed) {
        return notificationAllowed && "APPROVED".equals(status) && "SCHEDULE".equals(kind) &&
                currentReminderAt > 0 && currentReminderAt == firedReminderAt &&
                currentGeneration > 0 && currentGeneration == firedGeneration;
    }

    public static String reminderStateLabel(String state) {
        if (REMINDER_EXACT.equals(state)) return "알림: 정확 예약";
        if (REMINDER_INEXACT_DEGRADED.equals(state)) return "알림: 정시 불가 · 대체 예약";
        if (REMINDER_BLOCKED.equals(state)) return "알림: 권한 필요";
        if (REMINDER_PENDING.equals(state)) return "알림: 예약 확인 중";
        return "알림: 없음";
    }

    public static String calendarStateLabel(String state, boolean hasEventId, boolean deletePending) {
        if (CALENDAR_DELETE_FAILED.equals(state)) return "Calendar: 삭제 재시도 필요";
        if (deletePending || CALENDAR_DELETE_PENDING.equals(state)) return "Calendar: 삭제 대기";
        if (CALENDAR_FAILED.equals(state)) return "Calendar: 재시도 필요";
        if (CALENDAR_PENDING.equals(state)) return "Calendar: 처리 대기";
        if (hasEventId || CALENDAR_SYNCED.equals(state)) return "Calendar: 연결됨";
        return "Calendar: 사용 안 함";
    }

    private static String safeToken(String value) {
        if (!hasText(value)) return "missing";
        return value.trim().replaceAll("[^A-Za-z0-9._:-]", "_");
    }
}
