package com.evernote.android.job;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.evernote.android.job.util.JobApi;
import com.facebook.stetho.Stetho;

import org.junit.After;
import org.junit.AfterClass;
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
    public static void beforeClass() {
        Stetho.initializeWithDefaults(InstrumentationRegistry.getContext());

        JobManager.create(InstrumentationRegistry.getContext()).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return new TestJob();
            }
        });
    }

    @AfterClass
    public static void afterClass() {
        JobManager.instance().destroy();
    }

    @Test
    public void testScheduleAndCancel() {
        JobApi defaultApi = JobApi.getDefault(InstrumentationRegistry.getContext(), getManager().getConfig().isGcmApiEnabled());
        assertThat(getManager().getApi()).isEqualTo(defaultApi);

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

        assertThat(getManager().getAllJobRequestsForTag(TestJob.TAG)).isNotNull().hasSize(1);
        assertThat(getManager().getAllJobRequestsForTag("other")).isNotNull().isEmpty();

        assertThat(getManager().cancelAllForTag(TestJob.TAG)).isEqualTo(1);
        assertThat(getManager().cancelAllForTag(TestJob.TAG)).isZero();
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
        return new JobRequest.Builder(TestJob.TAG);
    }

    private JobManager getManager() {
        return JobManager.instance();
    }

    private static final class TestJob extends Job {

        private static final String TAG = "tag";

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            return Result.FAILURE;
        }
    }
}
