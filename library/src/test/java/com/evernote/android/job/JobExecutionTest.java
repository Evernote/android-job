package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobExecutionTest extends BaseJobManagerTest {

    @Test
    public void testPeriodicJob() throws Exception {
        int jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        executeJob(jobId, Job.Result.SUCCESS);

        // make sure job request is still around
        assertThat(manager().getAllJobRequestsForTag(DummyJobs.SuccessJob.TAG)).hasSize(1);
    }

    @Test
    public void testSimpleJob() throws Throwable {
        final int jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(200_000L, 400_000L)
                .build()
                .schedule();

        executeJob(jobId, Job.Result.SUCCESS);

        assertThat(manager().getAllJobRequestsForTag(DummyJobs.SuccessJob.TAG)).isEmpty();

        assertThat(manager().getJobRequest(jobId)).isNull();
        assertThat(manager().getJobRequest(jobId, true)).isNull();
    }

    @Test
    public void testStartedState() throws Throwable {
        int jobId = DummyJobs.createBuilder(DummyJobs.TwoSecondPauseJob.class)
                .setExecutionWindow(300_000, 400_000)
                .build()
                .schedule();

        executeJobAsync(jobId, Job.Result.SUCCESS);

        // wait until the job is started
        Thread.sleep(100);

        // request should be in started state, running but not removed from DB
        JobRequest startedRequest = manager().getJobRequest(jobId, true);
        assertThat(startedRequest).isNotNull();
        assertThat(startedRequest.isStarted()).isTrue();
    }

    @Test
    public void testPeriodicJobNotInStartedState() throws Throwable {
        int jobId = DummyJobs.createBuilder(DummyJobs.TwoSecondPauseJob.class)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        executeJobAsync(jobId, Job.Result.SUCCESS);

        // wait until the job is started
        Thread.sleep(100);

        // request should be in started state, running but not removed from DB
        JobRequest startedRequest = manager().getJobRequest(jobId, true);
        assertThat(startedRequest).isNotNull();
        assertThat(startedRequest.isStarted()).isFalse();
    }
}
