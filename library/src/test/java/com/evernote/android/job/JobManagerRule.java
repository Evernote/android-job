package com.evernote.android.job;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.core.app.JobIntentServiceReset;

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
        JobIntentServiceReset.reset();

        JobConfig.addLogger(TestLogger.INSTANCE);
        JobConfig.setSkipJobReschedule(true);
        JobConfig.setCloseDatabase(true);

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
