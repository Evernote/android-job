package com.evernote.android.job;

import android.support.annotation.NonNull;

import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.test.TestCat;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.RuntimeEnvironment;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobExecutionTest {

    private Set<Integer> cachedJobIds;

    @Before
    public void prepare() {
        JobManager.create(RuntimeEnvironment.application).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return new TestJob();
            }
        });
        cachedJobIds = new HashSet<>();
    }

    @After
    public void after() {
        for (Integer jobId : cachedJobIds) {
            JobManager.instance().getApi().getCachedProxy(RuntimeEnvironment.application).cancel(jobId);
        }

        JobManager.instance().cancelAll();
        JobManager.instance().destroy();
    }

    @Test
    public void testSimpleJob() throws Throwable {
        final int jobId = getBuilder()
                .setExecutionWindow(200_000L, 400_000L)
                .setPersisted(true)
                .build()
                .schedule();

        cachedJobIds.add(jobId);

        JobProxy.Common common = getCommon(jobId);
        JobRequest pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();

        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<Throwable> crash = new AtomicReference<>();
        new Thread() {
            @Override
            public void run() {
                try {
                    Thread.sleep(200);
                    assertThat(JobManager.instance().getJobRequest(jobId)).isNull();

                    JobRequest transientRequest = JobManager.instance().getJobRequest(jobId, true);
                    assertThat(transientRequest).isNotNull();
                    assertThat(transientRequest.isTransient()).isTrue();
                } catch (Throwable t) {
                    crash.set(t);
                }

                latch.countDown();
            }
        }.start();

        Job.Result result = common.executeJobRequest(pendingRequest);
        assertThat(result).isEqualTo(Job.Result.FAILURE);

        assertThat(JobManager.instance().getAllJobRequestsForTag(TestJob.TAG)).isEmpty();

        pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNull();

        latch.await(1, TimeUnit.SECONDS);
        if (crash.get() != null) {
            throw crash.get();
        }
    }

    @Test
    public void testPeriodicJob() throws Exception {
        int jobId = getBuilder()
                .setPeriodic(TimeUnit.MINUTES.toMillis(15))
                .setPersisted(true)
                .build()
                .schedule();

        cachedJobIds.add(jobId);

        JobProxy.Common common = getCommon(jobId);
        JobRequest pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();

        Job.Result result = common.executeJobRequest(pendingRequest);
        assertThat(result).isEqualTo(Job.Result.FAILURE);

        pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNull();

        assertThat(JobManager.instance().getAllJobRequestsForTag(TestJob.TAG)).hasSize(1);

        Thread.sleep(3_000);

        pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();
    }

    private JobRequest.Builder getBuilder() {
        return new JobRequest.Builder(TestJob.TAG);
    }

    private JobProxy.Common getCommon(int jobId) {
        return new JobProxy.Common(RuntimeEnvironment.application, new TestCat(), jobId);
    }

    private static final class TestJob extends Job {

        private static final String TAG = "tag";

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            try {
                Thread.sleep(1_000L);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            return Result.FAILURE;
        }
    }
}
