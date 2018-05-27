package com.evernote.android.job;

import android.content.Context;
import android.support.test.InstrumentationRegistry;

import org.junit.rules.ExternalResource;

import java.util.concurrent.TimeUnit;

import androidx.work.Configuration;
import androidx.work.impl.WorkManagerImpl;
import androidx.work.test.WorkManagerTestInitHelper;

/**
 * @author rwondratschek
 */
public class PlatformWorkManagerRule extends ExternalResource {

    private JobManager mManager;

    @Override
    protected void before() {
        Context context = InstrumentationRegistry.getTargetContext();

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
        resetWorkManager();
    }

    public void resetWorkManager() {
        WorkManagerImpl.setDelegate(new WorkManagerImpl(InstrumentationRegistry.getTargetContext(), new Configuration.Builder().build()));
    }

    public void initTestWorkManager() {
        WorkManagerTestInitHelper.initializeTestWorkManager(InstrumentationRegistry.getTargetContext());
    }

    public JobManager getManager() {
        return mManager;
    }
}
