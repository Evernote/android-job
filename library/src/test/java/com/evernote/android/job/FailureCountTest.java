package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class FailureCountTest extends BaseJobManagerTest {

    @Test
    @Ignore("Started failing with the SDK upgrade.")
    public void incrementPeriodicJobFailureCount() {
        int jobId = DummyJobs.createBuilder(DummyJobs.FailureJob.class)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        executeJob(jobId, Job.Result.FAILURE);
        assertThat(manager().getJobRequest(jobId).getFailureCount()).isEqualTo(1);

        resetJob(jobId);

        executeJob(jobId, Job.Result.FAILURE);
        assertThat(manager().getJobRequest(jobId).getFailureCount()).isEqualTo(2);
    }

    @Test
    public void incrementRescheduleJobFailureCount() {
        int jobId = DummyJobs.createBuilder(DummyJobs.RescheduleJob.class)
                .setExecutionWindow(1_000, 2_000)
                .build()
                .schedule();

        executeJob(jobId, Job.Result.RESCHEDULE);
        DummyJobs.RescheduleJob job = (DummyJobs.RescheduleJob) manager().getJob(jobId);
        jobId = job.getNewJobId();

        assertThat(manager().getJobRequest(jobId).getFailureCount()).isEqualTo(1);

        executeJob(jobId, Job.Result.RESCHEDULE);
        job = (DummyJobs.RescheduleJob) manager().getJob(jobId);
        jobId = job.getNewJobId();

        assertThat(manager().getJobRequest(jobId).getFailureCount()).isEqualTo(2);
    }

    @Test
    public void verifyDeletedJobIsNotPersistedAgain() throws Exception {
        int jobId = DummyJobs.createBuilder(DummyJobs.TwoSecondPauseJob.class)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        Future<Job.Result> future = executeJobAsync(jobId, Job.Result.SUCCESS);

        // wait until the job is started
        Thread.sleep(1_000);
        assertThat(manager().getJob(jobId)).isNotNull();

        // will also cancel the running job
        manager().cancel(jobId);

        assertThat(future.get(3, TimeUnit.SECONDS)).isEqualTo(Job.Result.SUCCESS);
        assertThat(manager().getJobRequest(jobId)).isNull();
    }
}
