package com.evernote.android.job;

import android.support.test.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

/**
 * @author rwondratschek
 */
public class JobManagerRule extends ExternalResource {

    private JobManager mManager;

    @Override
    protected void before() throws Throwable {
        mManager = JobManager.create(InstrumentationRegistry.getTargetContext());
    }

    @Override
    protected void after() {
        mManager.cancelAll();
        mManager.destroy();

        JobConfig.reset();
    }

    public JobManager getManager() {
        return mManager;
    }
}
