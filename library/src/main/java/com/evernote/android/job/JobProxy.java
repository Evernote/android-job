/*
 * Copyright 2007-present Evernote Corporation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.android.job;

import android.app.Service;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.util.JobUtil;

import net.vrallev.android.cat.CatLog;

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

/**
 * A proxy for each {@link JobApi}.
 *
 * @author rwondratschek
 */
public interface JobProxy {

    void plantOneOff(JobRequest request);

    void plantPeriodic(JobRequest request);

    void plantPeriodicFlexSupport(JobRequest request);

    void cancel(int jobId);

    boolean isPlatformJobScheduled(JobRequest request);

    @SuppressWarnings("UnusedReturnValue")
    /*package*/ final class Common {

        private static final Object COMMON_MONITOR = new Object();

        // see Google Guava: https://github.com/google/guava/blob/master/guava/src/com/google/common/math/LongMath.java
        private static long checkedAdd(long a, long b) {
            long result = a + b;
            return checkNoOverflow(result, (a ^ b) < 0 | (a ^ result) >= 0);
        }

        private static long checkNoOverflow(long result, boolean condition) {
            return condition ? result : Long.MAX_VALUE;
        }

        public static long getStartMs(JobRequest request) {
            if (request.getFailureCount() > 0) {
                return request.getBackoffOffset();
            } else {
                return request.getStartMs();
            }
        }

        public static long getEndMs(JobRequest request) {
            if (request.getFailureCount() > 0) {
                return request.getBackoffOffset();
            } else {
                return request.getEndMs();
            }
        }

        public static long getAverageDelayMs(JobRequest request) {
            return checkedAdd(getStartMs(request), (getEndMs(request) - getStartMs(request)) / 2);
        }

        public static long getStartMsSupportFlex(JobRequest request) {
            return Math.max(1, request.getIntervalMs() - request.getFlexMs());
        }

        public static long getEndMsSupportFlex(JobRequest request) {
            return request.getIntervalMs();
        }

        public static long getAverageDelayMsSupportFlex(JobRequest request) {
            return checkedAdd(getStartMsSupportFlex(request), (getEndMsSupportFlex(request) - getStartMsSupportFlex(request)) / 2);
        }

        public static int getRescheduleCount(JobRequest request) {
            return request.getFailureCount();
        }

        private final Context mContext;
        private final int mJobId;
        private final CatLog mCat;

        private final JobManager mJobManager;

        public Common(@NonNull Service service, CatLog cat, int jobId) {
            this((Context) service, cat, jobId);
        }

        /*package*/ Common(@NonNull Context context, CatLog cat, int jobId) {
            mContext = context;
            mJobId = jobId;
            mCat = cat;

            JobManager manager;
            try {
                manager = JobManager.create(context);
            } catch (JobManagerCreateException e) {
                mCat.e(e);
                manager = null;
            }
            mJobManager = manager;
        }

        public JobRequest getPendingRequest(@SuppressWarnings("SameParameterValue") boolean cleanUpOrphanedJob, boolean markStarting) {
            synchronized (COMMON_MONITOR) {
                if (mJobManager == null) {
                    return null;
                }

                // order is important for logging purposes
                JobRequest request = mJobManager.getJobRequest(mJobId, true);
                Job job = mJobManager.getJob(mJobId);
                boolean periodic = request != null && request.isPeriodic();

                if (job != null && !job.isFinished()) {
                    // that's probably a platform bug http://stackoverflow.com/questions/33235754/jobscheduler-posting-jobs-twice-not-expected
                    mCat.d("Job %d is already running, %s", mJobId, request);
                    // not necessary to clean up, the running instance will do that
                    return null;

                } else if (job != null && !periodic) {
                    mCat.d("Job %d already finished, %s", mJobId, request);
                    cleanUpOrphanedJob(cleanUpOrphanedJob);
                    return null;

                } else if (job != null && System.currentTimeMillis() - job.getFinishedTimeStamp() < 2_000) {
                    // that's probably a platform bug http://stackoverflow.com/questions/33235754/jobscheduler-posting-jobs-twice-not-expected
                    mCat.d("Job %d is periodic and just finished, %s", mJobId, request);
                    // don't clean up, periodic job
                    return null;

                } else if (request != null && request.isStarted()) {
                    mCat.d("Request %d already started, %s", mJobId, request);
                    // not necessary to clean up, the JobManager will do this for started jobs
                    return null;

                } else if (request != null && mJobManager.getJobExecutor().isRequestStarting(request)) {
                    mCat.d("Request %d is in the queue to start, %s", mJobId, request);
                    return null;

                } else if (request == null) {
                    mCat.d("Request for ID %d was null", mJobId);
                    cleanUpOrphanedJob(cleanUpOrphanedJob);
                    return null;
                }

                if (markStarting) {
                    markStarting(request);
                }

                return request;
            }
        }

        public void markStarting(@NonNull JobRequest request) {
            mJobManager.getJobExecutor().markJobRequestStarting(request);
        }

        @NonNull
        public Job.Result executeJobRequest(@NonNull JobRequest request, @Nullable Bundle transientExtras) {
            long waited = System.currentTimeMillis() - request.getScheduledAt();
            String timeWindow;
            if (request.isPeriodic()) {
                timeWindow = String.format(Locale.US, "interval %s, flex %s", JobUtil.timeToString(request.getIntervalMs()),
                        JobUtil.timeToString(request.getFlexMs()));
            } else if (request.getJobApi().supportsExecutionWindow()) {
                timeWindow = String.format(Locale.US, "start %s, end %s", JobUtil.timeToString(getStartMs(request)),
                        JobUtil.timeToString(getEndMs(request)));
            } else {
                timeWindow = "delay " + JobUtil.timeToString(getAverageDelayMs(request));
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                mCat.w("Running JobRequest on a main thread, this could cause stutter or ANR in your app.");
            }

            mCat.d("Run job, %s, waited %s, %s", request, JobUtil.timeToString(waited), timeWindow);
            JobExecutor jobExecutor = mJobManager.getJobExecutor();
            Job job = null;

            try {
                // create job first before setting it started, avoids a race condition while rescheduling jobs
                job = mJobManager.getJobCreatorHolder().createJob(request.getTag());

                if (!request.isPeriodic()) {
                    request.setStarted(true);
                }

                if (transientExtras == null) {
                    transientExtras = Bundle.EMPTY;
                }

                Future<Job.Result> future = jobExecutor.execute(mContext, request, job, transientExtras);
                if (future == null) {
                    return Job.Result.FAILURE;
                }

                // wait until done
                Job.Result result = future.get();
                mCat.d("Finished job, %s %s", request, result);
                return result;

            } catch (InterruptedException | ExecutionException e) {
                mCat.e(e);

                if (job != null) {
                    job.cancel();
                    mCat.e("Canceled %s", request);
                }

                return Job.Result.FAILURE;

            } finally {
                if (!request.isPeriodic()) {
                    mJobManager.getJobStorage().remove(request);

                } else if (request.isFlexSupport() && (job == null || !job.isDeleted())) {
                    mJobManager.getJobStorage().remove(request); // remove, we store the new job in JobManager.schedule()
                    request.reschedule(false, false);
                }
            }
        }

        private void cleanUpOrphanedJob(boolean cleanUp) {
            if (cleanUp) {
                cleanUpOrphanedJob(mContext, mJobId);
            }
        }

        /*package*/ static void cleanUpOrphanedJob(Context context, int jobId) {
            /*
             * That's necessary if the database was deleted and jobs (especially the JobScheduler) are still around.
             * Then if a new job is being scheduled, it's possible that the new job has the ID of the old one. Here
             * we make sure, that no job is left in the system.
             */
            for (JobApi jobApi : JobApi.values()) {
                if (jobApi.isSupported(context)) {
                    try {
                        jobApi.getProxy(context).cancel(jobId);
                    } catch (Exception ignored) {
                        // GCM API could crash if it's disabled, ignore crashes at this point and continue
                    }
                }
            }
        }

        public static ComponentName startWakefulService(Context context, Intent intent) {
            return WakeLockUtil.startWakefulService(context, intent);
        }

        public static boolean completeWakefulIntent(Intent intent) {
            return WakeLockUtil.completeWakefulIntent(intent);
        }
    }
}
