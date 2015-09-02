package com.evernote.android.job.test;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobApi;

import org.junit.After;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class JobManagerTest {

    @BeforeClass
    public static void createJobManager() {
        JobManager.create(InstrumentationRegistry.getContext(), new JobCreator.ClassNameJobCreator());
    }

    @Test
    public void testScheduleAndCancel() {
        assertThat(getManager().getApi()).isEqualTo(JobApi.getDefault(InstrumentationRegistry.getContext()));

        JobRequest request = getJobRequest();
        int id = request.schedule();

        assertThat(getManager().getJobRequest(id)).isNotNull();
        assertThat(getManager().getJob(id)).isNull();

        boolean canceled = getManager().cancel(id);
        assertThat(canceled).isTrue();

        assertThat(getManager().getAllJobRequests()).isEmpty();
        assertThat(getManager().getAllJobs()).isEmpty();

        request.schedule();
        request.schedule();
        request.schedule();

        assertThat(getManager().getAllJobRequests().size()).isEqualTo(1);

        int cancelCount = getManager().cancelAll();
        assertThat(cancelCount).isEqualTo(1);

        assertThat(getManager().getAllJobRequests()).isEmpty();
        assertThat(getManager().getAllJobs()).isEmpty();
    }

    @Test
    public void testSameIdAfterCancel() {
        JobRequest request = getJobRequest();
        int jobId = request.getJobId();

        getManager().schedule(request);

        int newId = getManager().getJobRequest(jobId).cancelAndEdit().build().schedule();
        assertThat(jobId).isEqualTo(newId);
    }

    @Test
    public void testCancelTag() {
        JobRequest request = getJobRequest();

        request.schedule();

        assertThat(getManager().getJobRequest("tag")).isNotNull();
        assertThat(getManager().getJobRequest("other")).isNull();

        boolean canceled = getManager().cancel("tag");
        assertThat(canceled).isTrue();
        assertThat(getManager().cancel("tag")).isFalse();
    }

    @After
    public void tearDown() {
        getManager().cancelAll();
    }

    private JobRequest getJobRequest() {
        return getBuilder()
                .setExecutionWindow(300_000L, 300_000L)
                .setTag("tag")
                .build();
    }

    private JobRequest.Builder getBuilder() {
        return new JobRequest.Builder(TestJob.class);
    }

    private JobManager getManager() {
        return JobManager.instance();
    }

    private static final class TestJob extends Job {
        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            return Result.FAILURE;
        }
    }
}
