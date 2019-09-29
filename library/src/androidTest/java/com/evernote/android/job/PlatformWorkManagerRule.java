package com.evernote.android.job;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.test.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.work.Configuration;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.testing.WorkManagerTestInitHelper;

/**
 * @author rwondratschek
 */
public class PlatformWorkManagerRule extends ExternalResource {

    private JobManager mManager;

    @Override
    protected void before() {
        Context context = InstrumentationRegistry.getTargetContext();

        Executor executor = new Executor() {
            @Override
            public void execute(@NonNull Runnable command) {
                command.run();
            }
        };

        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder().setExecutor(executor).build());

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
        WorkManager.getInstance().cancelAllWork();
    }

    public JobManager getManager() {
        return mManager;
    }

    public void runJob(String tag) {
        WorkManagerTestInitHelper.getTestDriver().setInitialDelayMet(getWorkStatus(tag).get(0).getId());
    }

    public List<WorkInfo> getWorkStatus(String tag) {
        try {
            return WorkManager.getInstance().getWorkInfosByTag(tag).get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
