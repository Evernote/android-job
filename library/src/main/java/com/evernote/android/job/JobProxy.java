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
import android.content.Context;
import android.os.Looper;
import android.support.annotation.NonNull;

import com.evernote.android.job.util.JobApi;
import com.evernote.android.job.util.JobCat;
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

    void cancel(int jobId);

    boolean isPlatformJobScheduled(JobRequest request);

    /*package*/ final class Common {

        // see Google Guava: https://github.com/google/guava/blob/master/guava/src/com/google/common/math/LongMath.java
        private static long checkedAdd(long a, long b) {
            long result = a + b;
            return checkNoOverflow(result, (a ^ b) < 0 | (a ^ result) >= 0);
        }

        private static long checkNoOverflow(long result, boolean condition) {
            return condition ? result : Long.MAX_VALUE;
        }

        public static long getStartMs(JobRequest request) {
            return checkedAdd(request.getStartMs(), request.getBackoffOffset());
        }

        public static long getEndMs(JobRequest request) {
            return checkedAdd(request.getEndMs(), request.getBackoffOffset());
        }

        public static long getAverageDelayMs(JobRequest request) {
            return checkedAdd(getStartMs(request), (getEndMs(request) - getStartMs(request)) / 2);
        }

        private final Context mContext;
        private final int mJobId;
        private final CatLog mCat;

        private final JobManager mJobManager;

        public Common(@NonNull Service service, int jobId) {
            this(service, service.getClass().getSimpleName(), jobId);
        }

        /*package*/ Common(@NonNull Context context, String loggingTag, int jobId) {
            mContext = context;
            mJobId = jobId;
            mCat = new JobCat(loggingTag);

            mJobManager = JobManager.create(context);
        }

        public JobRequest getPendingRequest(boolean cleanUpOrphanedJob) {
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

            } else if (request != null && request.isTransient()) {
                mCat.d("Request %d is transient, %s", mJobId, request);
                // not necessary to clean up, the JobManager will do this for transient jobs
                return null;

            } else if (request == null) {
                mCat.d("Request for ID %d was null", mJobId);
                cleanUpOrphanedJob(cleanUpOrphanedJob);
                return null;
            }

            return request;
        }

        @NonNull
        public Job.Result executeJobRequest(@NonNull JobRequest request) {
            long waited = System.currentTimeMillis() - request.getScheduledAt();
            String timeWindow;
            if (request.isPeriodic()) {
                timeWindow = "interval " + JobUtil.timeToString(request.getIntervalMs());
            } else if (JobApi.V_14.equals(request.getJobApi())) {
                timeWindow = "delay " + JobUtil.timeToString(getAverageDelayMs(request));
            } else {
                timeWindow = String.format(Locale.US, "start %s, end %s", JobUtil.timeToString(getStartMs(request)),
                        JobUtil.timeToString(getEndMs(request)));
            }

            if (Looper.myLooper() == Looper.getMainLooper()) {
                mCat.w("Running JobRequest on a main thread, this could cause stutter or ANR in your app.");
            }

            mCat.d("Run job, %s, waited %s, %s", request, JobUtil.timeToString(waited), timeWindow);
            JobExecutor jobExecutor = mJobManager.getJobExecutor();
            Job job = null;

            try {
                // create job first before setting it transient, avoids a race condition while rescheduling jobs
                job = mJobManager.getJobCreatorHolder().createJob(request.getTag());

                if (!request.isPeriodic()) {
                    request.setTransient(true);
                }

                Future<Job.Result> future = jobExecutor.execute(mContext, request, job);
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
                }
            }
        }

        private void cleanUpOrphanedJob(boolean cleanUp) {
            if (cleanUp) {
                cleanUpOrphanedJob(mContext, mJobId);
            }
        }

        public static void cleanUpOrphanedJob(Context context, int jobId) {
            /*
             * That's necessary if the database was deleted and jobs (especially the JobScheduler) are still around.
             * Then if a new job is being scheduled, it's possible that the new job has the ID of the old one. Here
             * we make sure, that no job is left in the system.
             */
            for (JobApi jobApi : JobApi.values()) {
                if (jobApi.isSupported(context)) {
                    jobApi.getCachedProxy(context).cancel(jobId);
                }
            }
        }
    }
}
