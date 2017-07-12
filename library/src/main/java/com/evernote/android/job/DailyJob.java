package com.evernote.android.job;

import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;
import android.support.annotation.WorkerThread;

import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import net.vrallev.android.cat.CatLog;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

/**
 * Helper class for jobs which should run once a day. Your daily job needs to extend {@link DailyJob}
 * and you need to use the {@link #schedule(JobRequest.Builder, long, long)} to schedule the job.
 *
 * @author rwondratschek
 */
@SuppressWarnings("unused")
public abstract class DailyJob extends Job {

    private static final CatLog CAT = new JobCat("DailyJob");

    private static final String EXTRA_START_MS = "EXTRA_START_MS";
    private static final String EXTRA_END_MS = "EXTRA_END_MS";

    private static final long DAY = TimeUnit.DAYS.toMillis(1);

    /**
     * Schedule your daily job. You need to pass in the builder. That gives you the option to override
     * requirements or to pass extras to the job. However, it's not allowed to make the job exact,
     * periodic or transient. Since rescheduling a daily job isn't useful, enforcing requirements
     * isn't support either.
     * <br>
     * <br>
     * Daily jobs should use an unique tag and their classes shouldn't be reused for other jobs.
     * <br>
     * <br>
     * The start and end parameter must be less than one day. It's allowed to have a smaller end value
     * than start value. That's helpful for allowing the job to run in evening or morning.
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
     */
    public static void schedule(@NonNull JobRequest.Builder baseBuilder, long startMs, long endMs) {
        schedule(baseBuilder, true, startMs, endMs);
    }

    private static void schedule(@NonNull JobRequest.Builder builder, boolean newJob, long startMs, long endMs) {
        if (startMs >= DAY || endMs >= DAY || startMs < 0 || endMs < 0) {
            throw new IllegalArgumentException("startMs or endMs should be smaller than one day (in milliseconds)");
        }

        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);
        int second = calendar.get(Calendar.SECOND);

        // current time + startDelay = 00:00
        long startDelay = TimeUnit.SECONDS.toMillis(60 - second)
                + TimeUnit.MINUTES.toMillis(60 - minute)
                + TimeUnit.HOURS.toMillis((24 - hour) % 24)
                - TimeUnit.HOURS.toMillis(1); // subtract because we're adding minutes and seconds

        startDelay = (startDelay + startMs) % TimeUnit.DAYS.toMillis(1);

        if (startMs > endMs) {
            // e.g. when job should run between 10pm and 2am
            endMs += TimeUnit.DAYS.toMillis(1);
        }
        long endDelay = startDelay + (endMs - startMs);

        JobRequest request = builder
                .setExecutionWindow(startDelay, endDelay)
                .setUpdateCurrent(newJob)
                .build();

        if (newJob && (request.isExact() || request.isPeriodic() || request.isTransient())) {
            throw new IllegalArgumentException("Daily jobs are not allowed to be exact, periodic or transient");
        }
        if (newJob && (request.requirementsEnforced())) {
            throw new IllegalArgumentException("Daily jobs are not allowed to enforce requirements");
        }

        request.schedule();
    }

    @NonNull
    @Override
    protected final Result onRunJob(Params params) {
        PersistableBundleCompat extras = params.getExtras();
        if (!extras.containsKey(EXTRA_START_MS) || !extras.containsKey(EXTRA_END_MS)) {
            CAT.e("Daily job doesn't contain start and end time");
            return Result.FAILURE;
        }

        DailyJobResult result = null;

        try {
            result = onRunDailyJob(params);
        } finally {
            if (result == null) {
                // shouldn't happen if the job follows the contract
                result = DailyJobResult.SUCCESS;
                CAT.e("Daily job result was null");
            }

            JobRequest request = params.getRequest();
            if (result == DailyJobResult.SUCCESS) {
                CAT.i("Rescheduling daily job %s", request);

                // don't update current, it would cancel this currently running job
                schedule(request.createBuilder(), false,
                        extras.getLong(EXTRA_START_MS, 0), extras.getLong(EXTRA_END_MS, 0L));

            } else {
                CAT.i("Cancel daily job %s", request);
            }
        }

        return Result.SUCCESS;
    }

    /**
     * This method is invoked from a background thread. You should run your desired task here.
     * This method is thread safe. Each time a job starts executing a new instance of your {@link Job}
     * is instantiated. You can identify your {@link Job} with the passed {@code params}.
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
    protected abstract DailyJobResult onRunDailyJob(Params params);

    public enum DailyJobResult {
        /**
         * Indicates that the job was successful and should run the next day again.
         */
        SUCCESS,
        /**
         * Indicates that the job finished and should NOT run again.
         */
        CANCEL
    }
}
