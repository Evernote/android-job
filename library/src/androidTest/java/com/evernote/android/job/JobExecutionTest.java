package com.evernote.android.job;

import android.content.Context;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.facebook.stetho.Stetho;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class JobExecutionTest {

    private static Set<Integer> cachedJobIds;

    @BeforeClass
    public static void beforeClass() {
        Stetho.initializeWithDefaults(InstrumentationRegistry.getContext());

        JobManager.create(InstrumentationRegistry.getContext()).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return new TestJob();
            }
        });
        cachedJobIds = new HashSet<>();
    }

    @AfterClass
    public static void afterClass() {
        Context context = InstrumentationRegistry.getContext();
        for (Integer jobId : cachedJobIds) {
            JobManager.instance().getApi().getCachedProxy(context).cancel(jobId);
        }

        JobManager.instance().destroy();
    }

    @Before
    public void beforeTest() {
        JobManager.instance().cancelAll();
    }

    @After
    public void afterTest() {
        JobManager.instance().cancelAll();
    }

    @Test
    public void testSimpleJob() {
        final int jobId = getBuilder()
                .setExecutionWindow(200_000L, 400_000L)
                .setPersisted(true)
                .build()
                .schedule();

        cachedJobIds.add(jobId);

        JobProxy.Common common = getCommon(jobId);
        JobRequest pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();

        new Thread() {
            @Override
            public void run() {
                SystemClock.sleep(200);
                assertThat(JobManager.instance().getJobRequest(jobId)).isNull();

                JobRequest transientRequest = JobManager.instance().getJobRequest(jobId, true);
                assertThat(transientRequest).isNotNull();
                assertThat(transientRequest.isTransient()).isTrue();
            }
        }.start();

        Job.Result result = common.executeJobRequest(pendingRequest);
        assertThat(result).isEqualTo(Job.Result.FAILURE);

        assertThat(JobManager.instance().getAllJobRequestsForTag(TestJob.TAG)).isEmpty();

        pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNull();
    }

    @Test
    public void testPeriodicJob() {
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

        SystemClock.sleep(3_000);

        pendingRequest = common.getPendingRequest(true);
        assertThat(pendingRequest).isNotNull();
    }

    private JobRequest.Builder getBuilder() {
        return new JobRequest.Builder(TestJob.TAG);
    }

    private JobProxy.Common getCommon(int jobId) {
        return new JobProxy.Common(InstrumentationRegistry.getContext(), "JobExecutionTest", jobId);
    }

    private static final class TestJob extends Job {

        private static final String TAG = "tag";

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            SystemClock.sleep(1_000L);
            return Result.FAILURE;
        }
    }
}
