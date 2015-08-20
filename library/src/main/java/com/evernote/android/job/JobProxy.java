/*
 * Copyright 2012 Evernote Corporation.
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
import android.support.annotation.NonNull;

import com.evernote.android.job.util.JobApi;
import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;

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

    void cancel(JobRequest request);

    /*package*/ final class Common {

        public static long getStartMs(JobRequest request) {
            return request.getStartMs() + request.getBackoffOffset();
        }

        public static long getEndMs(JobRequest request) {
            return request.getEndMs() + request.getBackoffOffset();
        }

        public static long getAverageDelayMs(JobRequest request) {
            return getStartMs(request) + (getEndMs(request) - getStartMs(request)) / 2;
        }

        private final Context mContext;
        private final int mJobId;
        private final CatLog mCat;

        public Common(Service service, int jobId) {
            mContext = service;
            mJobId = jobId;
            mCat = new JobCat(service.getClass());
        }

        public JobRequest getPendingRequest() {
            // order is important for logging purposes
            JobRequest request = JobManager.instance(mContext).getJobRequest(mJobId);
            Job job = JobManager.instance(mContext).getJob(mJobId);
            boolean periodic = request != null && request.isPeriodic();

            if (job != null && !job.isFinished()) {
                mCat.d("Job %d is already running", mJobId);
                return null;

            } else if (job != null && !periodic) {
                mCat.d("Job %d already finished", mJobId);
                return null;

            } else if (job != null && System.currentTimeMillis() - job.getFinishedTimeStamp() < 2_000) {
                mCat.d("Job %d is periodic and just finished", mJobId);
                return null;

            } else if (request == null) {
                mCat.d("Request for ID %d was null", mJobId);
                return null;
            }

            return request;
        }

        @NonNull
        public Job.Result executeJobRequest(@NonNull JobRequest request) {
            mCat.d("Run job %d", mJobId);
            JobExecutor jobExecutor = JobManager.instance(mContext).getJobExecutor();

            try {
                if (!request.isPeriodic()) {
                    JobManager.instance(mContext).getJobStorage().remove(mJobId);
                }

                Future<Job.Result> future = jobExecutor.execute(mContext, request);

                // wait until done
                Job.Result result = future.get();
                mCat.d("Finished job %d %s", mJobId, result);
                return result;

            } catch (InterruptedException | ExecutionException e) {
                mCat.e(e);

                Job job = jobExecutor.getJob(mJobId);
                if (job != null) {
                    job.cancel();
                    mCat.e("Cancelled %d", mJobId);
                }

                return Job.Result.FAILURE;
            }
        }
    }
}
