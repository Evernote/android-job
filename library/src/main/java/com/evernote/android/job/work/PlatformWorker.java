package com.evernote.android.job.work;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.RestrictTo;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;

import androidx.work.Worker;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class PlatformWorker extends Worker {

    private static final JobCat CAT = new JobCat("PlatformWorker");

    @NonNull
    @Override
    public Result doWork() {
        final int jobId = getJobId();
        try {
            JobProxy.Common common = new JobProxy.Common(getApplicationContext(), CAT, jobId);

            JobRequest request = common.getPendingRequest(true, true);
            if (request == null) {
                return Result.FAILURE;
            }

            Bundle transientBundle = null;
            if (request.isTransient()) {
                transientBundle = TransientBundleHolder.getBundle(jobId);
                if (transientBundle == null) {
                    CAT.d("Transient bundle is gone for request %s", request);
                    return Result.FAILURE;
                }
            }

            Job.Result result = common.executeJobRequest(request, transientBundle);
            if (Job.Result.SUCCESS == result) {
                return Result.SUCCESS;
            } else {
                return Result.FAILURE;
            }
        } finally {
            TransientBundleHolder.cleanUpBundle(jobId);
        }
    }

    @Override
    public void onStopped(boolean cancelled) {
        int jobId = getJobId();
        Job job = JobManager.create(getApplicationContext()).getJob(jobId);

        if (job != null) {
            job.cancel();
            CAT.d("Called onStopped for %s", job);
        } else {
            CAT.d("Called onStopped, job %d not found", jobId);
        }
    }

    private String getTag() {
        return getTags().iterator().next();
    }

    private int getJobId() {
        return JobProxyWorkManager.getJobIdFromTag(getTag());
    }
}
