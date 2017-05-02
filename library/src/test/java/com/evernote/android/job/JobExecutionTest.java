package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.test.TestCat;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.CountDownLatch;
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
                .setPersisted(true)
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
    public void testTransientState() throws Throwable {
        int jobId = DummyJobs.createBuilder(DummyJobs.TwoSecondPauseJob.class)
                .setExecutionWindow(300_000, 400_000)
                .build()
                .schedule();

        executeJobAsync(jobId, Job.Result.SUCCESS);

        // wait until the job is started
        Thread.sleep(100);

        // request should be in transient state, running but not removed from DB
        JobRequest transientRequest = manager().getJobRequest(jobId, true);
        assertThat(transientRequest).isNotNull();
        assertThat(transientRequest.isTransient()).isTrue();
    }

    @Test
    public void testPeriodicJobNotInTransientState() throws Throwable {
        int jobId = DummyJobs.createBuilder(DummyJobs.TwoSecondPauseJob.class)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        executeJobAsync(jobId, Job.Result.SUCCESS);

        // wait until the job is started
        Thread.sleep(100);

        // request should be in transient state, running but not removed from DB
        JobRequest transientRequest = manager().getJobRequest(jobId, true);
        assertThat(transientRequest).isNotNull();
        assertThat(transientRequest.isTransient()).isFalse();
    }

    @Test
    public void verifyNoRaceConditionOneOff() throws Exception {
        final int jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(20))
                .build()
                .schedule();

        final JobProxy.Common common = new JobProxy.Common(RuntimeEnvironment.application, TestCat.INSTANCE, jobId);
        final JobRequest request = common.getPendingRequest(true, true);
        assertThat(request).isNotNull();

        final CountDownLatch latch = new CountDownLatch(1);

        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }

                common.executeJobRequest(request);
                latch.countDown();
            }
        }.start();

        assertThat(common.getPendingRequest(true, false)).isNull();
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(2_000);
        assertThat(common.getPendingRequest(true, false)).isNull();
    }

    @Test
    public void verifyNoRaceConditionPeriodic() throws Exception {
        final int jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .build()
                .schedule();

        final JobProxy.Common common = new JobProxy.Common(RuntimeEnvironment.application, TestCat.INSTANCE, jobId);
        final JobRequest request = common.getPendingRequest(true, true);
        assertThat(request).isNotNull();

        final CountDownLatch latch = new CountDownLatch(1);

        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException ignored) {
                }

                common.executeJobRequest(request);
                latch.countDown();
            }
        }.start();

        assertThat(common.getPendingRequest(true, false)).isNull();
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        Thread.sleep(2_000);
        assertThat(common.getPendingRequest(true, false)).isNotNull();
    }
}
