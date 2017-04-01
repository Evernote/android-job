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

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.LruCache;
import android.util.SparseArray;

import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * @author rwondratschek
 */
/*package*/ class JobExecutor {

    private static final CatLog CAT = new JobCat("JobExecutor");
    private static final long WAKE_LOCK_TIMEOUT = TimeUnit.MINUTES.toMillis(3);

    private final ExecutorService mExecutorService;

    private final SparseArray<Job> mJobs; // only cached in memory, that's fine
    private final LruCache<Integer, Job> mFinishedJobsCache;

    public JobExecutor() {
        mExecutorService = Executors.newCachedThreadPool(JobProxy.Common.COMMON_THREAD_FACTORY);
        mJobs = new SparseArray<>();
        mFinishedJobsCache = new LruCache<>(20);
    }

    public synchronized Future<Job.Result> execute(@NonNull Context context, @NonNull JobRequest request, @Nullable Job job) {
        if (job == null) {
            CAT.w("JobCreator returned null for tag %s", request.getTag());
            return null;
        }
        if (job.isFinished()) {
            throw new IllegalStateException(String.format(Locale.ENGLISH, "Job for tag %s was already run, a creator should always create a new Job instance", request.getTag()));
        }

        job.setContext(context).setRequest(request);

        CAT.i("Executing %s, context %s", request, context.getClass().getSimpleName());

        mJobs.put(request.getJobId(), job);
        return mExecutorService.submit(new JobCallable(job));
    }

    public synchronized Job getJob(int jobId) {
        Job job = mJobs.get(jobId);
        return job != null ? job : mFinishedJobsCache.get(jobId);
    }

    public synchronized Set<Job> getAllJobs() {
        return getAllJobsForTag(null);
    }

    public synchronized Set<Job> getAllJobsForTag(String tag) {
        Set<Job> result = new HashSet<>();
        for (int i = 0; i < mJobs.size(); i++) {
            Job job = mJobs.valueAt(i);
            if (tag == null || tag.equals(job.getParams().getTag())) {
                result.add(job);
            }
        }

        Map<Integer, Job> snapshot = mFinishedJobsCache.snapshot();
        for (Job job : snapshot.values()) {
            if (tag == null || tag.equals(job.getParams().getTag())) {
                result.add(job);
            }
        }

        return result;
    }

    private synchronized void markJobAsFinished(Job job) {
        int id = job.getParams().getId();
        mJobs.remove(id);
        mFinishedJobsCache.put(id, job);
    }

    private final class JobCallable implements Callable<Job.Result> {

        private final Job mJob;
        private final PowerManager.WakeLock mWakeLock;

        private JobCallable(Job job) {
            mJob = job;

            Context context = mJob.getContext();
            mWakeLock = WakeLockUtil.acquireWakeLock(context, "JobExecutor", WAKE_LOCK_TIMEOUT);
        }

        @Override
        public Job.Result call() throws Exception {
            try {
                // just in case something was blocking and the wake lock is no longer acquired
                WakeLockUtil.acquireWakeLock(mJob.getContext(), mWakeLock, WAKE_LOCK_TIMEOUT);
                return runJob();

            } finally {
                markJobAsFinished(mJob);

                if (mWakeLock == null || !mWakeLock.isHeld()) {
                    CAT.w("Wake lock was not held after job %s was done. The job took too long to complete. This could have unintended side effects on your app.", mJob);
                }
                WakeLockUtil.releaseWakeLock(mWakeLock);
            }
        }

        private Job.Result runJob() {
            Job.Result result;
            try {
                result = mJob.runJob();
                CAT.i("Finished %s", mJob);

                handleResult(result);

            } catch (Throwable t) {
                CAT.e(t, "Crashed %s", mJob);
                result = mJob.getResult(); // probably the default value
            }

            return result;
        }

        private void handleResult(Job.Result result) {
            JobRequest request = mJob.getParams().getRequest();
            if (!request.isPeriodic() && Job.Result.RESCHEDULE.equals(result)) {
                int newJobId = request.reschedule(true, true);
                mJob.onReschedule(newJobId);
            } else if (request.isPeriodic() && !Job.Result.SUCCESS.equals(result)) {
                request.incNumFailures();
            }
        }
    }
}
