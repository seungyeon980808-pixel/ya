package com.malhaedwo.pttprobe;

import android.database.sqlite.SQLiteOpenHelper;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Objects;
import java.util.Properties;

public final class BackupBeforeMigrationTest {
    private static int cases;
    private static int assertions;

    public static void main(String[] args) throws Exception {
        run("main, WAL, and SHM are copied byte-identically", BackupBeforeMigrationTest::copiesMainWalShm);
        run("missing optional SQLite files are allowed", BackupBeforeMigrationTest::missingOptionalFiles);
        run("completed snapshots stay immutable on duplicate call", BackupBeforeMigrationTest::duplicateCallIsImmutable);
        run("failed external phase prevents helper construction and stale temp retry recovers", BackupBeforeMigrationTest::partialFailureBlocksOpenAndRetries);
        run("unavailable app-specific external destination fails closed", BackupBeforeMigrationTest::inaccessibleDestinationFailsClosed);
        run("fresh install without a database is safe", BackupBeforeMigrationTest::freshInstallIsSafe);
        System.out.println("BackupBeforeMigrationTest passed: " + cases + " cases, " + assertions + " assertions");
    }

    private static void copiesMainWalShm() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        byte[] main = bytes("synthetic-main-v4");
        byte[] wal = bytes("synthetic-wal");
        byte[] shm = bytes("synthetic-shm");
        write(context.getDatabasePath("malhaedwo.db"), main);
        write(new File(context.getDatabasePath("malhaedwo.db").getPath() + "-wal"), wal);
        write(new File(context.getDatabasePath("malhaedwo.db").getPath() + "-shm"), shm);

        BackupBeforeMigration.ensure(context);

        File privateSnapshot = snapshot(context.getFilesDir());
        File externalSnapshot = snapshot(context.getExternalFilesDir(null));
        bytesEqual(main, read(new File(privateSnapshot, "malhaedwo.db")), "private main copy");
        bytesEqual(wal, read(new File(privateSnapshot, "malhaedwo.db-wal")), "private WAL copy");
        bytesEqual(shm, read(new File(privateSnapshot, "malhaedwo.db-shm")), "private SHM copy");
        bytesEqual(main, read(new File(externalSnapshot, "malhaedwo.db")), "external main copy");
        bytesEqual(wal, read(new File(externalSnapshot, "malhaedwo.db-wal")), "external WAL copy");
        bytesEqual(shm, read(new File(externalSnapshot, "malhaedwo.db-shm")), "external SHM copy");
        bytesEqual(read(new File(privateSnapshot, BackupBeforeMigration.MANIFEST_NAME)), read(new File(externalSnapshot, BackupBeforeMigration.MANIFEST_NAME)), "private/external manifests match");
        Properties manifest = properties(new File(privateSnapshot, BackupBeforeMigration.MANIFEST_NAME));
        eq("SNAPSHOT", manifest.getProperty("state"), "snapshot manifest state");
        eq("3", manifest.getProperty("fileCount"), "snapshot manifest count");
        yes(manifest.getProperty("file.0.sha256").matches("[0-9a-f]{64}"), "manifest records SHA-256");
    }

    private static void missingOptionalFiles() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        byte[] main = bytes("main-only");
        write(context.getDatabasePath("malhaedwo.db"), main);

        BackupBeforeMigration.ensure(context);

        File privateSnapshot = snapshot(context.getFilesDir());
        bytesEqual(main, read(new File(privateSnapshot, "malhaedwo.db")), "main-only snapshot");
        no(new File(privateSnapshot, "malhaedwo.db-wal").exists(), "absent WAL remains absent");
        no(new File(privateSnapshot, "malhaedwo.db-shm").exists(), "absent SHM remains absent");
        eq("1", properties(new File(privateSnapshot, BackupBeforeMigration.MANIFEST_NAME)).getProperty("fileCount"), "one-file manifest");
    }

    private static void duplicateCallIsImmutable() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        byte[] firstMain = bytes("first-main");
        byte[] firstJournal = bytes("first-rollback-journal");
        File database = context.getDatabasePath("malhaedwo.db");
        write(database, firstMain);
        write(new File(database.getPath() + "-journal"), firstJournal);
        BackupBeforeMigration.ensure(context);

        write(database, bytes("changed-after-migration"));
        write(new File(database.getPath() + "-journal"), bytes("changed-journal"));
        BackupBeforeMigration.ensure(context);

        bytesEqual(firstMain, read(new File(snapshot(context.getFilesDir()), "malhaedwo.db")), "private main is immutable");
        bytesEqual(firstJournal, read(new File(snapshot(context.getFilesDir()), "malhaedwo.db-journal")), "private journal is immutable");
        bytesEqual(firstMain, read(new File(snapshot(context.getExternalFilesDir(null)), "malhaedwo.db")), "external main is immutable");
        bytesEqual(firstJournal, read(new File(snapshot(context.getExternalFilesDir(null)), "malhaedwo.db-journal")), "external journal is immutable");
    }

    private static void partialFailureBlocksOpenAndRetries() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        byte[] original = bytes("must-survive-failed-external-phase");
        File database = context.getDatabasePath("malhaedwo.db");
        write(database, original);
        File blockedExternal = context.getExternalFilesDir(null);
        deleteTree(blockedExternal);
        write(blockedExternal, bytes("not-a-directory"));
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();

        expectBackupFailure(() -> CaptureDatabase.get(context), "external destination failure");
        eq(0, SQLiteOpenHelper.constructionCount(), "SQLiteOpenHelper is not constructed before both phases verify");
        bytesEqual(original, read(new File(snapshot(context.getFilesDir()), "malhaedwo.db")), "private phase completed before external failure");

        write(database, bytes("current-db-may-now-differ"));
        yes(blockedExternal.delete(), "remove blocking external file");
        yes(blockedExternal.mkdir(), "restore external directory");
        File staleTemp = new File(new File(blockedExternal, BackupBeforeMigration.BACKUP_ROOT), ".before-v5.tmp");
        yes(staleTemp.mkdirs(), "create simulated crash temp");
        write(new File(staleTemp, "partial"), bytes("partial-copy"));

        CaptureDatabase opened = CaptureDatabase.get(context);
        yes(opened != null, "retry permits helper construction after verified external copy");
        eq(1, SQLiteOpenHelper.constructionCount(), "helper constructed exactly once after successful retry");
        no(staleTemp.exists(), "stale temporary snapshot is removed on retry");
        bytesEqual(original, read(new File(snapshot(blockedExternal), "malhaedwo.db")), "retry external copy comes from immutable private snapshot");
    }

    private static void inaccessibleDestinationFailsClosed() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        write(context.getDatabasePath("malhaedwo.db"), bytes("existing-db"));
        context.useExternalFilesDir(null);
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();

        expectBackupFailure(() -> CaptureDatabase.get(context), "null external destination");
        eq(0, SQLiteOpenHelper.constructionCount(), "unavailable destination fails before helper construction");
    }

    private static void freshInstallIsSafe() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();

        CaptureDatabase database = CaptureDatabase.get(context);

        yes(database != null, "fresh install returns helper");
        eq(1, SQLiteOpenHelper.constructionCount(), "fresh install constructs helper");
        File privateSnapshot = snapshot(context.getFilesDir());
        eq("NO_DATABASE", properties(new File(privateSnapshot, BackupBeforeMigration.MANIFEST_NAME)).getProperty("state"), "fresh install marker state");
        no(snapshot(context.getExternalFilesDir(null)).exists(), "fresh install writes no external database snapshot");
    }

    private static File snapshot(File base) {
        return new File(new File(base, BackupBeforeMigration.BACKUP_ROOT), BackupBeforeMigration.SNAPSHOT_NAME);
    }

    private static void resetDatabaseSingleton() throws Exception {
        Field field = CaptureDatabase.class.getDeclaredField("instance");
        field.setAccessible(true);
        field.set(null, null);
    }

    private static Properties properties(File file) throws Exception {
        Properties value = new Properties();
        try (FileInputStream input = new FileInputStream(file)) { value.load(input); }
        return value;
    }

    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }
    private static byte[] read(File file) throws Exception { return Files.readAllBytes(file.toPath()); }
    private static void write(File file, byte[] value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null) parent.mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) { output.write(value); output.getFD().sync(); }
    }
    private static void deleteTree(File file) throws Exception {
        if (!file.exists()) return;
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) for (File child : children) deleteTree(child);
        }
        if (!file.delete()) throw new IllegalStateException("cannot delete test path " + file);
    }

    private interface Checked { void run() throws Exception; }
    private static void run(String name, Checked body) throws Exception { body.run(); cases++; System.out.println("PASS " + name); }
    private static void expectBackupFailure(Checked body, String message) throws Exception {
        assertions++;
        try { body.run(); } catch (BackupBeforeMigration.BackupException expected) {
            yes(expected.getMessage().contains("database was not opened") || expected.getMessage().contains("Database migration backup failed"), message + " has clear error");
            return;
        }
        throw new AssertionError(message + " expected BackupException");
    }
    private static void bytesEqual(byte[] expected, byte[] actual, String message) { assertions++; if (!Arrays.equals(expected, actual)) throw new AssertionError(message); }
    private static void yes(boolean value, String message) { assertions++; if (!value) throw new AssertionError(message); }
    private static void no(boolean value, String message) { yes(!value, message); }
    private static void eq(Object expected, Object actual, String message) { assertions++; if (!Objects.equals(expected, actual)) throw new AssertionError(message + " expected=" + expected + " actual=" + actual); }
}
