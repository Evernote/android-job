package com.evernote.android.job;

import android.support.annotation.NonNull;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.TestCat;

import org.junit.Rule;
import org.robolectric.RuntimeEnvironment;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * @author rwondratschek
 */
public abstract class BaseJobManagerTest {

    @Rule
    public final JobManagerRule mJobManagerRule = new JobManagerRule(provideJobCreator());

    @NonNull
    protected JobCreator provideJobCreator() {
        return new DummyJobs.SpyableJobCreator(DummyJobs.TEST_JOB_CREATOR);
    }

    @NonNull
    protected final JobManager manager() {
        return mJobManagerRule.getJobManager();
    }

    @NonNull
    protected void executeJob(int jobId, @NonNull Job.Result expected) {
        JobProxy.Common common = new JobProxy.Common(RuntimeEnvironment.application, new TestCat(), jobId);

        JobRequest pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();

        Job.Result result = common.executeJobRequest(pendingRequest);
        assertThat(result).isEqualTo(expected);

        pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNull();
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void resetJob(int jobId) {
        Job job = manager().getJob(jobId);
        if (job != null) {
            doReturn(-1L).when(job).getFinishedTimeStamp();
        }
    }
}
