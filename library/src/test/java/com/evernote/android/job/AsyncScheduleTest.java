package com.evernote.android.job;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.test.TestClock;
import com.evernote.android.job.util.JobLogger;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class AsyncScheduleTest extends BaseJobManagerTest {

    @Before
    public void prepare() {
        JobConfig.setExecutorService(Executors.newSingleThreadExecutor());
    }

    @Test
    public void verifyScheduleAsync() throws Exception {
        DummyJobs.createBuilder(DummyJobs.SuccessJob.class).setExecutionWindow(300_000, 400_000).build().scheduleAsync();
        waitUntilScheduled();
        assertThat(manager().getAllJobRequests()).hasSize(1);
    }

    @Test
    public void verifyJobIdAsync() throws Exception {
        final AtomicInteger jobId = new AtomicInteger(-2);
        DummyJobs.createBuilder(DummyJobs.SuccessJob.class).setExecutionWindow(300_000, 400_000).build()
                .scheduleAsync(new JobRequest.JobScheduledCallback() {
                    @Override
                    public void onJobScheduled(int id, @NonNull String tag, @Nullable Exception exception) {
                        jobId.set(id);
                    }
                });

        waitUntilScheduled();
        assertThat(manager().getJobRequest(jobId.get())).isNotNull();
    }

    @Test
    public void verifyErrorAsync() throws Exception {
        JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.schedule(any(JobInfo.class))).thenThrow(new RuntimeException("test"));
        when(context().getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(jobScheduler);

        final AtomicReference<Exception> reference = new AtomicReference<>();

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .scheduleAsync(new JobRequest.JobScheduledCallback() {
                    @Override
                    public void onJobScheduled(int jobId, @NonNull String tag, @Nullable Exception exception) {
                        assertThat(jobId).isEqualTo(JOB_ID_ERROR);
                        reference.set(exception);
                    }
                });

        waitUntilScheduled();
        assertThat(reference.get()).isInstanceOf(RuntimeException.class);
    }

    @Test
    public void verifyErrorIsLoggedInDefaultHandler() throws Exception {
        final String errorMessage = "test ABC";

        JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.schedule(any(JobInfo.class))).thenThrow(new RuntimeException(errorMessage));
        when(context().getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(jobScheduler);

        final AtomicInteger specificError = new AtomicInteger(0);
        JobConfig.addLogger(new JobLogger() {
            @Override
            public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
                if (t != null && t.getMessage().equals(errorMessage)) {
                    specificError.incrementAndGet();
                }
            }
        });

        new JobRequest.Builder("tag").setExecutionWindow(200_000, 300_000).build().scheduleAsync();
        waitUntilScheduled();

        assertThat(specificError.get()).isEqualTo(1);
    }

    @Test
    public void verifyJobIdAsyncDailyJob() throws Exception {
        JobConfig.setClock(new TestClock());

        final AtomicInteger jobId = new AtomicInteger(-2);
        DailyJob.scheduleAsync(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), 1000, 2000, new JobRequest.JobScheduledCallback() {
            @Override
            public void onJobScheduled(int id, @NonNull String tag, @Nullable Exception exception) {
                jobId.set(id);
            }
        });

        waitUntilScheduled();
        assertThat(manager().getJobRequest(jobId.get())).isNotNull();
    }

    @Test
    public void verifyErrorAsyncDailyJob() throws Exception {
        JobConfig.setClock(new TestClock());

        JobScheduler jobScheduler = mock(JobScheduler.class);
        when(jobScheduler.schedule(any(JobInfo.class))).thenThrow(new RuntimeException("test"));
        when(context().getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(jobScheduler);

        final AtomicReference<Exception> reference = new AtomicReference<>();

        DailyJob.scheduleAsync(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), 1000, 2000, new JobRequest.JobScheduledCallback() {
            @Override
            public void onJobScheduled(int jobId, @NonNull String tag, @Nullable Exception exception) {
                assertThat(jobId).isEqualTo(JOB_ID_ERROR);
                reference.set(exception);
            }
        });

        waitUntilScheduled();
        assertThat(reference.get()).isInstanceOf(RuntimeException.class);
    }

    private void waitUntilScheduled() throws Exception {
        final CountDownLatch latch = new CountDownLatch(1);
        JobConfig.getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                latch.countDown();
            }
        });
        latch.await(50, TimeUnit.SECONDS);
    }
}
