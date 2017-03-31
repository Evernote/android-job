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
        request.schedule();
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

        manager().schedule(request);

        int newId = request.cancelAndEdit().build().schedule();
        assertThat(newId).isEqualTo(jobId);
    }

    @Test
    public void testCancelTag() {
        JobRequest request = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(300_000, 400_000)
                .build();
        request.schedule();

        assertThat(manager().getAllJobRequestsForTag(DummyJobs.SuccessJob.TAG)).hasSize(1);
        assertThat(manager().getAllJobRequestsForTag("other")).isNotNull().isEmpty();

        assertThat(manager().cancelAllForTag(DummyJobs.SuccessJob.TAG)).isEqualTo(1);
        assertThat(manager().cancelAllForTag(DummyJobs.SuccessJob.TAG)).isZero();
    }
}
