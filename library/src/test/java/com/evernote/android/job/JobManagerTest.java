package com.evernote.android.job;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.print.CatPrinter;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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

    @Test
    public void testSimultaneousCancel() throws Exception {
        final int threadCount = 5;
        final int jobCount = 25;

        CountDownLatch[] latches = new CountDownLatch[threadCount];
        for (int i = 0; i < latches.length; i++) {
            latches[i] = new CountDownLatch(1);

        }

        // that sucks, but the storage can't be injected to verify it better
        class TestPrinter implements CatPrinter {
            private final List<String> mMessages = new ArrayList<>();

            @Override
            public void println(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
                if (message.endsWith("canceling")) {
                    mMessages.add(message);
                }
            }
        }

        TestPrinter testPrinter = new TestPrinter();
        JobCat.addLogPrinter(testPrinter);

        for (int i = 0; i < jobCount; i++) {
            DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                    .setExecutionWindow(300_000, 400_000)
                    .build().schedule();
        }

        for (final CountDownLatch latch : latches) {
            new Thread() {
                @Override
                public void run() {
                    manager().cancelAll();
                    latch.countDown();
                }
            }.start();
        }

        for (CountDownLatch latch : latches) {
            latch.await(3, TimeUnit.SECONDS);
        }

        assertThat(testPrinter.mMessages).hasSize(25);
    }

    @Test
    public void testReusingBuilderObject() throws Exception {
        final int jobs = 20;

        JobRequest.Builder builder = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(300_000, 400_000);

        int previousJobId = -1;
        for (int i = 0; i < jobs; i++) {
            int jobId = builder.build().schedule();

            assertThat(jobId).isNotEqualTo(previousJobId);
            previousJobId = jobId;
        }

        assertThat(manager().getAllJobRequests()).hasSize(jobs);
    }

    @Test
    public void testReusingBuilderObjectWithMultipleThreads() throws Exception {
        final int jobs = 20;

        final JobRequest.Builder builder = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(300_000, 400_000);

        for (int i = 0; i < jobs; i++) {
            final CountDownLatch latch = new CountDownLatch(1);
            new Thread() {
                @Override
                public void run() {
                    builder.build().schedule();
                    latch.countDown();
                }
            }.start();
            latch.await(1, TimeUnit.SECONDS);
        }

        assertThat(manager().getAllJobRequests()).hasSize(jobs);
    }
}
