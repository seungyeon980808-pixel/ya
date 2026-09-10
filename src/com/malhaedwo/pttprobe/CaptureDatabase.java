package com.malhaedwo.pttprobe;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.json.JSONObject;

public final class CaptureDatabase extends SQLiteOpenHelper {
    private static final String DB_NAME = "malhaedwo.db";
    private static final int DB_VERSION = 5;
    private static volatile CaptureDatabase instance;
    private final Context appContext;

    public static synchronized CaptureDatabase get(Context context) {
        CaptureDatabase current = instance;
        if (current == null) {
            Context appContext = context.getApplicationContext();
            BackupBeforeMigration.ensure(appContext);
            BackupBeforeMigration.ensureCurrentRelease(appContext);
            instance = current = new CaptureDatabase(appContext);
        }
        return current;
    }

    private CaptureDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE captures (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item_uuid TEXT NOT NULL," +
                "audio_path TEXT NOT NULL," +
                "source_type TEXT NOT NULL DEFAULT 'AUDIO'," +
                "source_mime TEXT NOT NULL DEFAULT 'audio/wav'," +
                "area TEXT NOT NULL DEFAULT 'PERSONAL'," +
                "transcript TEXT," +
                "kind TEXT NOT NULL DEFAULT 'UNKNOWN'," +
                "title TEXT," +
                "start_at INTEGER NOT NULL DEFAULT 0," +
                "end_at INTEGER NOT NULL DEFAULT 0," +
                "reminder_at INTEGER NOT NULL DEFAULT 0," +
                "status TEXT NOT NULL," +
                "calendar_event_id INTEGER NOT NULL DEFAULT 0," +
                "approved_at INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL," +
                "version INTEGER NOT NULL DEFAULT 1," +
                "error TEXT," +
                "created_at INTEGER NOT NULL," +
                "duration_ms INTEGER NOT NULL DEFAULT 0," +
                "peak_amplitude INTEGER NOT NULL DEFAULT 0," +
                "source TEXT," +
                "remote_synced_version INTEGER NOT NULL DEFAULT 0," +
                "remote_synced_at INTEGER NOT NULL DEFAULT 0," +
                "drive_file_id TEXT," +
                "audio_sha256 TEXT," +
                "sync_error TEXT," +
                "remote_calendar_enabled INTEGER NOT NULL DEFAULT 0," +
                "remote_calendar_id TEXT," +
                "remote_calendar_event_id TEXT," +
                "calendar_sync_state TEXT NOT NULL DEFAULT 'NONE'," +
                "calendar_sync_error TEXT," +
                "calendar_attempts INTEGER NOT NULL DEFAULT 0," +
                "calendar_next_attempt_at INTEGER NOT NULL DEFAULT 0," +
                "calendar_idempotency_key TEXT," +
                "calendar_selected_id INTEGER NOT NULL DEFAULT 0," +
                "calendar_timezone TEXT," +
                "calendar_delete_pending INTEGER NOT NULL DEFAULT 0," +
                "reminder_state TEXT NOT NULL DEFAULT 'NONE'," +
                "reminder_generation INTEGER NOT NULL DEFAULT 0," +
                "scheduled_reminder_at INTEGER NOT NULL DEFAULT 0," +
                "alarm_last_error TEXT," +
                "deleted_at INTEGER NOT NULL DEFAULT 0)");
        db.execSQL("CREATE INDEX captures_status_idx ON captures(status)");
        db.execSQL("CREATE INDEX captures_start_idx ON captures(start_at)");
        db.execSQL("CREATE UNIQUE INDEX captures_uuid_idx ON captures(item_uuid)");
        createSyncQueue(db);
    }

    @Override public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 1) {
            onCreate(db);
            return;
        }
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE captures ADD COLUMN item_uuid TEXT");
            db.execSQL("ALTER TABLE captures ADD COLUMN source_type TEXT NOT NULL DEFAULT 'AUDIO'");
            db.execSQL("ALTER TABLE captures ADD COLUMN source_mime TEXT NOT NULL DEFAULT 'audio/wav'");
            db.execSQL("ALTER TABLE captures ADD COLUMN area TEXT NOT NULL DEFAULT 'PERSONAL'");
            db.execSQL("ALTER TABLE captures ADD COLUMN approved_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE captures ADD COLUMN updated_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE captures ADD COLUMN version INTEGER NOT NULL DEFAULT 1");
            long now = System.currentTimeMillis();
            db.execSQL("UPDATE captures SET updated_at=CASE WHEN created_at>0 THEN created_at ELSE ? END", new Object[]{now});
            db.execSQL("UPDATE captures SET status='PENDING' WHERE status IN ('READY','REVIEW')");
            db.execSQL("UPDATE captures SET status='APPROVED', approved_at=CASE WHEN created_at>0 THEN created_at ELSE ? END WHERE status='SYNCED'", new Object[]{now});
            try (Cursor cursor = db.query("captures", new String[]{"_id"}, "item_uuid IS NULL", null, null, null, null)) {
                while (cursor.moveToNext()) {
                    ContentValues values = new ContentValues();
                    values.put("item_uuid", UUID.randomUUID().toString());
                    db.update("captures", values, "_id=?", new String[]{String.valueOf(cursor.getLong(0))});
                }
            }
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS captures_uuid_idx ON captures(item_uuid)");
        }
        if (oldVersion < 3) {
            db.execSQL("ALTER TABLE captures ADD COLUMN remote_synced_version INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE captures ADD COLUMN remote_synced_at INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE captures ADD COLUMN drive_file_id TEXT");
            db.execSQL("ALTER TABLE captures ADD COLUMN audio_sha256 TEXT");
            db.execSQL("ALTER TABLE captures ADD COLUMN sync_error TEXT");
            createSyncQueue(db);
            db.execSQL("INSERT OR REPLACE INTO sync_queue(item_uuid,operation,item_version,updated_at,payload) " +
                    "SELECT item_uuid,'UPSERT',version,updated_at,'' FROM captures WHERE item_uuid IS NOT NULL");
            db.execSQL("INSERT OR REPLACE INTO sync_queue(item_uuid,operation,item_version,updated_at,payload) " +
                    "SELECT item_uuid,'UPLOAD',version,updated_at,'' FROM captures WHERE item_uuid IS NOT NULL AND audio_path<>''");
        }
        if (oldVersion < 4) {
            db.execSQL("ALTER TABLE captures ADD COLUMN remote_calendar_enabled INTEGER NOT NULL DEFAULT 0");
            db.execSQL("ALTER TABLE captures ADD COLUMN remote_calendar_id TEXT");
            db.execSQL("ALTER TABLE captures ADD COLUMN remote_calendar_event_id TEXT");
        }
        if (oldVersion < 5) addCalendarReliabilityColumns(db);
    }

    private static void addCalendarReliabilityColumns(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_sync_state TEXT NOT NULL DEFAULT 'NONE'");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_sync_error TEXT");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_attempts INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_next_attempt_at INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_idempotency_key TEXT");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_selected_id INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_timezone TEXT");
        db.execSQL("ALTER TABLE captures ADD COLUMN calendar_delete_pending INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE captures ADD COLUMN reminder_state TEXT NOT NULL DEFAULT 'NONE'");
        db.execSQL("ALTER TABLE captures ADD COLUMN reminder_generation INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE captures ADD COLUMN scheduled_reminder_at INTEGER NOT NULL DEFAULT 0");
        db.execSQL("ALTER TABLE captures ADD COLUMN alarm_last_error TEXT");
        db.execSQL("ALTER TABLE captures ADD COLUMN deleted_at INTEGER NOT NULL DEFAULT 0");
    }

    private static void createSyncQueue(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS sync_queue (" +
                "_id INTEGER PRIMARY KEY AUTOINCREMENT," +
                "item_uuid TEXT NOT NULL," +
                "operation TEXT NOT NULL," +
                "item_version INTEGER NOT NULL DEFAULT 0," +
                "updated_at INTEGER NOT NULL," +
                "attempts INTEGER NOT NULL DEFAULT 0," +
                "payload TEXT," +
                "last_error TEXT," +
                "UNIQUE(item_uuid,operation))");
        db.execSQL("CREATE INDEX IF NOT EXISTS sync_queue_order_idx ON sync_queue(operation,updated_at)");
    }

    public synchronized long insertAudio(File file, String source, long durationMs, int peakAmplitude) {
        ContentValues values = new ContentValues();
        long now = System.currentTimeMillis();
        values.put("item_uuid", UUID.randomUUID().toString());
        values.put("audio_path", file.getAbsolutePath());
        values.put("source_type", "AUDIO");
        values.put("source_mime", "audio/wav");
        values.put("area", "PERSONAL");
        values.put("status", "TRANSCRIBING");
        values.put("created_at", now);
        values.put("updated_at", now);
        values.put("duration_ms", durationMs);
        values.put("peak_amplitude", peakAmplitude);
        values.put("source", source);
        long id = getWritableDatabase().insertOrThrow("captures", null, values);
        queueItem(id, true);
        return id;
    }

    public synchronized boolean applyParsed(long id, int expectedVersion, String transcript, ParsedCommand parsed) {
        CaptureItem current = getItem(id);
        if (current == null || current.version != expectedVersion || !"TRANSCRIBING".equals(current.status)) return false;
        ContentValues values = new ContentValues();
        values.put("transcript", transcript);
        values.put("kind", parsed.kind);
        values.put("title", parsed.title);
        values.put("start_at", parsed.startAt);
        values.put("end_at", parsed.endAt);
        values.put("reminder_at", parsed.reminderAt);
        values.put("status", "PENDING");
        values.put("updated_at", System.currentTimeMillis());
        values.put("version", expectedVersion + 1);
        values.putNull("error");
        int changed = getWritableDatabase().update("captures", values,
                "_id=? AND version=? AND status='TRANSCRIBING'",
                new String[]{String.valueOf(id), String.valueOf(expectedVersion)});
        if (changed == 0) return false;
        queueItem(id, false);
        return true;
    }

    public synchronized void beginTranscription(long id) {
        ContentValues values = new ContentValues();
        values.put("status", "TRANSCRIBING");
        values.putNull("error");
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized boolean markTranscriptionError(long id, int expectedVersion, String error) {
        CaptureItem current = getItem(id);
        if (current == null || current.version != expectedVersion || !"TRANSCRIBING".equals(current.status)) return false;
        ContentValues values = new ContentValues();
        values.put("status", "AUDIO_ONLY");
        values.put("error", error);
        values.put("updated_at", System.currentTimeMillis());
        values.put("version", expectedVersion + 1);
        int changed = getWritableDatabase().update("captures", values,
                "_id=? AND version=? AND status='TRANSCRIBING'",
                new String[]{String.valueOf(id), String.valueOf(expectedVersion)});
        if (changed == 0) return false;
        queueItem(id, false);
        return true;
    }

    public synchronized void updateEdited(long id, String title, String kind, String area, long startAt, long endAt, long reminderAt) {
        CaptureItem before = getItem(id);
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("kind", kind);
        values.put("area", "SCHOOL".equals(area) ? "SCHOOL" : "PERSONAL");
        values.put("start_at", startAt);
        values.put("end_at", endAt);
        values.put("reminder_at", reminderAt);
        boolean approved = before != null && ("APPROVED".equals(before.status) || "DONE".equals(before.status));
        values.put("status", approved ? before.status : "PENDING");
        if (approved && before != null && before.hasLocalCalendarWork()) {
            values.put("calendar_sync_state", CalendarReliability.CALENDAR_PENDING);
            values.put("calendar_next_attempt_at", 0);
            values.putNull("calendar_sync_error");
        }
        touch(values, id);
        values.putNull("error");
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        queueItem(id, false);
    }

    public synchronized void approve(long id) {
        CaptureItem item = getItem(id);
        if (item == null) return;
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("status", "APPROVED");
        values.put("approved_at", now);
        values.put("updated_at", now);
        values.put("version", item.version + 1);
        values.putNull("error");
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        queueItem(id, false);
    }

    public synchronized void markCalendarSynced(long id, long eventId) {
        markCalendarSynced(id, eventId, 0, null);
    }

    public synchronized void markCalendarSynced(long id, long eventId, long selectedCalendarId, String timezone) {
        CaptureItem item = getItem(id);
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", eventId);
        values.put("status", "APPROVED");
        values.put("calendar_sync_state", CalendarReliability.CALENDAR_SYNCED);
        values.putNull("calendar_sync_error");
        values.put("calendar_attempts", 0);
        values.put("calendar_next_attempt_at", 0);
        values.put("calendar_delete_pending", 0);
        if (selectedCalendarId > 0) values.put("calendar_selected_id", selectedCalendarId);
        else if (item != null && item.calendarSelectedId > 0) values.put("calendar_selected_id", item.calendarSelectedId);
        if (CalendarReliability.hasText(timezone)) values.put("calendar_timezone", timezone);
        else if (item != null && CalendarReliability.hasText(item.calendarTimezone)) {
            values.put("calendar_timezone", item.calendarTimezone);
        }
        touch(values, id);
        values.putNull("error");
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        queueItem(id, false);
    }

    public synchronized void clearCalendarEvent(long id) {
        ContentValues values = new ContentValues();
        values.put("calendar_event_id", 0);
        values.put("calendar_sync_state", CalendarReliability.CALENDAR_NONE);
        values.putNull("calendar_sync_error");
        values.put("calendar_attempts", 0);
        values.put("calendar_next_attempt_at", 0);
        values.put("calendar_delete_pending", 0);
        values.put("calendar_selected_id", 0);
        touch(values, id);
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        queueItem(id, false);
    }

    public synchronized CaptureItem prepareCalendarUpsert(long id, long selectedCalendarId, String timezone, long now) {
        CaptureItem item = getItem(id);
        if (item == null) return null;
        ContentValues values = new ContentValues();
        if (!CalendarReliability.hasText(item.calendarIdempotencyKey)) {
            values.put("calendar_idempotency_key", CalendarReliability.newIdempotencyKey());
        }
        long snapshot = CalendarReliability.chooseCalendarSnapshot(item.calendarSelectedId, selectedCalendarId);
        if (snapshot > 0) values.put("calendar_selected_id", snapshot);
        if (CalendarReliability.hasText(timezone)) values.put("calendar_timezone", timezone);
        values.put("calendar_sync_state", CalendarReliability.CALENDAR_PENDING);
        values.put("calendar_next_attempt_at", now);
        values.putNull("calendar_sync_error");
        values.put("calendar_delete_pending", 0);
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        return getItem(id);
    }

    public synchronized void markCalendarFailure(long id, String error, long now) {
        CaptureItem item = getItem(id);
        int attempts = item == null ? 1 : item.calendarAttempts + 1;
        ContentValues values = new ContentValues();
        values.put("calendar_sync_state", CalendarReliability.CALENDAR_FAILED);
        values.put("calendar_sync_error", error);
        values.put("calendar_attempts", attempts);
        values.put("calendar_next_attempt_at", CalendarReliability.nextCalendarAttemptAt(now, attempts));
        values.put("error", error);
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void markCalendarDeletePending(long id, String error, long now) {
        CaptureItem item = getItem(id);
        if (item == null) return;
        ContentValues values = new ContentValues();
        values.put("status", "DELETE_PENDING");
        values.put("calendar_delete_pending", 1);
        values.put("calendar_sync_state", CalendarReliability.hasText(error)
                ? CalendarReliability.CALENDAR_DELETE_FAILED : CalendarReliability.CALENDAR_DELETE_PENDING);
        if (CalendarReliability.hasText(error)) values.put("calendar_sync_error", error);
        else values.putNull("calendar_sync_error");
        values.put("calendar_next_attempt_at", now);
        values.put("deleted_at", item.deletedAt > 0 ? item.deletedAt : now);
        touch(values, id);
        SQLiteDatabase db = getWritableDatabase();
        db.update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        if (item.uuid != null) db.delete("sync_queue", "item_uuid=?", new String[]{item.uuid});
    }

    public synchronized void markCalendarDeleteFailure(long id, String error, long now) {
        CaptureItem item = getItem(id);
        int attempts = item == null ? 1 : item.calendarAttempts + 1;
        ContentValues values = new ContentValues();
        values.put("status", "DELETE_PENDING");
        values.put("calendar_delete_pending", 1);
        values.put("calendar_sync_state", CalendarReliability.CALENDAR_DELETE_FAILED);
        values.put("calendar_sync_error", error);
        values.put("calendar_attempts", attempts);
        values.put("calendar_next_attempt_at", CalendarReliability.nextCalendarAttemptAt(now, attempts));
        values.put("error", error);
        if (item != null && item.deletedAt <= 0) values.put("deleted_at", now);
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized CaptureItem prepareReminder(long id, long now) {
        CaptureItem item = getItem(id);
        if (item == null) return null;
        ContentValues values = new ContentValues();
        if (!item.isReminderEligible(now)) {
            values.put("reminder_state", CalendarReliability.REMINDER_NONE);
            values.put("scheduled_reminder_at", 0);
            values.putNull("alarm_last_error");
        } else {
            long generation = item.reminderGeneration;
            if (generation <= 0 || item.scheduledReminderAt != item.reminderAt) generation++;
            values.put("reminder_generation", generation);
            values.put("scheduled_reminder_at", item.reminderAt);
            values.put("reminder_state", CalendarReliability.REMINDER_PENDING);
            values.putNull("alarm_last_error");
        }
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        return getItem(id);
    }

    public synchronized void markReminderResult(long id, ReminderScheduler.Result result, String error) {
        ContentValues values = new ContentValues();
        values.put("reminder_state", result == null ? CalendarReliability.REMINDER_NONE : result.state);
        if (CalendarReliability.hasText(error)) values.put("alarm_last_error", error);
        else values.putNull("alarm_last_error");
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void clearReminder(long id) {
        ContentValues values = new ContentValues();
        values.put("reminder_state", CalendarReliability.REMINDER_NONE);
        values.put("scheduled_reminder_at", 0);
        values.putNull("alarm_last_error");
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
    }

    public synchronized void markDone(long id, boolean done) {
        ContentValues values = new ContentValues();
        values.put("status", done ? "DONE" : "APPROVED");
        touch(values, id);
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        queueItem(id, false);
    }

    public synchronized void setError(long id, String error) {
        ContentValues values = new ContentValues();
        values.put("error", error);
        touch(values, id);
        getWritableDatabase().update("captures", values, "_id=?", new String[]{String.valueOf(id)});
        queueItem(id, false);
    }

    public synchronized void delete(long id) {
        CaptureItem item = getItem(id);
        if (item == null) return;
        SQLiteDatabase db = getWritableDatabase();
        long now = System.currentTimeMillis();
        db.beginTransaction();
        try {
            db.delete("sync_queue", "item_uuid=?", new String[]{item.uuid});
            JSONObject payload = new JSONObject();
            try { payload.put("driveFileId", item.driveFileId == null ? "" : item.driveFileId); }
            catch (Throwable ignored) {}
            enqueue(db, item.uuid, "DELETE", item.version + 1, now, payload.toString());
            db.delete("captures", "_id=?", new String[]{String.valueOf(id)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (item.audioPath != null && !item.audioPath.isEmpty()) {
            File file = new File(item.audioPath);
            if (file.isFile()) file.delete();
        }
        SyncScheduler.request(appContext);
    }

    public synchronized CaptureItem getItem(long id) {
        try (Cursor cursor = getReadableDatabase().query("captures", null, "_id=?",
                new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public synchronized List<CaptureItem> listAll(int limit) {
        ArrayList<CaptureItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("captures", null, null, null,
                null, null, "created_at DESC", String.valueOf(limit))) {
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
        }
        return items;
    }

    public synchronized List<CaptureItem> listByStatus(String status) {
        ArrayList<CaptureItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("captures", null, "status=?",
                new String[]{status}, null, null, "created_at ASC")) {
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
        }
        return items;
    }

    public synchronized List<CaptureItem> listPendingTranscriptions() {
        ArrayList<CaptureItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("captures", null,
                "status='TRANSCRIBING'", null, null, null, "created_at ASC")) {
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
        }
        return items;
    }

    public synchronized List<CaptureItem> listFutureReminders(long now) {
        ArrayList<CaptureItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("captures", null,
                "reminder_at>? AND status='APPROVED' AND kind='SCHEDULE'", new String[]{String.valueOf(now)},
                null, null, "reminder_at ASC")) {
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
        }
        return items;
    }

    public synchronized List<CaptureItem> listAutomationCandidates() {
        ArrayList<CaptureItem> items = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query("captures", null,
                "status='APPROVED' OR status='DELETE_PENDING' OR calendar_delete_pending=1",
                null, null, null, "updated_at ASC")) {
            while (cursor.moveToNext()) items.add(fromCursor(cursor));
        }
        return items;
    }

    public synchronized int countByStatus(String status) {
        try (Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COUNT(*) FROM captures WHERE status=?", new String[]{status})) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized CaptureItem getByUuid(String uuid) {
        if (uuid == null) return null;
        try (Cursor cursor = getReadableDatabase().query("captures", null, "item_uuid=?",
                new String[]{uuid}, null, null, null)) {
            return cursor.moveToFirst() ? fromCursor(cursor) : null;
        }
    }

    public synchronized List<SyncOperation> listSyncOperations() {
        ArrayList<SyncOperation> out = new ArrayList<>();
        String order = "CASE operation WHEN 'DELETE' THEN 0 WHEN 'UPSERT' THEN 1 ELSE 2 END, updated_at ASC";
        try (Cursor cursor = getReadableDatabase().query("sync_queue", null, null, null,
                null, null, order)) {
            while (cursor.moveToNext()) {
                SyncOperation op = new SyncOperation();
                op.id = getLong(cursor, "_id");
                op.itemUuid = getString(cursor, "item_uuid");
                op.operation = getString(cursor, "operation");
                op.itemVersion = (int) getLong(cursor, "item_version");
                op.updatedAt = getLong(cursor, "updated_at");
                op.attempts = (int) getLong(cursor, "attempts");
                op.payload = getString(cursor, "payload");
                op.lastError = getString(cursor, "last_error");
                out.add(op);
            }
        }
        return out;
    }

    public synchronized SyncOperation getSyncOperation(String uuid, String operation) {
        try (Cursor cursor = getReadableDatabase().query("sync_queue", null,
                "item_uuid=? AND operation=?", new String[]{uuid, operation}, null, null, null)) {
            if (!cursor.moveToFirst()) return null;
            SyncOperation op = new SyncOperation();
            op.id = getLong(cursor, "_id");
            op.itemUuid = getString(cursor, "item_uuid");
            op.operation = getString(cursor, "operation");
            op.itemVersion = (int) getLong(cursor, "item_version");
            op.updatedAt = getLong(cursor, "updated_at");
            op.attempts = (int) getLong(cursor, "attempts");
            op.payload = getString(cursor, "payload");
            op.lastError = getString(cursor, "last_error");
            return op;
        }
    }

    public synchronized int pendingSyncCount() {
        try (Cursor cursor = getReadableDatabase().rawQuery("SELECT COUNT(*) FROM sync_queue", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public synchronized void queueAllForSync() {
        for (CaptureItem item : listAll(Integer.MAX_VALUE)) {
            if (item.isDeletePending()) continue;
            enqueue(getWritableDatabase(), item.uuid, "UPSERT", item.version, item.updatedAt, "");
            if (item.audioPath != null && !item.audioPath.isEmpty() &&
                    new File(item.audioPath).isFile() && (item.driveFileId == null || item.driveFileId.isEmpty())) {
                enqueue(getWritableDatabase(), item.uuid, "UPLOAD", item.version, item.updatedAt, "");
            }
        }
        SyncScheduler.request(appContext);
    }

    public synchronized void removeSyncOperation(long operationId) {
        getWritableDatabase().delete("sync_queue", "_id=?", new String[]{String.valueOf(operationId)});
    }

    public synchronized void removeSyncOperations(String uuid) {
        getWritableDatabase().delete("sync_queue", "item_uuid=?", new String[]{uuid});
    }

    public synchronized void markSyncFailure(SyncOperation operation, String error) {
        ContentValues values = new ContentValues();
        values.put("attempts", operation.attempts + 1);
        values.put("last_error", error);
        getWritableDatabase().update("sync_queue", values, "_id=?", new String[]{String.valueOf(operation.id)});
        ContentValues capture = new ContentValues();
        capture.put("sync_error", error);
        getWritableDatabase().update("captures", capture, "item_uuid=?", new String[]{operation.itemUuid});
    }

    public synchronized void markMetadataSynced(String uuid, int version, long at) {
        ContentValues values = new ContentValues();
        values.put("remote_synced_version", version);
        values.put("remote_synced_at", at);
        values.putNull("sync_error");
        getWritableDatabase().update("captures", values, "item_uuid=?", new String[]{uuid});
    }

    public synchronized void markDriveUploaded(String uuid, String driveFileId, String sha256) {
        ContentValues values = new ContentValues();
        values.put("drive_file_id", driveFileId);
        values.put("audio_sha256", sha256);
        values.putNull("sync_error");
        getWritableDatabase().update("captures", values, "item_uuid=?", new String[]{uuid});
    }

    public synchronized long applyRemote(RemoteCapture remote) {
        CaptureItem existing = getByUuid(remote.uuid);
        ContentValues values = new ContentValues();
        values.put("item_uuid", remote.uuid);
        values.put("source_type", remote.sourceType == null || remote.sourceType.isEmpty() ? "WEB" : remote.sourceType);
        values.put("source_mime", "AUDIO".equals(remote.sourceType) ? "audio/wav" : "application/octet-stream");
        values.put("area", "SCHOOL".equals(remote.area) ? "SCHOOL" : "PERSONAL");
        values.put("transcript", remote.transcript);
        values.put("kind", remote.kind);
        values.put("title", remote.title);
        values.put("start_at", remote.startAt);
        values.put("end_at", remote.endAt);
        values.put("reminder_at", remote.reminderAt);
        values.put("status", remote.status);
        values.put("approved_at", remote.approvedAt);
        values.put("updated_at", remote.updatedAt);
        values.put("version", remote.version);
        values.put("created_at", remote.createdAt > 0 ? remote.createdAt : remote.updatedAt);
        values.put("remote_synced_version", remote.version);
        values.put("remote_synced_at", System.currentTimeMillis());
        values.put("remote_calendar_enabled", remote.remoteCalendarEnabled ? 1 : 0);
        values.put("remote_calendar_id", remote.remoteCalendarId);
        values.put("remote_calendar_event_id", remote.remoteCalendarEventId);
        values.putNull("sync_error");
        boolean localCalendarContentChanged = existing != null && existing.calendarEventId > 0
                && !existing.isDeletePending()
                && "APPROVED".equals(existing.status) && "APPROVED".equals(remote.status)
                && (!same(existing.title, remote.title) || existing.startAt != remote.startAt
                || existing.endAt != remote.endAt || !same(existing.kind, remote.kind)
                || !same(existing.transcript, remote.transcript));
        if (localCalendarContentChanged) {
            values.put("calendar_sync_state", CalendarReliability.CALENDAR_PENDING);
            values.put("calendar_next_attempt_at", 0);
            values.putNull("calendar_sync_error");
        }
        SQLiteDatabase db = getWritableDatabase();
        long id;
        if (existing == null) {
            values.put("audio_path", "");
            values.put("duration_ms", 0);
            values.put("peak_amplitude", 0);
            values.put("source", "google_sheets");
            id = db.insertOrThrow("captures", null, values);
        } else {
            db.update("captures", values, "_id=?", new String[]{String.valueOf(existing.id)});
            id = existing.id;
        }
        db.delete("sync_queue", "item_uuid=? AND operation='UPSERT' AND item_version<=?",
                new String[]{remote.uuid, String.valueOf(remote.version)});
        return id;
    }

    private static boolean same(String left, String right) {
        return left == null ? right == null : left.equals(right);
    }

    public synchronized void rememberRemoteCalendarLink(String uuid, boolean enabled, String calendarId, String eventId) {
        ContentValues values = new ContentValues();
        values.put("remote_calendar_enabled", enabled ? 1 : 0);
        values.put("remote_calendar_id", calendarId == null ? "" : calendarId);
        values.put("remote_calendar_event_id", eventId == null ? "" : eventId);
        getWritableDatabase().update("captures", values, "item_uuid=?", new String[]{uuid});
    }

    /**
     * Applies a server tombstone without allowing local Calendar cleanup to be skipped.
     * Returns the local row id when cleanup is required, 0 when already absent/finalized,
     * or -1 when a newer local version must win.
     */
    public synchronized long applyRemoteDeleted(String uuid, int remoteVersion) {
        CaptureItem item = getByUuid(uuid);
        SQLiteDatabase db = getWritableDatabase();
        if (item == null) {
            db.delete("sync_queue", "item_uuid=? AND item_version<=?",
                    new String[]{uuid, String.valueOf(remoteVersion)});
            return 0;
        }

        boolean matchingRemoteDelete = item.isDeletePending()
                && item.remoteSyncedVersion == remoteVersion
                && item.version == remoteVersion;
        if (matchingRemoteDelete) return item.id; // Repeated tombstones must never skip provider cleanup.
        if (item.version > remoteVersion) return -1;

        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("status", "DELETE_PENDING");
        values.put("calendar_delete_pending", 1);
        values.put("calendar_sync_state", CalendarReliability.CALENDAR_DELETE_PENDING);
        values.putNull("calendar_sync_error");
        values.put("calendar_next_attempt_at", now);
        values.put("deleted_at", item.deletedAt > 0 ? item.deletedAt : now);
        values.put("remote_synced_version", remoteVersion);
        values.put("remote_synced_at", now);
        values.put("version", remoteVersion);
        values.putNull("sync_error");
        db.beginTransaction();
        try {
            int changed = db.update("captures", values, "_id=? AND version<=?",
                    new String[]{String.valueOf(item.id), String.valueOf(remoteVersion)});
            if (changed == 0) return -1;
            db.delete("sync_queue", "item_uuid=? AND item_version<=?",
                    new String[]{uuid, String.valueOf(remoteVersion)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        return item.id;
    }

    /** Only called after dependent Calendar cleanup has succeeded. */
    synchronized void finishRemoteDeleted(String uuid, int remoteVersion) {
        CaptureItem item = getByUuid(uuid);
        if (item == null || !item.isDeletePending() || item.version != remoteVersion
                || item.remoteSyncedVersion != remoteVersion) return;
        SQLiteDatabase db = getWritableDatabase();
        int removed = 0;
        db.beginTransaction();
        try {
            db.delete("sync_queue", "item_uuid=? AND item_version<=?",
                    new String[]{uuid, String.valueOf(remoteVersion)});
            removed = db.delete("captures", "_id=? AND version=?",
                    new String[]{String.valueOf(item.id), String.valueOf(remoteVersion)});
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
        if (removed > 0 && item.audioPath != null && !item.audioPath.isEmpty()) {
            File file = new File(item.audioPath);
            if (file.isFile()) file.delete();
        }
    }

    private void queueItem(long id, boolean includeUpload) {
        CaptureItem item = getItem(id);
        if (item == null || item.uuid == null) return;
        enqueue(getWritableDatabase(), item.uuid, "UPSERT", item.version, item.updatedAt, "");
        if (includeUpload && item.audioPath != null && !item.audioPath.isEmpty()) {
            enqueue(getWritableDatabase(), item.uuid, "UPLOAD", item.version, item.updatedAt, "");
        }
        SyncScheduler.request(appContext);
    }

    private static void enqueue(SQLiteDatabase db, String uuid, String operation, int version, long updatedAt, String payload) {
        ContentValues values = new ContentValues();
        values.put("item_uuid", uuid);
        values.put("operation", operation);
        values.put("item_version", version);
        values.put("updated_at", updatedAt > 0 ? updatedAt : System.currentTimeMillis());
        values.put("payload", payload == null ? "" : payload);
        values.put("attempts", 0);
        values.putNull("last_error");
        db.insertWithOnConflict("sync_queue", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private synchronized void touch(ContentValues values, long id) {
        CaptureItem item = getItem(id);
        values.put("updated_at", System.currentTimeMillis());
        values.put("version", item == null ? 1 : item.version + 1);
    }

    private static CaptureItem fromCursor(Cursor c) {
        CaptureItem item = new CaptureItem();
        item.id = getLong(c, "_id");
        item.uuid = getString(c, "item_uuid");
        item.audioPath = getString(c, "audio_path");
        item.sourceType = getString(c, "source_type");
        item.sourceMime = getString(c, "source_mime");
        item.area = getString(c, "area");
        item.transcript = getString(c, "transcript");
        item.kind = getString(c, "kind");
        item.title = getString(c, "title");
        item.startAt = getLong(c, "start_at");
        item.endAt = getLong(c, "end_at");
        item.reminderAt = getLong(c, "reminder_at");
        item.status = getString(c, "status");
        item.calendarEventId = getLong(c, "calendar_event_id");
        item.approvedAt = getLong(c, "approved_at");
        item.updatedAt = getLong(c, "updated_at");
        item.version = (int) getLong(c, "version");
        item.error = getString(c, "error");
        item.createdAt = getLong(c, "created_at");
        item.durationMs = getLong(c, "duration_ms");
        item.peakAmplitude = (int) getLong(c, "peak_amplitude");
        item.source = getString(c, "source");
        item.remoteSyncedVersion = (int) getLong(c, "remote_synced_version");
        item.remoteSyncedAt = getLong(c, "remote_synced_at");
        item.driveFileId = getString(c, "drive_file_id");
        item.audioSha256 = getString(c, "audio_sha256");
        item.syncError = getString(c, "sync_error");
        item.remoteCalendarEnabled = getLong(c, "remote_calendar_enabled") != 0;
        item.remoteCalendarId = getString(c, "remote_calendar_id");
        item.remoteCalendarEventId = getString(c, "remote_calendar_event_id");
        item.calendarSyncState = getString(c, "calendar_sync_state");
        if (item.calendarSyncState == null) item.calendarSyncState = CalendarReliability.CALENDAR_NONE;
        item.calendarSyncError = getString(c, "calendar_sync_error");
        item.calendarAttempts = (int) getLong(c, "calendar_attempts");
        item.calendarNextAttemptAt = getLong(c, "calendar_next_attempt_at");
        item.calendarIdempotencyKey = getString(c, "calendar_idempotency_key");
        item.calendarSelectedId = getLong(c, "calendar_selected_id");
        item.calendarTimezone = getString(c, "calendar_timezone");
        item.calendarDeletePending = getLong(c, "calendar_delete_pending") != 0;
        item.reminderState = getString(c, "reminder_state");
        if (item.reminderState == null) item.reminderState = CalendarReliability.REMINDER_NONE;
        item.reminderGeneration = getLong(c, "reminder_generation");
        item.scheduledReminderAt = getLong(c, "scheduled_reminder_at");
        item.alarmLastError = getString(c, "alarm_last_error");
        item.deletedAt = getLong(c, "deleted_at");
        return item;
    }

    private static long getLong(Cursor c, String column) {
        int index = c.getColumnIndex(column);
        return index < 0 || c.isNull(index) ? 0 : c.getLong(index);
    }

    private static String getString(Cursor c, String column) {
        int index = c.getColumnIndex(column);
        return index < 0 || c.isNull(index) ? null : c.getString(index);
    }
}
