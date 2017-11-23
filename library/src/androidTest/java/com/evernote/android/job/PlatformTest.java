package com.evernote.android.job;

import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.evernote.android.job.gcm.PlatformGcmService;
import com.evernote.android.job.v14.PlatformAlarmService;
import com.evernote.android.job.v14.PlatformAlarmServiceExact;
import com.evernote.android.job.v21.PlatformJobService;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PlatformTest {

    private JobManager mManager;

    private TestJob mJob;

    @Rule
    public PlatformJobManagerRule mJobManagerRule = new PlatformJobManagerRule();

    @Before
    public void prepare() {
        mManager = mJobManagerRule.getManager();
        mManager.addJobCreator(new TestJobCreator());
    }

    @Test
    public void testApi14() throws Exception {
        testOneOff(JobApi.V_14);
    }

    @Test
    public void testApi19() throws Exception {
        testOneOff(JobApi.V_19);
    }

    @Test
    @Ignore
    public void testApiGcm() throws Exception {
        testOneOff(JobApi.GCM, 60, TimeUnit.SECONDS);
    }

    @Test
    public void testApi21() throws Exception {
        testOneOff(JobApi.V_21);
    }

    @Test
    public void testApi24() throws Exception {
        testOneOff(JobApi.V_24);
    }

    @Test
    public void testApi26() throws Exception {
        testOneOff(JobApi.V_26);
    }

    @Test
    public void testExactRealTime() throws Exception {
        testJobExact();
    }

    @Test
    public void testExactRtc() throws Exception {
        JobConfig.setForceRtc(true);
        testJobExact();
    }

    private void testJobExact() throws Exception {
        mJob = new TestJob(PlatformAlarmServiceExact.class);

        new JobRequest.Builder("tag")
                .setExact(1_000)
                .build()
                .schedule();

        mJob.verifyJob(12, TimeUnit.SECONDS);
    }

    @Test
    public void testStartNow() throws Exception {
        mJob = new TestJob(PlatformAlarmService.class);

        new JobRequest.Builder("tag")
                .startNow()
                .build()
                .schedule();

        mJob.verifyJob(3, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api) throws Exception {
        testOneOff(api, 15, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api, long wait, TimeUnit timeUnit) throws Exception {
        // ignore test if not supported
        assumeTrue(api.isSupported(InstrumentationRegistry.getTargetContext()));

        switch (api) {
            case V_14:
            case V_19:
                mJob = new TestJob(PlatformAlarmService.class);
                break;
            case GCM:
                mJob = new TestJob(PlatformGcmService.class);
                break;
            case V_21:
            case V_24:
            case V_26:
                mJob = new TestJob(PlatformJobService.class);
                break;
            default:
                throw new IllegalStateException("not implemented");
        }

        JobConfig.forceApi(api);

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(1_000, 2_000)
                .build()
                .schedule();

        mJob.verifyJob(wait, timeUnit);

        // give the platform implementation some time to clean everything up
        Thread.sleep(300L);

        assertThat(mManager.getJob(jobId)).isNotNull();
        assertThat(mManager.getJobRequest(jobId)).isNull();
    }

    private final class TestJob extends Job {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final Class<? extends Context> mExpectedContext;

        private Context mContext;
        private Params mParams;

        private TestJob(Class<? extends Context> expectedContext) {
            mExpectedContext = expectedContext;
        }

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            mContext = getContext();
            mParams = params;

            mLatch.countDown();
            return Result.SUCCESS;
        }

        private void verifyJob(long wait, TimeUnit timeUnit) throws InterruptedException {
            assertThat(mJob.mLatch.await(wait, timeUnit)).isTrue();

            assertThat(mContext).isInstanceOf(mExpectedContext);

            Bundle extras = mParams.getTransientExtras();
            assertThat(extras).isNotNull();
        }
    }

    private final class TestJobCreator implements JobCreator {
        @Override
        public Job create(@NonNull String tag) {
            return mJob;
        }
    }
}
