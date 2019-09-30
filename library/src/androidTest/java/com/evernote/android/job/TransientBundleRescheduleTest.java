package com.evernote.android.job;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@SuppressWarnings("SameParameterValue")
@RunWith(AndroidJUnit4.class)
@LargeTest
public class TransientBundleRescheduleTest {

    private JobManager mManager;

    private CountDownLatch mLatch;
    private Job.Params mParams;
    private int mCount;

    @Rule
    public PlatformJobManagerRule mJobManagerRule = new PlatformJobManagerRule();

    @Before
    public void prepare() {
        mManager = mJobManagerRule.getManager();
        mManager.addJobCreator(new TestJobCreator());
        mLatch = new CountDownLatch(1);
    }

    @Test
    public void testRescheduleHasTransientBundle() throws Exception {
        testOneOff(JobApi.V_21, 30, TimeUnit.SECONDS);
    }

    private void testOneOff(JobApi api, long wait, TimeUnit timeUnit) throws Exception {
        // ignore test if not supported
        assumeTrue(api.isSupported(ApplicationProvider.getApplicationContext()));

        JobConfig.forceApi(api);

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(500, 1_000)
                .setTransientExtras(createTransientBundle())
                .setBackoffCriteria(500, JobRequest.BackoffPolicy.LINEAR)
                .build()
                .schedule();

        JobRequest request = mManager.getJobRequest(jobId);
        assertThat(request).isNotNull();

        boolean scheduled = api.getProxy(ApplicationProvider.getApplicationContext()).isPlatformJobScheduled(request);
        assertThat(scheduled).isTrue();

        assertThat(mLatch.await(wait, timeUnit)).isTrue();

        Bundle extras = mParams.getTransientExtras();
        assertThat(extras).isNotNull();
        assertThat(extras.getString("Key")).isEqualTo("Value");
    }

    private final class TestJob extends Job {

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            Bundle extras = params.getTransientExtras();
            assertThat(extras).isNotNull();
            assertThat(extras.getString("Key")).isEqualTo("Value");

            if (++mCount < 3) {
                return Result.RESCHEDULE;
            }

            mParams = params;
            mLatch.countDown();
            return Result.SUCCESS;
        }
    }

    private final class TestJobCreator implements JobCreator {
        @Override
        public Job create(@NonNull String tag) {
            return new TestJob();
        }
    }

    private static Bundle createTransientBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("Key", "Value");
        return bundle;
    }
}
