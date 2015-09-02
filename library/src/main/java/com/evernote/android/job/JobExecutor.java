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
import android.util.SparseArray;

import net.vrallev.android.cat.Cat;

import java.util.HashSet;
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

    private final ExecutorService mExecutorService;
    private final SparseArray<Job> mJobs; // only cached in memory, that's fine

    public JobExecutor() {
        mExecutorService = Executors.newCachedThreadPool();
        mJobs = new SparseArray<>();
    }

    public synchronized Future<Job.Result> execute(@NonNull Context context, @NonNull JobRequest request, @NonNull JobCreator creator) {
        try {
            Job job = creator.create(request.getJobKey());
            if (job == null) {
                Cat.w("JobCreator returned null for key %s", request.getJobKey());
                return null;
            }
            if (job.isFinished()) {
                throw new IllegalStateException("Job for key %s was already run, a creator should always create a new Job instance");
            }

            job.setContext(context).setRequest(request);

            Cat.i("Executing %s, context %s", request, context.getClass().getSimpleName());

            mJobs.put(request.getJobId(), job);
            return mExecutorService.submit(new JobCallable(job));

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public synchronized Job getJob(int jobId) {
        return mJobs.get(jobId);
    }

    public synchronized Job getJob(String tag) {
        if (tag == null) {
            return null;
        }
        for (int i = 0; i < mJobs.size(); i++) {
            Job job = mJobs.valueAt(i);
            if (tag.equals(job.getParams().getRequest().getTag())) {
                return job;
            }
        }
        return null;
    }

    public synchronized Set<Job> getAllJobs() {
        Set<Job> result = new HashSet<>();
        for (int i = 0; i < mJobs.size(); i++) {
            result.add(mJobs.valueAt(i));
        }
        return result;
    }

    private final class JobCallable implements Callable<Job.Result> {

        private final Job mJob;
        private final PowerManager.WakeLock mWakeLock;

        private JobCallable(Job job) {
            mJob = job;

            Context context = mJob.getContext();
            PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

            mWakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, JobExecutor.class.getSimpleName());
            acquireWakeLock();
        }

        @Override
        public Job.Result call() throws Exception {
            try {
                // just in case something was blocking and the wake lock is no longer acquired
                acquireWakeLock();

                return runJob();

            } finally {
                if (mWakeLock.isHeld()) {
                    mWakeLock.release();
                }
            }
        }

        private Job.Result runJob() {
            Job.Result result;
            try {
                result = mJob.runJob();
                Cat.i("Finished %s", mJob);

                handleResult(result);

            } catch (Throwable t) {
                Cat.e(t, "Crashed %s", mJob);
                result = mJob.getResult(); // probably the default value
            }

            return result;
        }

        private void handleResult(Job.Result result) {
            JobRequest request = mJob.getParams().getRequest();
            if (!request.isPeriodic() && Job.Result.RESCHEDULE.equals(result)) {
                int newJobId = request.reschedule(true);
                mJob.onReschedule(newJobId);
            }
        }

        private void acquireWakeLock() {
            if (!mWakeLock.isHeld()) {
                mWakeLock.acquire(TimeUnit.MINUTES.toMillis(3));
            }
        }
    }
}
