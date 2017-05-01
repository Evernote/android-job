package com.evernote.android.job;

import android.content.Context;
import android.support.annotation.NonNull;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.TestCat;

import org.junit.Rule;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.doReturn;

/**
 * @author rwondratschek
 */
@SuppressWarnings("WeakerAccess")
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
    protected final Context context() {
        return RuntimeEnvironment.application;
    }

    protected void executeJob(int jobId, @NonNull Job.Result expected) {
        try {
            executeJobAsync(jobId, expected).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError("Job timeout");
        }
    }

    protected Future<Job.Result> executeJobAsync(int jobId, @NonNull final Job.Result expected) {
        final JobProxy.Common common = new JobProxy.Common(RuntimeEnvironment.application, TestCat.INSTANCE, jobId);

        final JobRequest pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Job.Result> future = executor.submit(new Callable<Job.Result>() {
            @Override
            public Job.Result call() throws Exception {
                Job.Result result = common.executeJobRequest(pendingRequest, null);
                assertThat(result).isEqualTo(expected);
                assertThat(common.getPendingRequest(true)).isNull();

                return result;
            }
        });

        executor.shutdown();
        return future;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void resetJob(int jobId) {
        Job job = manager().getJob(jobId);
        if (job != null) {
            doReturn(-1L).when(job).getFinishedTimeStamp();
        }
    }
}
