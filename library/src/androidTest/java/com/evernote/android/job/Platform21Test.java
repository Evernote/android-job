package com.evernote.android.job;

import android.os.Build;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class Platform21Test {

    @Rule
    public PlatformJobManagerRule mJobManagerRule = new PlatformJobManagerRule();

    @Test(expected = IllegalStateException.class)
    public void test100DistinctJobsLimit() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

        for (int i = 0; i < 130; i++) {
            new JobRequest.Builder("tag")
                    .setExecutionWindow(30_000, 40_000)
                    .build()
                    .schedule();
        }

        fail("It shouldn't be possible to create more than 100 distinct jobs with the JobScheduler");
    }

    @Test
    public void testRescheduleService() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(300_000, 400_000)
                .build()
                .schedule();

        assertThat(mJobManagerRule.getManager().getAllJobRequests()).hasSize(1);
        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).hasSize(1);

        mJobManagerRule.getJobScheduler().cancel(jobId);
        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).isEmpty();

        waitForJobRescheduleService();
        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).hasSize(1);
    }

    @Test
    public void verifyNotLandingInCrashLoop() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

        try {
            for (int i = 0; i < 130; i++) {
                new JobRequest.Builder("tag")
                        .setExecutionWindow(300_000, 400_000)
                        .build()
                        .schedule();
            }

            fail("It shouldn't be possible to create more than 100 distinct jobs with the JobScheduler");

        } catch (Exception ignored) {
        }

        JobManager manager = mJobManagerRule.getManager();

        int jobCount = manager.getAllJobRequests().size();
        assertThat(manager.getAllJobRequests()).isNotEmpty();

        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).hasSize(jobCount);

        mJobManagerRule.getJobScheduler().cancelAll();
        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).isEmpty();

        final int moreJobs = 50;
        for (int i = 0; i < moreJobs; i++) {
            new JobRequest.Builder("tag")
                    .setExecutionWindow(300_000, 400_000)
                    .build()
                    .schedule();
        }

        assertThat(manager.getAllJobRequests()).hasSize(jobCount + moreJobs);

        waitForJobRescheduleService();
        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler().size()).isGreaterThanOrEqualTo(jobCount);
    }

    private void waitForJobRescheduleService() {
        new JobRescheduleService().rescheduleJobs(mJobManagerRule.getManager());
    }
}
