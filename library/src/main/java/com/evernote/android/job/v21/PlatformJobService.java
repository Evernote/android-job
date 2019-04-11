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
package com.evernote.android.job.v21;

import android.annotation.TargetApi;
import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.RestrictTo;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobConfig;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;

/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PlatformJobService extends JobService {

    /*
     * JobScheduler can have issues: http://stackoverflow.com/questions/32079407/android-jobscheduler-onstartjob-called-multiple-times
     */

    private static final JobCat CAT = new JobCat("PlatformJobService");

    @Override
    public boolean onStartJob(final JobParameters params) {
        JobConfig.getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    final int jobId = params.getJobId();
                    final JobProxy.Common common = new JobProxy.Common(PlatformJobService.this, CAT, jobId);

                    // don't mark starting!
                    final JobRequest request = common.getPendingRequest(true, false);
                    if (request == null) {
                        return;
                    }

                    if (request.isTransient()) {
                        if (TransientBundleCompat.startWithTransientBundle(PlatformJobService.this, request)) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                // should only happen during testing if an API is disabled
                                CAT.d("PendingIntent for transient bundle is not null although running on O, using compat mode, request %s", request);
                            }
                            return;

                        } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                            CAT.d("PendingIntent for transient job %s expired", request);
                            return;
                        }
                    }

                    common.markStarting(request);

                    common.executeJobRequest(request, getTransientBundle(params));

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
        Job job = JobManager.create(this).getJob(params.getJobId());
        if (job != null) {
            job.cancel();
            CAT.d("Called onStopJob for %s", job);
        } else {
            CAT.d("Called onStopJob, job %d not found", params.getJobId());
        }

        // do not reschedule
        return false;
    }

    @TargetApi(Build.VERSION_CODES.O)
    private Bundle getTransientBundle(JobParameters params) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            return params.getTransientExtras();
        } else {
            return Bundle.EMPTY;
        }
    }
}
