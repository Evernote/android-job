package com.evernote.android.job;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class TransientBundleTest {

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
    public void testExact() throws Exception {
        mJob = new TestJob();

        new JobRequest.Builder("tag")
                .setExact(1_000)
                .setTransientExtras(createTransientBundle())
                .build()
                .schedule();

        mJob.verifyJob(12, TimeUnit.SECONDS);
    }

    @Test
    public void testStartNow() throws Exception {
        mJob = new TestJob();

        new JobRequest.Builder("tag")
                .startNow()
                .setTransientExtras(createTransientBundle())
                .build()
                .schedule();

        mJob.verifyJob(3, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api) throws Exception {
        testOneOff(api, 15, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api, long wait, TimeUnit timeUnit) throws Exception {
        mJob = new TestJob();

        // ignore test if not supported
        assumeTrue(api.isSupported(ApplicationProvider.getApplicationContext()));

        JobConfig.forceApi(api);

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(1_000, 2_000)
                .setTransientExtras(createTransientBundle())
                .build()
                .schedule();

        JobRequest request = mManager.getJobRequest(jobId);
        assertThat(request).isNotNull();

        boolean scheduled = api.getProxy(ApplicationProvider.getApplicationContext()).isPlatformJobScheduled(request);
        assertThat(scheduled).isTrue();

        mJob.verifyJob(wait, timeUnit);
    }

    private final class TestJob extends Job {

        private final CountDownLatch mLatch = new CountDownLatch(1);

        private Params mParams;

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            mParams = params;

            mLatch.countDown();
            return Result.SUCCESS;
        }

        private void verifyJob(long wait, TimeUnit timeUnit) throws InterruptedException {
            assertThat(mJob.mLatch.await(wait, timeUnit)).isTrue();

            Bundle extras = mParams.getTransientExtras();
            assertThat(extras).isNotNull();
            assertThat(extras.getString("Key")).isEqualTo("Value");
        }
    }

    private final class TestJobCreator implements JobCreator {
        @Override
        public Job create(@NonNull String tag) {
            return mJob;
        }
    }

    private static Bundle createTransientBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("Key", "Value");
        return bundle;
    }
}
