package com.evernote.android.job;

import android.content.Context;
import android.support.annotation.NonNull;

import com.evernote.android.job.test.TestLogger;

import org.junit.rules.ExternalResource;

/**
 * @author rwondratschek
 */
public final class JobManagerRule extends ExternalResource {

    private JobManager mManager;
    private final JobCreator mJobCreator;
    private final Context mContext;

    public JobManagerRule(@NonNull JobCreator jobCreator, @NonNull Context context) {
        mJobCreator = jobCreator;
        mContext = context;
    }

    @Override
    protected void before() throws Throwable {
        JobConfig.addLogger(TestLogger.INSTANCE);
        JobConfig.setSkipJobReschedule(true);

        mManager = JobManager.create(mContext);
        mManager.addJobCreator(mJobCreator);
    }

    @Override
    protected void after() {
        mManager.cancelAll();
        mManager.destroy();
        JobConfig.reset();
    }

    public JobManager getJobManager() {
        return mManager;
    }
}
