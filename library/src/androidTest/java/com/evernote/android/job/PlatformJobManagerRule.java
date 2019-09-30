package com.evernote.android.job;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.rules.ExternalResource;

/**
 * @author rwondratschek
 */
public class PlatformJobManagerRule extends ExternalResource {

    private JobManager mManager;

    @Override
    protected void before() {
        JobConfig.setJobReschedulePause(0, TimeUnit.MILLISECONDS);
        JobConfig.setSkipJobReschedule(true);
        JobConfig.setApiEnabled(JobApi.WORK_MANAGER, false);

        mManager = JobManager.create(ApplicationProvider.getApplicationContext());
        mManager.cancelAll();
    }

    @Override
    protected void after() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getJobScheduler().cancelAll();
        }

        mManager.cancelAll();
        mManager.destroy();

        getJobScheduler().cancelAll();
        JobConfig.reset();
    }

    public JobManager getManager() {
        return mManager;
    }

    public JobScheduler getJobScheduler() {
        return (JobScheduler) ApplicationProvider.getApplicationContext().getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    public List<JobInfo> getAllPendingJobsFromScheduler() {
        JobScheduler jobScheduler = getJobScheduler();
        ArrayList<JobInfo> jobs = new ArrayList<>(jobScheduler.getAllPendingJobs());

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Iterator<JobInfo> iterator = jobs.iterator();
            while (iterator.hasNext()) {
                if (iterator.next().getId() == JobIdsInternal.JOB_ID_JOB_RESCHEDULE_SERVICE) {
                    iterator.remove();
                }
            }
        }
        return jobs;
    }
}
