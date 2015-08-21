package com.evernote.android.job.test;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobApi;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class JobManagerTest {

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

    @After
    public void tearDown() {
        getManager().cancelAll();
    }

    private JobRequest getJobRequest() {
        return getBuilder()
                .setExecutionWindow(300_000L, 300_000L)
                .build();
    }

    private JobRequest.Builder getBuilder() {
        return new JobRequest.Builder(InstrumentationRegistry.getContext(), TestJob.class);
    }

    private JobManager getManager() {
        return JobManager.instance(InstrumentationRegistry.getContext());
    }

    private static final class TestJob implements Job.Action {

        @NonNull
        @Override
        public Job.Result onRunJob(@NonNull Job.Params params) {
            return Job.Result.FAILURE;
        }

        @Override
        public void onReschedule(int newJobId) {}
    }
}
