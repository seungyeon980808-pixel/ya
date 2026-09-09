package com.malhaedwo.pttprobe;

import android.app.job.JobParameters;
import android.app.job.JobService;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public final class SyncJobService extends JobService {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private volatile Future<?> runningTask;
    private volatile boolean stopped;

    @Override public boolean onStartJob(JobParameters params) {
        stopped = false;
        Future<?> previous = runningTask;
        if (previous != null && !previous.isDone()) previous.cancel(true);
        runningTask = executor.submit(() -> {
            GoogleSyncEngine.Result result = GoogleSyncEngine.sync(getApplicationContext());
            if (!stopped && !Thread.currentThread().isInterrupted()) jobFinished(params, !result.success);
        });
        return true;
    }

    @Override public boolean onStopJob(JobParameters params) {
        stopped = true;
        Future<?> task = runningTask;
        if (task != null) task.cancel(true);
        runningTask = null;
        return GoogleOAuth.isConnected(this);
    }

    @Override public void onDestroy() {
        Future<?> task = runningTask;
        if (task != null) task.cancel(true);
        runningTask = null;
        executor.shutdownNow();
        super.onDestroy();
    }
}
