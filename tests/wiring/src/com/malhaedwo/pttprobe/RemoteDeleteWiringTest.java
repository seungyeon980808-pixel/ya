package com.malhaedwo.pttprobe;

import android.app.PendingIntent;
import android.content.ContentValues;

import java.lang.reflect.Field;
import java.util.Objects;

public final class RemoteDeleteWiringTest {
    private static int cases;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        run("remote tombstone retries provider cleanup before local removal",
                RemoteDeleteWiringTest::providerFailureRetry);
        run("permission denial preserves remote delete for retry",
                RemoteDeleteWiringTest::permissionDeniedRetry);
        run("unlinked tombstone cancels before local removal",
                RemoteDeleteWiringTest::unlinkedDelete);
        run("older tombstone cannot delete newer local data",
                RemoteDeleteWiringTest::olderVersionRejected);
        run("missing local tombstone is idempotent",
                RemoteDeleteWiringTest::missingLocalIdempotent);
        System.out.println("RemoteDeleteWiringTest passed: " + cases + " cases, "
                + assertions + " assertions");
    }

    private static void providerFailureRetry() throws Exception {
        TestEnvironment.ContextImpl context = fresh();
        CaptureDatabase db = CaptureDatabase.get(context);
        long at = soon(90);
        context.resolver.addEvent(701, 7, "Remote delete", "body", null);
        long id = seed(db, "remote-delete", "Remote delete", at, 2, 701);
        queue(db, "remote-delete", "UPSERT", 2);
        ReminderScheduler.schedule(context, db.getItem(id));
        eq(1, context.alarms.alarms.size(), "fixture has a pending alarm");

        new ReminderReceiver().onReceive(context, context.alarms.alarms.get(0).operation.getIntent());
        eq(1, context.notifications.notifications.size(), "fixture has a real receiver post");
        context.resolver.failDelete = true;
        yes(CaptureAutomation.requestRemoteDelete(context, "remote-delete", 2),
                "matching tombstone is accepted");

        CaptureItem failed = db.getItem(id);
        notNull(failed, "provider failure preserves local row");
        eq("DELETE_PENDING", failed.status, "remote delete stays pending");
        eq("DELETE_FAILED", failed.calendarSyncState, "provider failure is persisted");
        eq(1, failed.calendarAttempts, "provider failure increments attempts");
        yes(failed.calendarNextAttemptAt > System.currentTimeMillis(), "provider failure gets backoff");
        eq(701L, failed.calendarEventId, "Calendar link remains for retry");
        eq(0, context.alarms.alarms.size(), "alarm is cancelled immediately");
        yes(context.alarms.cancelled.size() > 0, "cancellation reached AlarmManager");
        eq(0, context.notifications.notifications.size(), "remote delete withdraws post even while provider cleanup fails");
        eq("NONE", failed.reminderState, "reminder state is cleared immediately");
        eq(null, db.getSyncOperation("remote-delete", "UPSERT"), "stale UPSERT is removed");
        eq(0, context.resolver.deleteCount, "failed provider did not report a deletion");

        yes(CaptureAutomation.requestRemoteDelete(context, "remote-delete", 2),
                "repeated tombstone remains accepted");
        notNull(db.getItem(id), "repeated tombstone cannot finalize failed provider cleanup");
        eq(1, context.resolver.events.size(), "failed provider event retained after repeat");
        context.resolver.failDelete = false;
        ContentValues due = new ContentValues(); due.put("calendar_next_attempt_at", 0L);
        db.getWritableDatabase().update("captures", due, "_id=?", new String[]{String.valueOf(id)});
        CaptureAutomation.processAllReady(context);
        eq(null, db.getItem(id), "successful retry removes local row");
        eq(0, context.resolver.events.size(), "successful retry removes provider event");
        eq(1, context.resolver.deleteCount, "same provider event is removed once");
        eq(0, db.pendingSyncCount(), "remote deletion creates no reverse DELETE or stale UPSERT");
    }

    private static void permissionDeniedRetry() throws Exception {
        TestEnvironment.ContextImpl context = fresh();
        CaptureDatabase db = CaptureDatabase.get(context);
        long at = soon(80);
        context.resolver.addEvent(702, 7, "Denied", "body", null);
        long id = seed(db, "remote-denied", "Denied", at, 3, 702);
        context.deny(android.Manifest.permission.WRITE_CALENDAR);

        yes(CaptureAutomation.requestRemoteDelete(context, "remote-denied", 3),
                "matching tombstone is accepted while permission is denied");
        CaptureItem denied = db.getItem(id);
        notNull(denied, "permission denial preserves row");
        eq("DELETE_FAILED", denied.calendarSyncState, "permission denial is retryable");
        eq(1, denied.calendarAttempts, "permission denial increments attempts");
        yes(denied.calendarNextAttemptAt > System.currentTimeMillis(), "permission denial gets backoff");
        eq(0, context.resolver.deleteCount, "provider is not called without permission");

        context.grant(android.Manifest.permission.WRITE_CALENDAR);
        CaptureAutomation.processExplicit(context, id);
        eq(null, db.getItem(id), "retry after permission grant removes local row");
        eq(1, context.resolver.deleteCount, "retry deletes provider event once");
        eq(0, db.pendingSyncCount(), "permission recovery does not queue a server delete");
    }

    private static void unlinkedDelete() throws Exception {
        TestEnvironment.ContextImpl context = fresh();
        CaptureDatabase db = CaptureDatabase.get(context);
        long at = soon(70);
        long id = seed(db, "remote-unlinked", "Unlinked", at, 1, 0);
        queue(db, "remote-unlinked", "UPSERT", 1);
        ReminderScheduler.schedule(context, db.getItem(id));

        yes(CaptureAutomation.requestRemoteDelete(context, "remote-unlinked", 1),
                "unlinked tombstone is accepted");
        eq(0, context.alarms.alarms.size(), "unlinked item alarm is cancelled first");
        yes(context.alarms.cancelled.size() > 0, "unlinked cancellation reached AlarmManager");
        eq(0, context.resolver.deleteCount, "unlinked item does not call provider delete");
        eq(null, db.getItem(id), "unlinked item is removed after cancellation");
        eq(0, db.pendingSyncCount(), "unlinked remote delete leaves no sync operation");
    }

    private static void olderVersionRejected() throws Exception {
        TestEnvironment.ContextImpl context = fresh();
        CaptureDatabase db = CaptureDatabase.get(context);
        long at = soon(60);
        long id = seed(db, "newer-local", "Keep newer local", at, 5, 703);
        context.resolver.addEvent(703, 7, "Keep newer local", "body", null);
        queue(db, "newer-local", "UPSERT", 5);
        ReminderScheduler.schedule(context, db.getItem(id));

        no(CaptureAutomation.requestRemoteDelete(context, "newer-local", 4),
                "older tombstone loses to newer local version");
        CaptureItem kept = db.getItem(id);
        notNull(kept, "newer local row is preserved");
        eq("APPROVED", kept.status, "older tombstone does not change status");
        eq("Keep newer local", kept.title, "older tombstone does not replace data");
        eq(5, kept.version, "newer local version is preserved");
        notNull(db.getSyncOperation("newer-local", "UPSERT"), "newer UPSERT remains queued");
        eq(1, context.alarms.alarms.size(), "rejected tombstone does not cancel alarm");
        eq(0, context.resolver.deleteCount, "rejected tombstone does not touch provider");
    }

    private static void missingLocalIdempotent() throws Exception {
        TestEnvironment.ContextImpl context = fresh();
        CaptureDatabase db = CaptureDatabase.get(context);
        queue(db, "already-gone", "UPSERT", 2);

        yes(CaptureAutomation.requestRemoteDelete(context, "already-gone", 2),
                "missing local tombstone is accepted");
        eq(0, db.pendingSyncCount(), "missing local cleanup removes stale operation");
        yes(CaptureAutomation.requestRemoteDelete(context, "already-gone", 2),
                "repeated missing tombstone remains accepted");
        eq(0, db.pendingSyncCount(), "repeated missing tombstone stays clean");
        eq(0, context.resolver.deleteCount, "missing local tombstone does not touch provider");
    }

    private static TestEnvironment.ContextImpl fresh() throws Exception {
        Field field = CaptureDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
        PendingIntent.reset();
        SyncScheduler.requests = 0;
        return new TestEnvironment.ContextImpl();
    }

    private static long seed(CaptureDatabase db, String uuid, String title, long at,
                             int version, long eventId) {
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("item_uuid", uuid);
        values.put("audio_path", "");
        values.put("source_type", "WEB");
        values.put("source_mime", "application/octet-stream");
        values.put("area", "PERSONAL");
        values.put("transcript", "remote delete fixture");
        values.put("kind", "SCHEDULE");
        values.put("title", title);
        values.put("start_at", at);
        values.put("end_at", at + 3_600_000L);
        values.put("reminder_at", at);
        values.put("status", "APPROVED");
        values.put("calendar_event_id", eventId);
        values.put("approved_at", now);
        values.put("updated_at", now);
        values.put("version", version);
        values.put("created_at", now);
        values.put("remote_synced_version", Math.max(0, version - 1));
        values.put("calendar_sync_state", eventId > 0 ? "SYNCED" : "NONE");
        values.put("calendar_attempts", 0);
        values.put("calendar_next_attempt_at", 0);
        values.put("calendar_idempotency_key", uuid + "-key");
        values.put("calendar_selected_id", eventId > 0 ? 7 : 0);
        values.put("calendar_timezone", "Asia/Seoul");
        values.put("calendar_delete_pending", 0);
        values.put("reminder_state", "NONE");
        values.put("reminder_generation", 1);
        values.put("scheduled_reminder_at", at);
        values.put("deleted_at", 0);
        return db.getWritableDatabase().insertOrThrow("captures", null, values);
    }

    private static void queue(CaptureDatabase db, String uuid, String operation, int version) {
        ContentValues values = new ContentValues();
        values.put("item_uuid", uuid);
        values.put("operation", operation);
        values.put("item_version", version);
        values.put("updated_at", System.currentTimeMillis());
        values.put("attempts", 0);
        values.put("payload", "");
        db.getWritableDatabase().insertWithOnConflict("sync_queue", null, values,
                android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static long soon(int minutes) {
        return System.currentTimeMillis() + minutes * 60_000L;
    }

    private interface Checked { void run() throws Exception; }

    private static void run(String name, Checked body) throws Exception {
        body.run();
        cases++;
        System.out.println("PASS " + name);
    }

    private static void yes(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    private static void no(boolean value, String message) {
        yes(!value, message);
    }

    private static void eq(Object expected, Object actual, String message) {
        assertions++;
        if (!Objects.equals(expected, actual)) {
            throw new AssertionError(message + " expected=" + expected + " actual=" + actual);
        }
    }

    private static void notNull(Object value, String message) {
        yes(value != null, message);
    }
}
