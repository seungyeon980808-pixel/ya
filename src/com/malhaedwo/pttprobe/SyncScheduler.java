package com.malhaedwo.pttprobe;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;

public final class SyncScheduler {
    private static final int PERIODIC_JOB_ID = 491743;
    private static final int IMMEDIATE_JOB_ID = 491744;
    private static final long PERIOD_MS = 15L * 60_000L;

    private SyncScheduler() {}

    public static void ensurePeriodic(Context context) {
        if (!GoogleOAuth.isConnected(context)) return;
        Context app = context.getApplicationContext();
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        if (scheduler == null || scheduler.getPendingJob(PERIODIC_JOB_ID) != null) return;
        JobInfo info = new JobInfo.Builder(PERIODIC_JOB_ID, new ComponentName(app, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .setPeriodic(PERIOD_MS)
                .build();
        scheduler.schedule(info);
    }

    public static void request(Context context) {
        if (!GoogleOAuth.isConnected(context)) return;
        Context app = context.getApplicationContext();
        ensurePeriodic(app);
        JobScheduler scheduler = app.getSystemService(JobScheduler.class);
        if (scheduler == null) return;
        JobInfo info = new JobInfo.Builder(IMMEDIATE_JOB_ID, new ComponentName(app, SyncJobService.class))
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setMinimumLatency(500L)
                .setOverrideDeadline(20_000L)
                .build();
        scheduler.schedule(info);
    }

    public static void cancel(Context context) {
        JobScheduler scheduler = context.getSystemService(JobScheduler.class);
        if (scheduler != null) {
            scheduler.cancel(PERIODIC_JOB_ID);
            scheduler.cancel(IMMEDIATE_JOB_ID);
        }
    }
}
