package com.evernote.android.job;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import androidx.work.Configuration;
import androidx.work.WorkManager;
import androidx.work.test.WorkManagerTestInitHelper;

/**
 * @author rwondratschek
 */
public class PlatformWorkManagerRule extends ExternalResource {

    private JobManager mManager;
    private Executor mExecutor;
    private boolean mAllowExecution;

    @Override
    protected void before() {
        Context context = InstrumentationRegistry.getTargetContext();

        mAllowExecution = false;
        mExecutor = new Executor() {
            @Override
            public void execute(@NonNull Runnable command) {
                if (mAllowExecution) {
                    command.run();
                }
            }
        };

        WorkManagerTestInitHelper.initializeTestWorkManager(context, new Configuration.Builder().setExecutor(mExecutor).build());

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

    public void setAllowExecution(boolean allowExecution) {
        mAllowExecution = allowExecution;
    }
}
