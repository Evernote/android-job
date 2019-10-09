package com.evernote.android.job;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.SynchronousExecutor;
import androidx.work.testing.TestDriver;
import androidx.work.testing.WorkManagerTestInitHelper;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import org.junit.rules.ExternalResource;

/**
 * @author rwondratschek
 */
public class PlatformWorkManagerRule extends ExternalResource {

    private JobManager mManager;

    @Override
    protected void before() {
        Context context = getContext();

        Executor executor = new SynchronousExecutor();
        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder()
            .setTaskExecutor(executor)
            .setExecutor(executor)
            .build());

        JobConfig.setJobReschedulePause(0, TimeUnit.MILLISECONDS);
        JobConfig.setSkipJobReschedule(true);
        JobConfig.forceApi(JobApi.WORK_MANAGER);

        mManager = JobManager.create(context);
        mManager.cancelAll();
    }

    @Override
    protected void after() {
        mManager.cancelAll();
        mManager.destroy();

        JobConfig.reset();
        WorkManager.getInstance(getContext()).cancelAllWork();
    }

    public JobManager getManager() {
        return mManager;
    }

    @SuppressWarnings("ConstantConditions")
    public void runJob(String tag) {
        TestDriver testDriver = WorkManagerTestInitHelper.getTestDriver(getContext());

        UUID id = getWorkStatus(tag).get(0).getId();
        testDriver.setAllConstraintsMet(id);
        testDriver.setInitialDelayMet(id);
    }

    public List<WorkInfo> getWorkStatus(String tag) {
        try {
            return WorkManager.getInstance(getContext()).getWorkInfosByTag(tag).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Context getContext() {
        return ApplicationProvider.getApplicationContext();
    }
}
