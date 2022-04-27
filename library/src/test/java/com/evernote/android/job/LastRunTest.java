package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Ignore;
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
public class LastRunTest extends BaseJobManagerTest {

    @Test
    @Ignore("Started failing with the SDK upgrade.")
    public void updateLastRunPeriodicSuccess() throws Exception {
        testPeriodicJob(DummyJobs.SuccessJob.class, Job.Result.SUCCESS);
    }

    @Test
    @Ignore("Started failing with the SDK upgrade.")
    public void updateLastRunPeriodicReschedule() throws Exception {
        testPeriodicJob(DummyJobs.RescheduleJob.class, Job.Result.RESCHEDULE);
    }

    @Test
    @Ignore("Started failing with the SDK upgrade.")
    public void updateLastRunPeriodicFailure() throws Exception {
        testPeriodicJob(DummyJobs.FailureJob.class, Job.Result.FAILURE);
    }

    private void testPeriodicJob(Class<? extends Job> clazz, Job.Result result) throws Exception {
        int jobId = DummyJobs.createBuilder(clazz)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        assertThat(manager().getJobRequest(jobId).getLastRun()).isEqualTo(0);

        executeJob(jobId, result);

        long lastRun = manager().getJobRequest(jobId).getLastRun();
        assertThat(lastRun).isGreaterThan(0);

        Thread.sleep(2L);
        resetJob(jobId);

        executeJob(jobId, result);
        assertThat(manager().getJobRequest(jobId).getLastRun()).isGreaterThan(lastRun);
    }

    @Test
    public void updateLastRunReschedule() throws Exception {
        int jobId = DummyJobs.createBuilder(DummyJobs.RescheduleJob.class)
                .setExecutionWindow(1_000, 2_000)
                .build()
                .schedule();

        assertThat(manager().getJobRequest(jobId).getLastRun()).isEqualTo(0);

        executeJob(jobId, Job.Result.RESCHEDULE);
        DummyJobs.RescheduleJob job = (DummyJobs.RescheduleJob) manager().getJob(jobId);
        jobId = job.getNewJobId();

        long lastRun = manager().getJobRequest(jobId).getLastRun();
        assertThat(lastRun).isGreaterThan(0);

        Thread.sleep(2L);

        executeJob(jobId, Job.Result.RESCHEDULE);
        job = (DummyJobs.RescheduleJob) manager().getJob(jobId);
        jobId = job.getNewJobId();

        assertThat(manager().getJobRequest(jobId).getLastRun()).isGreaterThan(lastRun);
    }

    @Test
    public void verifyTimeNotUpdatedSuccess() throws Exception {
        int jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(1_000, 2_000)
                .build()
                .schedule();

        JobRequest request = manager().getJobRequest(jobId);
        assertThat(request.getLastRun()).isEqualTo(0);

        executeJob(jobId, Job.Result.SUCCESS);
        assertThat(request.getLastRun()).isEqualTo(0);
    }

    @Test
    public void verifyTimeNotUpdatedFailure() throws Exception {
        int jobId = DummyJobs.createBuilder(DummyJobs.FailureJob.class)
                .setExecutionWindow(1_000, 2_000)
                .build()
                .schedule();

        JobRequest request = manager().getJobRequest(jobId);
        assertThat(request.getLastRun()).isEqualTo(0);

        executeJob(jobId, Job.Result.FAILURE);
        assertThat(request.getLastRun()).isEqualTo(0);
    }
}
