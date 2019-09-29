package com.evernote.android.job;

import androidx.annotation.NonNull;

import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobCanceledTest extends BaseJobManagerTest {

    @Test
    public void verifyOnCancelInvokedOnce() {
        final AtomicInteger onCancelCalled = new AtomicInteger(0);
        final Job job = new Job() {
            @NonNull
            @Override
            protected Result onRunJob(@NonNull Params params) {
                cancel();
                cancel();
                cancel();
                return Result.SUCCESS;
            }

            @Override
            protected void onCancel() {
                onCancelCalled.incrementAndGet();
            }
        };

        manager().addJobCreator(new JobCreator() {
            @Override
            public Job create(@NonNull String tag) {
                return job;
            }
        });

        final String tag = "something";
        final int jobId = new JobRequest.Builder(tag)
                .setExecutionWindow(200_000L, 400_000L)
                .build()
                .schedule();

        executeJob(jobId, Job.Result.SUCCESS);

        assertThat(manager().getAllJobRequestsForTag(tag)).isEmpty();

        assertThat(manager().getJobRequest(jobId)).isNull();
        assertThat(manager().getJobRequest(jobId, true)).isNull();

        assertThat(job.isCanceled()).isTrue();
        assertThat(onCancelCalled.get()).isEqualTo(1);
    }

    @Test
    public void verifyOnCancelNotInvokedWhenFinished() {
        final AtomicInteger onCancelCalled = new AtomicInteger(0);
        final Job job = new Job() {
            @NonNull
            @Override
            protected Result onRunJob(@NonNull Params params) {
                return Result.SUCCESS;
            }

            @Override
            protected void onCancel() {
                onCancelCalled.incrementAndGet();
            }
        };

        manager().addJobCreator(new JobCreator() {
            @Override
            public Job create(@NonNull String tag) {
                return job;
            }
        });

        final String tag = "something";
        final int jobId = new JobRequest.Builder(tag)
                .setExecutionWindow(200_000L, 400_000L)
                .build()
                .schedule();

        executeJob(jobId, Job.Result.SUCCESS);
        job.cancel();

        assertThat(manager().getAllJobRequestsForTag(tag)).isEmpty();

        assertThat(manager().getJobRequest(jobId)).isNull();
        assertThat(manager().getJobRequest(jobId, true)).isNull();

        assertThat(job.isCanceled()).isFalse();
        assertThat(onCancelCalled.get()).isEqualTo(0);
    }
}
