package com.evernote.android.job;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.app.job.JobWorkItem;
import android.content.Context;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.test.TestClock;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class BackoffCriteriaTests extends BaseJobManagerTest {

    @Test
    public void verifyBackoffCriteriaIsAppliedForImmediatelyStartedJobs() {
        JobConfig.setClock(new TestClock());

        AlarmManager alarmManager = mock(AlarmManager.class);
        JobScheduler jobScheduler = mock(JobScheduler.class);

        doReturn(alarmManager).when(context()).getSystemService(eq(Context.ALARM_SERVICE));
        doReturn(jobScheduler).when(context()).getSystemService(eq(Context.JOB_SCHEDULER_SERVICE));

        int jobId = DummyJobs.createBuilder(DummyJobs.RescheduleJob.class)
                .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
                .startNow()
                .build()
                .schedule();

        // this uses the JobIntentService under the hood, so verify that the JobScheduler was used for Android O and above
        verify(jobScheduler).enqueue(any(JobInfo.class), any(JobWorkItem.class));

        executeJob(jobId, Job.Result.RESCHEDULE);
        jobId = manager().getAllJobRequests().iterator().next().getJobId(); // because the job was rescheduled and its ID changed

        // make sure that this method was not called again, because with the backoff criteria we have a delay
        verify(jobScheduler, times(1)).enqueue(any(JobInfo.class), any(JobWorkItem.class));

        // instead the AlarmManager should be used
        verify(alarmManager).setExactAndAllowWhileIdle(anyInt(), eq(5_000L), any(PendingIntent.class));

        executeJob(jobId, Job.Result.RESCHEDULE);
        verify(jobScheduler, times(1)).enqueue(any(JobInfo.class), any(JobWorkItem.class));
        verify(alarmManager).setExactAndAllowWhileIdle(anyInt(), eq(10_000L), any(PendingIntent.class));
    }
}
