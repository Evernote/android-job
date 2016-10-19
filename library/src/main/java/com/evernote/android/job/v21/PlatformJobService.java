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
package com.evernote.android.job.v21;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class PlatformJobService extends JobService {

    /*
     * JobScheduler can have issues: http://stackoverflow.com/questions/32079407/android-jobscheduler-onstartjob-called-multiple-times
     */

    private static final CatLog CAT = new JobCat("PlatformJobService");
    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    @Override
    public boolean onStartJob(final JobParameters params) {
        final int jobId = params.getJobId();
        final JobProxy.Common common = new JobProxy.Common(this, jobId);

        final JobRequest request = common.getPendingRequest(true);
        if (request == null) {
            return false;
        }

        EXECUTOR_SERVICE.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    common.executeJobRequest(request);

                } finally {
                    // do not reschedule
                    jobFinished(params, false);
                }
            }
        });

        // yes, we have a job running in the background
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        Job job = JobManager.instance().getJob(params.getJobId());
        if (job != null) {
            job.cancel();
            CAT.d("Called onStopJob for %s", job);
        } else {
            CAT.d("Called onStopJob, job %d not found", params.getJobId());
        }


        // do not reschedule
        return false;
    }
}
