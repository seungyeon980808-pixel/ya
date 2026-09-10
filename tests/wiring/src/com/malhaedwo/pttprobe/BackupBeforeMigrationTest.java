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
        run("release snapshot copies current DB and all sidecars without replacing legacy", BackupBeforeMigrationTest::releasePreservesLegacyAndCopiesCurrent);
        run("release snapshots remain immutable across later opens", BackupBeforeMigrationTest::releaseRepeatedOpenIsImmutable);
        run("release external failure blocks helper and recovers staged retry", BackupBeforeMigrationTest::releaseExternalFailureAndRetry);
        run("invalid current source fails closed then recovers", BackupBeforeMigrationTest::releaseInvalidSourceFailsClosed);
        run("release private and external hash corruption each block helper", BackupBeforeMigrationTest::releaseCorruptionFailsClosed);
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
        eq("NO_DATABASE", properties(new File(releaseSnapshot(context.getFilesDir()), BackupBeforeMigration.MANIFEST_NAME)).getProperty("state"), "fresh install release marker state");
        no(releaseSnapshot(context.getExternalFilesDir(null)).exists(), "fresh install writes no external release snapshot");
    }


    private static void releasePreservesLegacyAndCopiesCurrent() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        File database = context.getDatabasePath("malhaedwo.db");
        byte[] legacy = bytes("synthetic-legacy-v4");
        write(database, legacy);
        BackupBeforeMigration.ensure(context);
        byte[] legacyManifest = read(new File(snapshot(context.getFilesDir()), BackupBeforeMigration.MANIFEST_NAME));
        String[] suffixes = {"", "-wal", "-shm", "-journal"};
        for (String suffix : suffixes) write(new File(database.getPath() + suffix), bytes("current-v5" + suffix));
        write(new File(context.getFilesDir(), "private-setting.txt"), bytes("must-not-be-exported"));
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();
        yes(CaptureDatabase.get(context) != null, "helper follows both verified backups");
        eq(1, SQLiteOpenHelper.constructionCount(), "helper constructed once");
        for (String suffix : suffixes) {
            byte[] current = bytes("current-v5" + suffix);
            bytesEqual(current, read(new File(releaseSnapshot(context.getFilesDir()), "malhaedwo.db" + suffix)), "release private current " + suffix);
            bytesEqual(current, read(new File(releaseSnapshot(context.getExternalFilesDir(null)), "malhaedwo.db" + suffix)), "release external current " + suffix);
            bytesEqual(current, read(new File(database.getPath() + suffix)), "live source unchanged " + suffix);
        }
        bytesEqual(legacy, read(new File(snapshot(context.getFilesDir()), "malhaedwo.db")), "legacy private v4 preserved");
        bytesEqual(legacy, read(new File(snapshot(context.getExternalFilesDir(null)), "malhaedwo.db")), "legacy external v4 preserved");
        bytesEqual(legacyManifest, read(new File(snapshot(context.getFilesDir()), BackupBeforeMigration.MANIFEST_NAME)), "legacy manifest untouched");
        byte[] releaseManifest = read(new File(releaseSnapshot(context.getFilesDir()), BackupBeforeMigration.MANIFEST_NAME));
        bytesEqual(releaseManifest, read(new File(releaseSnapshot(context.getExternalFilesDir(null)), BackupBeforeMigration.MANIFEST_NAME)), "release manifests match");
        eq("4", properties(new File(releaseSnapshot(context.getFilesDir()), BackupBeforeMigration.MANIFEST_NAME)).getProperty("fileCount"), "all four DB files included");
        no(new File(releaseSnapshot(context.getExternalFilesDir(null)), "private-setting.txt").exists(), "unrelated settings excluded");
    }

    private static void releaseRepeatedOpenIsImmutable() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        File database = context.getDatabasePath("malhaedwo.db");
        write(database, bytes("first-current"));
        write(new File(database.getPath() + "-journal"), bytes("first-journal"));
        resetDatabaseSingleton();
        CaptureDatabase.get(context);
        byte[] manifest = read(new File(releaseSnapshot(context.getFilesDir()), BackupBeforeMigration.MANIFEST_NAME));
        write(database, bytes("later-live-db"));
        write(new File(database.getPath() + "-journal"), bytes("later-journal"));
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();
        CaptureDatabase.get(context);
        eq(1, SQLiteOpenHelper.constructionCount(), "repeat open allowed after validation");
        for (File base : new File[]{context.getFilesDir(), context.getExternalFilesDir(null)}) {
            bytesEqual(bytes("first-current"), read(new File(releaseSnapshot(base), "malhaedwo.db")), "release main immutable");
            bytesEqual(bytes("first-journal"), read(new File(releaseSnapshot(base), "malhaedwo.db-journal")), "release journal immutable");
            bytesEqual(manifest, read(new File(releaseSnapshot(base), BackupBeforeMigration.MANIFEST_NAME)), "release manifest immutable");
        }
        bytesEqual(bytes("later-live-db"), read(database), "repeat does not overwrite live database");
    }

    private static void releaseExternalFailureAndRetry() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        File database = context.getDatabasePath("malhaedwo.db");
        write(database, bytes("legacy"));
        BackupBeforeMigration.ensure(context);
        write(database, bytes("current-v5-before-open"));
        File externalFinal = releaseSnapshot(context.getExternalFilesDir(null));
        write(externalFinal, bytes("blocking-file-not-directory"));
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();
        expectBackupFailure(() -> CaptureDatabase.get(context), "release external snapshot blocked");
        eq(0, SQLiteOpenHelper.constructionCount(), "release mirror failure prevents helper");
        bytesEqual(bytes("current-v5-before-open"), read(database), "failed release leaves live DB untouched");
        bytesEqual(bytes("legacy"), read(new File(snapshot(context.getExternalFilesDir(null)), "malhaedwo.db")), "failed release leaves legacy untouched");
        bytesEqual(bytes("current-v5-before-open"), read(new File(releaseSnapshot(context.getFilesDir()), "malhaedwo.db")), "release private completed before failure");
        yes(externalFinal.delete(), "remove test external blocker");
        File staleTemp = new File(externalFinal.getParentFile(), "." + BackupBeforeMigration.RELEASE_SNAPSHOT_NAME + ".tmp");
        yes(staleTemp.mkdirs(), "simulate incomplete release mirror");
        write(new File(staleTemp, "partial"), bytes("incomplete"));
        yes(CaptureDatabase.get(context) != null, "release retry recovers");
        eq(1, SQLiteOpenHelper.constructionCount(), "helper only after recovery");
        no(staleTemp.exists(), "release stale temp removed");
        bytesEqual(bytes("current-v5-before-open"), read(new File(externalFinal, "malhaedwo.db")), "recovered mirror matches private release");
    }

    private static void releaseInvalidSourceFailsClosed() throws Exception {
        TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
        File database = context.getDatabasePath("malhaedwo.db");
        write(database, bytes("legacy"));
        BackupBeforeMigration.ensure(context);
        write(database, bytes("current-v5"));
        File journal = new File(database.getPath() + "-journal");
        yes(journal.mkdir(), "simulate non-file source sidecar");
        resetDatabaseSingleton();
        SQLiteOpenHelper.resetConstructionCount();
        expectBackupFailure(() -> CaptureDatabase.get(context), "release invalid source");
        eq(0, SQLiteOpenHelper.constructionCount(), "invalid source prevents helper");
        bytesEqual(bytes("current-v5"), read(database), "source rejection leaves main intact");
        no(releaseSnapshot(context.getFilesDir()).exists(), "invalid source does not publish snapshot");
        yes(journal.delete(), "remove only synthetic invalid journal directory");
        write(journal, bytes("valid-current-journal"));
        yes(CaptureDatabase.get(context) != null, "valid source retry recovers");
        eq(1, SQLiteOpenHelper.constructionCount(), "helper after valid copy");
        bytesEqual(bytes("valid-current-journal"), read(new File(releaseSnapshot(context.getExternalFilesDir(null)), "malhaedwo.db-journal")), "journal included after recovery");
    }

    private static void releaseCorruptionFailsClosed() throws Exception {
        for (boolean corruptExternal : new boolean[]{false, true}) {
            TestEnvironment.ContextImpl context = new TestEnvironment.ContextImpl();
            File database = context.getDatabasePath("malhaedwo.db");
            write(database, bytes("good"));
            resetDatabaseSingleton();
            CaptureDatabase.get(context);
            File base = corruptExternal ? context.getExternalFilesDir(null) : context.getFilesDir();
            File stored = new File(releaseSnapshot(base), "malhaedwo.db");
            write(stored, bytes("evil")); // Same length: validation must check the hash, not only size.
            resetDatabaseSingleton();
            SQLiteOpenHelper.resetConstructionCount();
            expectBackupFailure(() -> CaptureDatabase.get(context), "release stored hash mismatch");
            eq(0, SQLiteOpenHelper.constructionCount(), "hash failure prevents helper");
            bytesEqual(bytes("good"), read(database), "hash failure leaves live DB intact");
            bytesEqual(bytes("good"), read(new File(snapshot(context.getFilesDir()), "malhaedwo.db")), "hash failure leaves legacy intact");
            bytesEqual(bytes("evil"), read(stored), "corrupt finalized snapshot is not silently overwritten");
            write(stored, bytes("good")); // Test-only restoration of exactly the original bytes.
            yes(CaptureDatabase.get(context) != null, "verified restoration permits retry");
            eq(1, SQLiteOpenHelper.constructionCount(), "helper constructed after hash recovery");
        }
    }

    private static File releaseSnapshot(File base) {
        return new File(new File(base, BackupBeforeMigration.BACKUP_ROOT), BackupBeforeMigration.RELEASE_SNAPSHOT_NAME);
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
