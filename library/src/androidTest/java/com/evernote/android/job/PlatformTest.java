package com.evernote.android.job;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.evernote.android.job.gcm.PlatformGcmService;
import com.evernote.android.job.v14.PlatformAlarmService;
import com.evernote.android.job.v21.PlatformJobService;

import org.junit.Before;
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
    public JobManagerRule mJobManagerRule = new JobManagerRule();

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
    public void testApiGcm() throws Exception {
        testOneOff(JobApi.GCM, 40, TimeUnit.SECONDS);
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
    public void testExact() throws Exception {
        mJob = new TestJob(PlatformAlarmService.class);

        new JobRequest.Builder("tag")
                .setExact(2_000)
                .build()
                .schedule();

        mJob.mLatch.await(4, TimeUnit.SECONDS);
    }

    @Test
    public void testStartNow() throws Exception {
        mJob = new TestJob(PlatformAlarmService.class);

        new JobRequest.Builder("tag")
                .startNow()
                .build()
                .schedule();

        mJob.mLatch.await(1, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api) throws Exception {
        testOneOff(api, 10, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api, long wait, TimeUnit timeUnit) throws Exception {
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

        // ignore test if not supported
        assumeTrue(api.isSupported(InstrumentationRegistry.getTargetContext()));

        JobConfig.forceApi(api);

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(2_000, 3_000)
                .build()
                .schedule();

        mJob.mLatch.await(wait, timeUnit);

        // give the platform implementation some time to clean everything up
        Thread.sleep(300L);

        assertThat(mManager.getJob(jobId)).isNotNull();
        assertThat(mManager.getJobRequest(jobId)).isNull();
    }

    private final class TestJob extends Job {

        private final CountDownLatch mLatch = new CountDownLatch(1);
        private final Class<? extends Context> mExpectedContext;

        private TestJob(Class<? extends Context> expectedContext) {
            mExpectedContext = expectedContext;
        }

        @NonNull
        @Override
        protected Result onRunJob(Params params) {
            assertThat(getContext()).isInstanceOf(mExpectedContext);

            mLatch.countDown();
            return Result.SUCCESS;
        }
    }

    private final class TestJobCreator implements JobCreator {
        @Override
        public Job create(String tag) {
            return mJob;
        }
    }
}
