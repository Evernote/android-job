package com.evernote.android.job;

import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class Platform21Test {

    @Rule
    public JobManagerRule mJobManagerRule = new JobManagerRule();

    @Test(expected = IllegalStateException.class)
    public void test100DistinctJobsLimit() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

        for (int i = 0; i < 130; i++) {
            new JobRequest.Builder("tag")
                    .setExecutionWindow(30_000, 40_000)
                    .build()
                    .schedule();
        }

        throw new AssertionError("It shouldn't be possible to create more than 100 distinct jobs with the JobScheduler");
    }

    @Test
    public void testRescheduleService() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

        JobManager manager = mJobManagerRule.getManager();
        Context context = InstrumentationRegistry.getTargetContext();

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(300_000, 400_000)
                .build()
                .schedule();

        assertThat(manager.getAllJobRequests()).hasSize(1);

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertThat(jobScheduler.getAllPendingJobs()).hasSize(1);

        jobScheduler.cancel(jobId);
        assertThat(jobScheduler.getAllPendingJobs()).isEmpty();

        JobRescheduleService.latch.await(15, TimeUnit.SECONDS);
        assertThat(jobScheduler.getAllPendingJobs()).hasSize(1);
    }

    @Test
    public void verifyNotLandingInCrashLoop() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

        try {
            for (int i = 0; i < 130; i++) {
                new JobRequest.Builder("tag")
                        .setExecutionWindow(300_000, 400_000)
                        .build()
                        .schedule();
            }

            throw new AssertionError("It shouldn't be possible to create more than 100 distinct jobs with the JobScheduler");

        } catch (Exception ignored) {
        }

        JobManager manager = mJobManagerRule.getManager();
        Context context = InstrumentationRegistry.getTargetContext();

        int jobCount = manager.getAllJobRequests().size();
        assertThat(manager.getAllJobRequests()).isNotEmpty();

        JobScheduler jobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        assertThat(jobScheduler.getAllPendingJobs()).hasSize(jobCount);

        jobScheduler.cancelAll();
        assertThat(jobScheduler.getAllPendingJobs()).isEmpty();

        final int moreJobs = 50;
        for (int i = 0; i < moreJobs; i++) {
            new JobRequest.Builder("tag")
                    .setExecutionWindow(300_000, 400_000)
                    .build()
                    .schedule();
        }

        assertThat(manager.getAllJobRequests()).hasSize(jobCount + moreJobs);

        JobRescheduleService.latch.await(15, TimeUnit.SECONDS);
        assertThat(jobScheduler.getAllPendingJobs()).hasSize(jobCount);
    }
}
