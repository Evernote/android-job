package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobManagerTest extends BaseJobManagerTest {

    @Test
    public void testScheduleAndCancel() {
        JobRequest request = DummyJobs.createOneOff();
        int jobId = request.schedule();

        assertThat(manager().getJobRequest(jobId)).isNotNull();
        assertThat(manager().getJob(jobId)).isNull();

        assertThat(manager().cancel(jobId)).isTrue();

        assertThat(manager().getAllJobRequests()).isEmpty();
        assertThat(manager().getAllJobs()).isEmpty();

        request.schedule();

        assertThat(manager().getAllJobRequests()).hasSize(1);
        assertThat(manager().cancelAll()).isEqualTo(1);

        assertThat(manager().getAllJobRequests()).isEmpty();
        assertThat(manager().getAllJobs()).isEmpty();
    }

    @Test
    public void testSameIdAfterCancel() {
        JobRequest request = DummyJobs.createOneOff();
        int jobId = request.getJobId();
        assertThat(request.getScheduledAt()).isEqualTo(0L);

        manager().schedule(request);
        assertThat(request.getScheduledAt()).isGreaterThan(0L);

        JobRequest requestNew = request.cancelAndEdit().build();
        assertThat(request.getScheduledAt()).isEqualTo(0L);

        int newId = requestNew.schedule();
        assertThat(newId).isEqualTo(jobId);

        assertThat(request.getScheduledAt()).isEqualTo(0L);
        assertThat(requestNew.getScheduledAt()).isGreaterThan(0L);
    }

    @Test
    public void testCancelTag() {
        JobRequest request = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(300_000, 400_000)
                .build();
        request.schedule();

        assertThat(request.getScheduledAt()).isGreaterThan(0L);

        assertThat(manager().getAllJobRequestsForTag(DummyJobs.SuccessJob.TAG)).hasSize(1);
        assertThat(manager().getAllJobRequestsForTag("other")).isNotNull().isEmpty();

        assertThat(manager().cancelAllForTag(DummyJobs.SuccessJob.TAG)).isEqualTo(1);
        assertThat(manager().cancelAllForTag(DummyJobs.SuccessJob.TAG)).isZero();

        assertThat(request.getScheduledAt()).isEqualTo(0L);
    }

    @Test
    public void testScheduleIsIdempotent() throws Exception {
        JobRequest request = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(300_000, 400_000)
                .build();
        int jobId = request.schedule();

        long scheduledAt = request.getScheduledAt();
        assertThat(scheduledAt).isGreaterThan(0L);

        Thread.sleep(10);

        int newJobId = request.schedule();

        assertThat(newJobId).isEqualTo(jobId);
        assertThat(request.getScheduledAt()).isEqualTo(scheduledAt);
    }
}
