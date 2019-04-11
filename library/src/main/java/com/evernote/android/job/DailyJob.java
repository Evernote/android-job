/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job;

import android.os.PowerManager.WakeLock;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;

import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobPreconditions;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.util.Calendar;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for jobs which should run once a day. Your daily job needs to extend {@link DailyJob}
 * and you need to use the {@link #schedule(JobRequest.Builder, long, long)} to schedule the job.
 *
 * @author rwondratschek
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class DailyJob extends Job {

    private static final JobCat CAT = new JobCat("DailyJob");

    @VisibleForTesting
    /*package*/ static final String EXTRA_START_MS = "EXTRA_START_MS";
    @VisibleForTesting
    /*package*/ static final String EXTRA_END_MS = "EXTRA_END_MS";
    @VisibleForTesting
    private static final String EXTRA_ONCE = "EXTRA_ONCE";

    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    /**
     * Schedules your daily job. A builder is required for this method call. Within the builder, you may specify
     * additional requirements and/or extras for the job. However, a daily job may not be exact,
     * periodic or transient. If the requirements are enforced but are not met when the job runs, then the
     * daily job will only be rescheduled for the next day. The back-off criteria is ignored in this case.
     * <br>
     * <br>
     * Daily jobs should use a unique tag and their classes shouldn't be reused for other jobs.
     * <br>
     * <br>
     * The start and end parameter must be less than one day. The end value may be smaller than the start value,
     * allowing you to run the job in the evening or morning.
     * <br>
     * <br>
     * Sample usage:
     * <pre>
     * // schedule between 1am and 6am
     * DailyJob.schedule(builder, TimeUnit.HOURS.toMillis(1), TimeUnit.HOURS.toMillis(6));
     *
     * // schedule between 8pm and 3am
     * JobRequest.Builder builder = new JobRequest.Builder("MyTag").setRequiresCharging(true);
     * DailyJob.schedule(builder, TimeUnit.HOURS.toMillis(20), TimeUnit.HOURS.toMillis(3));
     * </pre>
     *
     * @param baseBuilder The builder of your daily job.
     * @param startMs The time of the day when the job is allowed to start in milliseconds.
     * @param endMs The time of the day when the job is not allowed to start later in milliseconds.
     * @return The unique ID for this job.
     */
    public static int schedule(@NonNull JobRequest.Builder baseBuilder, long startMs, long endMs) {
        return schedule(baseBuilder, true, startMs, endMs, false);
    }

    /**
     * Helper method to schedule a daily job on a background thread. This is helpful to avoid IO operations
     * on the main thread. For more information about scheduling daily jobs see {@link #schedule(JobRequest.Builder, long, long)}.
     *
     * <br>
     * <br>
     *
     * In case of a failure an error is logged, but the application doesn't crash.
     */
    public static void scheduleAsync(@NonNull JobRequest.Builder baseBuilder, long startMs, long endMs) {
        scheduleAsync(baseBuilder, startMs, endMs, JobRequest.DEFAULT_JOB_SCHEDULED_CALLBACK);
    }

    /**
     * Helper method to schedule a daily job on a background thread. This is helpful to avoid IO operations
     * on the main thread. The callback notifies you about the job ID or a possible failure. For more
     * information about scheduling daily jobs see {@link #schedule(JobRequest.Builder, long, long)}.
     *
     * @param callback The callback which is invoked after the request has been scheduled.
     */
    public static void scheduleAsync(@NonNull final JobRequest.Builder baseBuilder, final long startMs, final long endMs,
                                     @NonNull final JobRequest.JobScheduledCallback callback) {
        JobPreconditions.checkNotNull(callback);
        JobConfig.getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    int jobId = schedule(baseBuilder, startMs, endMs);
                    callback.onJobScheduled(jobId, baseBuilder.mTag, null);
                } catch (Exception e) {
                    callback.onJobScheduled(JobRequest.JobScheduledCallback.JOB_ID_ERROR, baseBuilder.mTag, e);
                }
            }
        });
    }

    /**
     * Schedules the daily job only once and runs it immediately. This is helpful if you want to reuse your job
     * and want to trigger the execution immediately. It's possible to schedule a daily job normally with
     * {@link #schedule(JobRequest.Builder, long, long)} and this method at the same time to trigger the
     * execution immediately.
     *
     * @param baseBuilder The builder of your daily job.
     * @return The unique ID for this job.
     */
    public static int startNowOnce(@NonNull JobRequest.Builder baseBuilder) {
        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putBoolean(EXTRA_ONCE, true);

        return baseBuilder
                .startNow()
                .addExtras(extras)
                .build()
                .schedule();
    }

    private static int schedule(@NonNull JobRequest.Builder builder, boolean newJob, long startMs, long endMs, boolean isReschedule) {
        if (startMs >= DAY || endMs >= DAY || startMs < 0 || endMs < 0) {
            throw new IllegalArgumentException("startMs or endMs should be less than one day (in milliseconds)");
        }

        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(JobConfig.getClock().currentTimeMillis());

        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        // current time + startDelay = 00:00
        long startDelay = TimeUnit.SECONDS.toMillis(60 - second)
                + TimeUnit.MINUTES.toMillis(60 - minute)
                + TimeUnit.HOURS.toMillis((24 - hour) % 24)
                - TimeUnit.HOURS.toMillis(1)  // subtract because we're adding minutes
                - TimeUnit.MINUTES.toMillis(1)  // subtract because we're adding seconds
                + TimeUnit.DAYS.toMillis(1); // add one day, otherwise result could be negative, e.g. if startMs is 0 and time is 00:08

        startDelay = (startDelay + startMs) % TimeUnit.DAYS.toMillis(1);

        if (isReschedule && startDelay < TimeUnit.HOURS.toMillis(12)) {
            // it happens that the job runs too early and while rescheduling we schedule the job for the same day again
            startDelay += TimeUnit.DAYS.toMillis(1);
        }

        if (startMs > endMs) {
            // e.g. when job should run between 10pm and 2am
            endMs += TimeUnit.DAYS.toMillis(1);
        }
        long endDelay = startDelay + (endMs - startMs);

        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putLong(EXTRA_START_MS, startMs);
        extras.putLong(EXTRA_END_MS, endMs);

        builder.addExtras(extras);

        if (newJob) {
            // cancel all previous jobs, but not the one scheduled immediately
            JobManager manager = JobManager.instance();
            Set<JobRequest> requests = new HashSet<>(manager.getAllJobRequestsForTag(builder.mTag));
            for (JobRequest request : requests) {
                if (!request.isExact() || request.getStartMs() != JobRequest.START_NOW) {
                    manager.cancel(request.getJobId());
                }
            }
        }

        JobRequest request = builder
                .setExecutionWindow(Math.max(1L, startDelay), Math.max(1L, endDelay))
                .build();

        if (newJob && (request.isExact() || request.isPeriodic() || request.isTransient())) {
            throw new IllegalArgumentException("Daily jobs cannot be exact, periodic or transient");
        }

        return request.schedule();
    }

    @NonNull
    @Override
    protected final Result onRunJob(@NonNull Params params) {
        PersistableBundleCompat extras = params.getExtras();
        boolean runOnce = extras.getBoolean(EXTRA_ONCE, false);

        if (!runOnce && (!extras.containsKey(EXTRA_START_MS) || !extras.containsKey(EXTRA_END_MS))) {
            CAT.e("Daily job doesn't contain start and end time");
            return Result.FAILURE;
        }

        DailyJobResult result = null;

        try {
            if (meetsRequirements(true)) {
                result = onRunDailyJob(params);
            } else {
                result = DailyJobResult.SUCCESS; // reschedule
                CAT.i("Daily job requirements not met, reschedule for the next day");
            }

        } finally {
            if (result == null) {
                // shouldn't happen if the job follows the contract
                result = DailyJobResult.SUCCESS;
                CAT.e("Daily job result was null");
            }

            if (!runOnce) {
                JobRequest request = params.getRequest();
                if (result == DailyJobResult.SUCCESS) {
                    CAT.i("Rescheduling daily job %s", request);

                    // don't update current, it would cancel this currently running job
                    int newJobId = schedule(request.createBuilder(), false,
                            extras.getLong(EXTRA_START_MS, 0) % DAY, extras.getLong(EXTRA_END_MS, 0L) % DAY, true);

                    request = JobManager.instance().getJobRequest(newJobId);
                    if (request != null) {
                        request.updateStats(false, true);
                    }

                } else {
                    CAT.i("Cancel daily job %s", request);
                }
            }
        }

        return Result.SUCCESS;
    }

    /**
     * This method is invoked from a background thread. You should run your desired task here.
     * This method is thread safe. Each time a job starts, a new instance of your {@link Job}
     * is instantiated and executed. You can identify your {@link Job} with the passed {@code params}.
     *
     * <br>
     * <br>
     *
     * You should call {@link #isCanceled()} frequently for long running jobs and stop your
     * task if necessary.
     *
     * <br>
     * <br>
     *
     * A {@link WakeLock} is acquired for 3 minutes for each {@link Job}. If your task
     * needs more time, then you need to create an extra {@link WakeLock}.
     *
     * @param params The parameters for this concrete job.
     * @return The result of this {@link DailyJob}.
     */
    @SuppressWarnings("WeakerAccess")
    @NonNull
    @WorkerThread
    protected abstract DailyJobResult onRunDailyJob(@NonNull Params params);

    public enum DailyJobResult {
        /**
         * Indicates that the job was successful and should run again the next day.
         */
        SUCCESS,
        /**
         * Indicates that the job is finished and should NOT run again.
         */
        CANCEL
    }
}
