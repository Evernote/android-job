package com.evernote.android.job.demo;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;

/**
 * @author rwondratschek
 */
public class DemoSyncJob extends Job {

    public static final String TAG = "job_demo_tag";

    @Override
    @NonNull
    protected Result onRunJob(final Params params) {
        boolean success = new DemoSyncEngine(getContext()).sync();
        return success ? Result.SUCCESS : Result.FAILURE;
    }
}
