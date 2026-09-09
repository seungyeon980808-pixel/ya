package com.malhaedwo.pttprobe;

import android.content.Context;

import java.util.List;
import java.util.Objects;
import java.util.TimeZone;

public final class CaptureAutomation {
    private CaptureAutomation() {}

    /** Reconciles derived alarms and optional Calendar output only after explicit approval. */
    public static void process(Context context, long id) {
        process(context, id, false);
    }

    public static void processExplicit(Context context, long id) {
        process(context, id, true);
    }

    /** Call only after an edit was successfully persisted, before normal reconciliation. */
    static void withdrawNotificationAfterEdit(Context context, CaptureItem before, CaptureItem after) {
        if (before == null || after == null || before.id != after.id) return;
        if (!Objects.equals(before.status, after.status)
                || !Objects.equals(before.kind, after.kind)
                || before.reminderAt != after.reminderAt
                || !Objects.equals(before.title, after.title)
                || !Objects.equals(before.transcript, after.transcript)) {
            ReminderScheduler.cancel(context, after.id);
        }
    }

    public static void requestDelete(Context context, long id) {
        CaptureDatabase database = CaptureDatabase.get(context);
        CaptureItem item = database.getItem(id);
        if (item == null) return;
        ReminderScheduler.cancel(context, id);
        database.clearReminder(id);
        long now = System.currentTimeMillis();
        if (item.calendarEventId > 0 || item.isDeletePending()) {
            database.markCalendarDeletePending(id, null, now);
            reconcileCalendarDelete(context, database, database.getItem(id), true, now);
        } else {
            database.delete(id);
        }
    }

    /** Starts the normal Calendar cleanup lifecycle for a deletion accepted from sync. */
    static boolean requestRemoteDelete(Context context, String uuid, int remoteVersion) {
        CaptureDatabase database = CaptureDatabase.get(context);
        long id = database.applyRemoteDeleted(uuid, remoteVersion);
        if (id < 0) return false;
        if (id > 0) {
            ReminderScheduler.cancel(context, id);
            database.clearReminder(id);
            processExplicit(context, id);
        }
        return true;
    }

    private static void process(Context context, long id, boolean explicitCalendarRetry) {
        CaptureDatabase database = CaptureDatabase.get(context);
        CaptureItem item = database.getItem(id);
        if (item == null) return;
        long now = System.currentTimeMillis();
        if (item.isDeletePending()) {
            reconcileCalendarDelete(context, database, item, explicitCalendarRetry, now);
            return;
        }
        if (!"APPROVED".equals(item.status)) {
            ReminderScheduler.cancel(context, id);
            database.clearReminder(id);
            return;
        }
        reconcileReminder(context, database, item, now);
        item = database.getItem(id);
        if (item != null) reconcileCalendarUpsert(context, database, item, explicitCalendarRetry, now);
    }

    private static void reconcileReminder(Context context, CaptureDatabase database, CaptureItem item, long now) {
        if (!item.isReminderEligible(now)) {
            // An elapsed approved schedule is not an explicit dismissal. Periodic sync
            // must not withdraw a notification the user has not yet acted on.
            if (item.isSchedule() && item.reminderAt > 0 && item.reminderAt <= now) {
                ReminderScheduler.cancelAlarmOnly(context, item.id);
            } else {
                ReminderScheduler.cancel(context, item.id);
            }
            database.clearReminder(item.id);
            return;
        }
        CaptureItem token = database.prepareReminder(item.id, now);
        ReminderScheduler.Result result = ReminderScheduler.schedule(context, token);
        database.markReminderResult(item.id, result, result.message);
    }

    private static void reconcileCalendarUpsert(Context context, CaptureDatabase database, CaptureItem item,
                                                boolean explicit, long now) {
        if (item.calendarEventId > 0 && (!item.isSchedule() || item.startAt <= 0)) {
            if (!CalendarSync.hasPermission(context)) {
                database.markCalendarFailure(item.id,
                        "연결된 캘린더 일정을 삭제하려면 캘린더 권한이 필요합니다", now);
                return;
            }
            try {
                if (CalendarSync.deleteEvent(context, item.calendarEventId)) database.clearCalendarEvent(item.id);
                else database.markCalendarFailure(item.id, "캘린더 일정 삭제 실패", now);
            } catch (Throwable error) {
                database.markCalendarFailure(item.id, "캘린더 일정 삭제 실패: " + safe(error), now);
            }
            return;
        }
        if (!item.isSchedule() || item.startAt <= 0) return;
        boolean hasCalendarWork = item.hasLocalCalendarWork();
        if (!explicit && !hasCalendarWork) return;
        if (!explicit && !item.needsCalendarRetry(now) &&
                !CalendarReliability.CALENDAR_PENDING.equals(item.calendarSyncState)) return;
        long selectedCalendarId = CalendarSync.targetCalendarId(context, item);
        String timezone = CalendarReliability.hasText(item.calendarTimezone)
                ? item.calendarTimezone : TimeZone.getDefault().getID();
        item = database.prepareCalendarUpsert(item.id, selectedCalendarId, timezone, now);
        if (item == null) return;
        if (!CalendarSync.hasPermission(context)) {
            database.markCalendarFailure(item.id, "캘린더 권한이 없어 일정을 처리하지 못했습니다", now);
            return;
        }
        if (CalendarSync.targetCalendarId(context, item) < 0) {
            database.markCalendarFailure(item.id, "쓰기 가능한 캘린더를 먼저 선택해주세요", now);
            return;
        }
        try {
            long eventId = CalendarSync.upsert(context, item);
            database.markCalendarSynced(item.id, eventId, CalendarSync.targetCalendarId(context, item), timezone);
        } catch (Throwable error) {
            database.markCalendarFailure(item.id, "캘린더 처리 실패: " + safe(error), now);
        }
    }

    private static void reconcileCalendarDelete(Context context, CaptureDatabase database, CaptureItem item,
                                                boolean explicit, long now) {
        if (item == null) return;
        if (!explicit && !item.needsCalendarRetry(now)) return;
        if (item.calendarEventId <= 0) {
            finishDelete(database, item);
            return;
        }
        if (!CalendarSync.hasPermission(context)) {
            database.markCalendarDeleteFailure(item.id,
                    "캘린더 권한이 없어 외부 일정을 삭제하지 못했습니다", now);
            return;
        }
        try {
            if (CalendarSync.deleteEvent(context, item.calendarEventId)) finishDelete(database, item);
            else database.markCalendarDeleteFailure(item.id, "외부 캘린더 일정 삭제 실패", now);
        } catch (Throwable error) {
            database.markCalendarDeleteFailure(item.id, "외부 캘린더 일정 삭제 실패: " + safe(error), now);
        }
    }

    private static void finishDelete(CaptureDatabase database, CaptureItem item) {
        boolean remoteDelete = item.isDeletePending()
                && item.remoteSyncedVersion == item.version
                && item.remoteSyncedVersion > 0;
        if (remoteDelete) database.finishRemoteDeleted(item.uuid, item.remoteSyncedVersion);
        else database.delete(item.id);
    }

    public static void processAllReady(Context context) {
        CaptureDatabase database = CaptureDatabase.get(context);
        List<CaptureItem> items = database.listAutomationCandidates();
        for (CaptureItem item : items) {
            if ("APPROVED".equals(item.status) || item.isDeletePending()) process(context, item.id);
        }
    }

    private static String safe(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
