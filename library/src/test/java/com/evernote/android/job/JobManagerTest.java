package com.evernote.android.job;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.JobLogger;

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
    public void testScheduleIsNotIdempotentWithNewRequest() throws Exception {
        JobRequest.Builder builder = DummyJobs.createBuilder(DummyJobs.SuccessJob.class).setExecutionWindow(300_000, 400_000);
        JobRequest request1 = builder.build();
        int jobId = request1.schedule();

        long scheduledAt = request1.getScheduledAt();
        assertThat(scheduledAt).isGreaterThan(0L);

        Thread.sleep(10);

        JobRequest request2 = builder.build();
        int newJobId = request2.schedule();

        assertThat(newJobId).isGreaterThan(jobId);
        assertThat(request2.getScheduledAt()).isGreaterThan(scheduledAt);
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
        class TestPrinter implements JobLogger {
            private final List<String> mMessages = new ArrayList<>();

            @Override
            public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
                if (message.endsWith("canceling")) {
                    mMessages.add(message);
                }
            }
        }

        TestPrinter testPrinter = new TestPrinter();
        JobConfig.addLogger(testPrinter);

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

        final CountDownLatch latch = new CountDownLatch(jobs);

        for (int i = 0; i < jobs; i++) {
            new Thread() {
                @Override
                public void run() {
                    builder.build().schedule();
                    latch.countDown();
                }
            }.start();
        }

        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        assertThat(manager().getAllJobRequests()).hasSize(jobs);
    }

    @Test
    public void testCancelAndEditTwice() throws Exception {
        final JobRequest.Builder builder = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
                .setExecutionWindow(300_000, 400_000);

        int jobId = builder.build().schedule();
        builder.build().schedule();

        assertThat(manager().getAllJobRequests()).hasSize(2);

        JobRequest request = manager().getJobRequest(jobId);
        assertThat(request.requiredNetworkType()).isEqualTo(JobRequest.NetworkType.UNMETERED);

        JobRequest.Builder cancelBuilder1 = request.cancelAndEdit();
        JobRequest.Builder cancelBuilder2 = request.cancelAndEdit();

        cancelBuilder1
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .build()
                .schedule();

        assertThat(manager().getJobRequest(jobId).requiredNetworkType()).isEqualTo(JobRequest.NetworkType.CONNECTED);

        JobRequest builtRequest = cancelBuilder2.build();
        assertThat(builtRequest.requiredNetworkType()).isEqualTo(JobRequest.NetworkType.UNMETERED);

        builtRequest.schedule();

        assertThat(manager().getAllJobRequests()).hasSize(2);
        assertThat(manager().getJobRequest(jobId).requiredNetworkType()).isEqualTo(JobRequest.NetworkType.UNMETERED);
    }

    @Test
    public void testJobIdIncremented() throws Exception {
        assertThat(manager().getJobStorage().getMaxJobId()).isEqualTo(0);
        assertThat(manager().getAllJobRequests()).isEmpty();

        DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(300_000, 400_000)
                .build().schedule();

        assertThat(manager().getJobStorage().getMaxJobId()).isEqualTo(1);

        assertThat(manager().getJobStorage().nextJobId()).isEqualTo(2);
        assertThat(manager().getJobStorage().nextJobId()).isEqualTo(3);

        // Once an ID has been used it's not allowed to use it again, even if the job wasn't saved for some reason
        assertThat(manager().getJobStorage().getMaxJobId()).isEqualTo(3);

        context().getSharedPreferences(JobStorage.PREF_FILE_NAME, Context.MODE_PRIVATE).edit().clear().apply();

        // if something goes wrong with the pref file, use the highest value from the database
        assertThat(manager().getJobStorage().getMaxJobId()).isEqualTo(1);
    }

    @Test
    public void testJobIdRollover() throws Exception {
        JobConfig.setJobIdOffset(10);

        context().getSharedPreferences(JobStorage.PREF_FILE_NAME, Context.MODE_PRIVATE).edit()
                .putInt(JobStorage.JOB_ID_COUNTER, JobIdsInternal.RESERVED_JOB_ID_RANGE_START - 2)
                .apply();

        assertThat(manager().getJobStorage().nextJobId()).isEqualTo(JobIdsInternal.RESERVED_JOB_ID_RANGE_START - 1);
        assertThat(manager().getJobStorage().nextJobId()).isEqualTo(11);
        assertThat(manager().getJobStorage().nextJobId()).isEqualTo(12);
    }

    @SuppressWarnings("ConstantConditions")
    @Test
    public void testCancelAndEdit() {
        int id = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExact(10_000)
                .build().schedule();

        int newId = manager().getJobRequest(id).cancelAndEdit().build().schedule();

        JobRequest request = manager().getJobRequest(newId);
        assertThat(request.getEndMs()).isGreaterThan(9_000);
    }

    @Test
    public void testWithUpdateCurrentRaceCondition() throws InterruptedException {
        final JobRequest.Builder builder = new JobRequest.Builder("any").setExecutionWindow(300_000, 400_000).setUpdateCurrent(true);

        final CountDownLatch latchWait = new CountDownLatch(2);
        final CountDownLatch latchStart = new CountDownLatch(1);
        final CountDownLatch latchFinished = new CountDownLatch(2);

        for (int i = 0; i < 5; i++) {
            new Thread() {
                @Override
                public void run() {
                    latchWait.countDown();
                    try {
                        latchStart.await();
                    } catch (InterruptedException e) {
                        throw new IllegalStateException(e);
                    }
                    manager().schedule(builder.build());
                    latchFinished.countDown();
                }
            }.start();
        }

        assertThat(latchWait.await(10, TimeUnit.SECONDS)).isTrue();
        latchStart.countDown();
        assertThat(latchFinished.await(10, TimeUnit.SECONDS)).isTrue();

        assertThat(manager().getAllJobRequests().size()).isEqualTo(1);
    }
}
