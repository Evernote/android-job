package com.evernote.android.job;

import android.support.annotation.NonNull;

import org.junit.rules.ExternalResource;
import org.robolectric.RuntimeEnvironment;

/**
 * @author rwondratschek
 */
public final class JobManagerRule extends ExternalResource {

    private JobManager mManager;
    private final JobCreator mJobCreator;

    public JobManagerRule(@NonNull JobCreator jobCreator) {
        mJobCreator = jobCreator;
    }

    @Override
    protected void before() throws Throwable {
        mManager = JobManager.create(RuntimeEnvironment.application);
        mManager.addJobCreator(mJobCreator);
    }

    @Override
    protected void after() {
        mManager.cancelAll();
        mManager.destroy();
    }

    public JobManager getJobManager() {
        return mManager;
    }

    public void setJobFinished() {
        mManager.removeJobCreator(mJobCreator);
    }
}
