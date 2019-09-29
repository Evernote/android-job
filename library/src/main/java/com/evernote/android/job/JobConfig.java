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

import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.evernote.android.job.util.Clock;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobLogger;
import com.evernote.android.job.util.JobPreconditions;

import java.util.EnumMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A global configuration for the job library.
 * <br>
 * <br>
 * See {@link JobCat} for settings to enable/disable logging.
 *
 * @author rwondratschek
 */
@SuppressWarnings({"WeakerAccess", "unused", "SameParameterValue"})
public final class JobConfig {

    private JobConfig() {
        throw new UnsupportedOperationException();
    }

    private static final long DEFAULT_JOB_RESCHEDULE_PAUSE = 3_000L;

    private static final EnumMap<JobApi, Boolean> ENABLED_APIS;
    private static final JobCat CAT = new JobCat("JobConfig");

    private static final ExecutorService DEFAULT_EXECUTOR_SERVICE = Executors.newCachedThreadPool(new ThreadFactory() {

        private final AtomicInteger mThreadNumber = new AtomicInteger();

        @Override
        public Thread newThread(@NonNull Runnable r) {
            Thread thread = new Thread(r, "AndroidJob-" + mThreadNumber.incrementAndGet());
            if (thread.isDaemon()) {
                thread.setDaemon(false);
            }
            if (thread.getPriority() != Thread.NORM_PRIORITY) {
                thread.setPriority(Thread.NORM_PRIORITY);
            }
            return thread;
        }
    });

    private static volatile boolean allowSmallerIntervals;
    private static volatile boolean forceAllowApi14 = false;

    private static volatile long jobReschedulePause = DEFAULT_JOB_RESCHEDULE_PAUSE;
    private static volatile boolean skipJobReschedule = false;

    private static volatile int jobIdOffset = 0;

    private static volatile boolean forceRtc = false;

    private static volatile Clock clock = Clock.DEFAULT;
    private static volatile ExecutorService executorService = DEFAULT_EXECUTOR_SERVICE;
    private static volatile boolean closeDatabase = false;

    static {
        ENABLED_APIS = new EnumMap<>(JobApi.class);
        for (JobApi api : JobApi.values()) {
            ENABLED_APIS.put(api, Boolean.TRUE);
        }
    }

    /**
     * @return Whether the given API is enabled. By default all APIs are enabled, although the current
     * device may not support it.
     */
    public static boolean isApiEnabled(@NonNull JobApi api) {
        return ENABLED_APIS.get(api);
    }

    /**
     * <b>WARNING:</b> Please use this method carefully. It's only meant to be used for testing purposes
     * and could break how the library works.
     * <br>
     * <br>
     * Programmatic switch to enable or disable the given API. This only has an impact for new scheduled jobs.
     *
     * @param api The API which should be enabled or disabled.
     * @param enabled Whether the API should be enabled or disabled.
     */
    public static void setApiEnabled(@NonNull JobApi api, boolean enabled) {
        ENABLED_APIS.put(api, enabled);
        CAT.w("setApiEnabled - %s, %b", api, enabled);
    }

    /**
     * <b>WARNING:</b> You shouldn't call this method. It only exists for testing and debugging
     * purposes. The {@link JobManager} automatically decides which API suits best for a {@link Job}.
     *
     * @param api The {@link JobApi} which will be used for future scheduled JobRequests.
     */
    public static void forceApi(@NonNull JobApi api) {
        for (JobApi jobApi : JobApi.values()) {
            ENABLED_APIS.put(jobApi, jobApi == api);
        }
        CAT.w("forceApi - %s", api);
    }

    /**
     * Checks whether a smaller interval and flex are allowed for periodic jobs. That's helpful
     * for testing purposes.
     *
     * @return Whether a smaller interval and flex than the minimum values are allowed for periodic jobs
     * are allowed. The default value is {@code false}.
     */
    public static boolean isAllowSmallerIntervalsForMarshmallow() {
        return allowSmallerIntervals && Build.VERSION.SDK_INT < Build.VERSION_CODES.N;
    }

    /**
     * Option to override the minimum period and minimum flex for periodic jobs. This is useful for testing
     * purposes. This method only works for Android M and earlier. Later versions throw an exception.
     *
     * @param allowSmallerIntervals Whether a smaller interval and flex than the minimum values are allowed
     *                              for periodic jobs are allowed. The default value is {@code false}.
     */
    public static void setAllowSmallerIntervalsForMarshmallow(boolean allowSmallerIntervals) {
        if (allowSmallerIntervals && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            throw new IllegalStateException("This method is only allowed to call on Android M or earlier");
        }
        JobConfig.allowSmallerIntervals = allowSmallerIntervals;
    }

    /**
     * On some devices for some reason all broadcast receiver and services are disabled. This library
     * cannot work properly in this case. This switch allows to use the AlarmManager as fallback even
     * in such a weird state.
     *
     * <br>
     * <br>
     *
     * If the value is {@code true}, then this suppresses the {@link JobManagerCreateException} during
     * the creation of the job manager.
     *
     * @param forceAllowApi14 Whether API 14 should be used as fallback in all scenarios. The default
     *                        value is {@code false}.
     */
    public static void setForceAllowApi14(boolean forceAllowApi14) {
        JobConfig.forceAllowApi14 = forceAllowApi14;
    }

    /**
     * @return Whether API 14 should be used as fallback in all scenarios. The default value is {@code false}.
     */
    public static boolean isForceAllowApi14() {
        return forceAllowApi14;
    }

    /**
     * Add a global logger for the job library, which will be notified about each log statement.
     *
     * @param logger Your desired logger.
     * @return {@code true} if the logger was added. Returns {@code false} if the logger was
     * already added.
     */
    public static synchronized boolean addLogger(@NonNull JobLogger logger) {
        return JobCat.addLogger(logger);
    }

    /**
     * Remove a global logger.
     *
     * @param logger Your desired logger.
     * @see #addLogger(JobLogger)
     */
    public static synchronized void removeLogger(@NonNull JobLogger logger) {
        JobCat.removeLogger(logger);
    }

    /**
     * Global switch to enable or disable printing log messages to Logcat.
     *
     * @param enabled Whether or not to print all log messages. The default value is {@code true}.
     */
    public static void setLogcatEnabled(boolean enabled) {
        JobCat.setLogcatEnabled(enabled);
    }

    /**
     * @return Whether logging is enabled for this library. The default value is {@code true}.
     */
    public static boolean isLogcatEnabled() {
        return JobCat.isLogcatEnabled();
    }

    /**
     * @return The pause of job reschedule service in milliseconds.
     */
    public static long getJobReschedulePause() {
        return jobReschedulePause;
    }

    /**
     * Overrides the default job reschedule pause. The default value is 3 seconds.
     *
     * @param pause The new pause.
     * @param timeUnit The time unit of the pause argument.
     */
    public static void setJobReschedulePause(long pause, @NonNull TimeUnit timeUnit) {
        jobReschedulePause = timeUnit.toMillis(pause);
    }

    /*package*/ static boolean isSkipJobReschedule() {
        return skipJobReschedule;
    }

    /*package*/ static void setSkipJobReschedule(boolean skipJobReschedule) {
        JobConfig.skipJobReschedule = skipJobReschedule;
    }

    /**
     * @return The offset for the very first job ID. The default value is 0 and very first job ID will be 1.
     */
    public static int getJobIdOffset() {
        return jobIdOffset;
    }

    /**
     * Adds an offset to the job IDs. Job IDs are generated and usually start with 1. This offset shifts the
     * very first job ID.
     *
     * @param jobIdOffset The offset for the very first job ID.
     */
    public static void setJobIdOffset(int jobIdOffset) {
        JobPreconditions.checkArgumentNonnegative(jobIdOffset, "offset can't be negative");
        if (jobIdOffset > JobIdsInternal.RESERVED_JOB_ID_RANGE_START - 500) {
            throw new IllegalArgumentException("offset is too close to Integer.MAX_VALUE");
        }

        JobConfig.jobIdOffset = jobIdOffset;
    }

    /**
     * @return Whether the alarm time should use System.currentTimeMillis() (wall clock time in UTC). The
     *                 default value is {@code false} and will use the alarm time in SystemClock.elapsedRealtime()
     *                 (time since boot, including sleep).
     */
    public static boolean isForceRtc() {
        return forceRtc;
    }

    /**
     * @param forceRtc Force using the alarm time in System.currentTimeMillis() (wall clock time in UTC). The
     *                 default value is {@code false} and will use the alarm time in SystemClock.elapsedRealtime()
     *                 (time since boot, including sleep).
     */
    public static void setForceRtc(boolean forceRtc) {
        JobConfig.forceRtc = forceRtc;
    }

    /**
     * @return A helper providing the system time
     */
    public static Clock getClock() {
        return clock;
    }

    @VisibleForTesting
    /*package*/ static void setClock(Clock clock) {
        JobConfig.clock = clock;
    }

    /**
     * @return The executor service for all parallel execution.
     */
    public static ExecutorService getExecutorService() {
        return executorService;
    }

    /**
     * Overrides the executor service for all parallel execution. This could be helpful for Espresso
     * tests.
     *
     * @param executorService The new executor service.
     */
    public static void setExecutorService(@NonNull ExecutorService executorService) {
        JobConfig.executorService = JobPreconditions.checkNotNull(executorService);
    }

    /**
     * @return Whether the internal database is closed after each access. The default value is {@code false}.
     */
    public static boolean isCloseDatabase() {
        return closeDatabase;
    }

    /**
     * Controls whether the internal database should be closed after each access to clean up
     * resources. The default value is {@code false}.
     *
     * @param closeDatabase Whether to close the database after each access.
     */
    public static void setCloseDatabase(boolean closeDatabase) {
        JobConfig.closeDatabase = closeDatabase;
    }

    /**
     * Resets all adjustments in the config.
     */
    public static void reset() {
        for (JobApi api : JobApi.values()) {
            ENABLED_APIS.put(api, Boolean.TRUE);
        }
        allowSmallerIntervals = false;
        forceAllowApi14 = false;
        jobReschedulePause = DEFAULT_JOB_RESCHEDULE_PAUSE;
        skipJobReschedule = false;
        jobIdOffset = 0;
        forceRtc = false;
        clock = Clock.DEFAULT;
        executorService = DEFAULT_EXECUTOR_SERVICE;
        closeDatabase = false;
        JobCat.setLogcatEnabled(true);
        JobCat.clearLogger();
    }
}
