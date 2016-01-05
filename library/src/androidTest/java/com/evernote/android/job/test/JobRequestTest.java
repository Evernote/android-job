package com.evernote.android.job.test;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class JobRequestTest {

    @BeforeClass
    public static void createJobManager() {
        JobManager.create(InstrumentationRegistry.getContext()).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return new TestJob();
            }
        });
    }

    @Test
    public void testSimpleJob() {
        JobRequest request = getBuilder()
                .setExecutionWindow(2_000L, 3_000L)
                .setBackoffCriteria(4_000, JobRequest.BackoffPolicy.LINEAR)
                .setExtras(new PersistableBundleCompat())
                .setPersisted(true)
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(TestJob.TAG);
        assertThat(request.getStartMs()).isEqualTo(2_000L);
        assertThat(request.getEndMs()).isEqualTo(3_000L);
        assertThat(request.getBackoffMs()).isEqualTo(4_000L);
        assertThat(request.getBackoffPolicy()).isEqualTo(JobRequest.BackoffPolicy.LINEAR);
        assertThat(request.isPersisted()).isTrue();
        assertThat(request.getExtras()).isNotNull();

        assertThat(request.getIntervalMs()).isZero();
        assertThat(request.isExact()).isFalse();
        assertThat(request.isPeriodic()).isFalse();
        assertThat(request.requiredNetworkType()).isEqualTo(JobRequest.DEFAULT_NETWORK_TYPE);
        assertThat(request.requirementsEnforced()).isFalse();
        assertThat(request.requiresCharging()).isFalse();
        assertThat(request.requiresDeviceIdle()).isFalse();
    }

    @Test
    public void testPeriodic() {
        JobRequest request = getBuilder()
                .setPeriodic(60_000L)
                .setExtras(new PersistableBundleCompat())
                .setPersisted(true)
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(TestJob.TAG);
        assertThat(request.isPersisted()).isTrue();
        assertThat(request.getIntervalMs()).isEqualTo(60_000L);
        assertThat(request.isPeriodic()).isTrue();

        assertThat(request.getStartMs()).isNegative();
        assertThat(request.getEndMs()).isNegative();
        assertThat(request.getBackoffMs()).isEqualTo(JobRequest.DEFAULT_BACKOFF_MS);
        assertThat(request.getBackoffPolicy()).isEqualTo(JobRequest.DEFAULT_BACKOFF_POLICY);
        assertThat(request.getExtras()).isNotNull();
        assertThat(request.isExact()).isFalse();
        assertThat(request.requiredNetworkType()).isEqualTo(JobRequest.DEFAULT_NETWORK_TYPE);
        assertThat(request.requirementsEnforced()).isFalse();
        assertThat(request.requiresCharging()).isFalse();
        assertThat(request.requiresDeviceIdle()).isFalse();
    }

    @Test
    public void testExact() {
        JobRequest request = getBuilder()
                .setBackoffCriteria(4_000, JobRequest.BackoffPolicy.LINEAR)
                .setExtras(new PersistableBundleCompat())
                .setPersisted(true)
                .setExact(2_000L)
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(TestJob.TAG);
        assertThat(request.getStartMs()).isEqualTo(2_000L);
        assertThat(request.getEndMs()).isEqualTo(2_000L);
        assertThat(request.getBackoffMs()).isEqualTo(4_000L);
        assertThat(request.getBackoffPolicy()).isEqualTo(JobRequest.BackoffPolicy.LINEAR);
        assertThat(request.isPersisted()).isTrue();
        assertThat(request.getExtras()).isNotNull();
        assertThat(request.isExact()).isTrue();

        assertThat(request.getIntervalMs()).isZero();
        assertThat(request.isPeriodic()).isFalse();
        assertThat(request.requiredNetworkType()).isEqualTo(JobRequest.DEFAULT_NETWORK_TYPE);
        assertThat(request.requirementsEnforced()).isFalse();
        assertThat(request.requiresCharging()).isFalse();
        assertThat(request.requiresDeviceIdle()).isFalse();
    }

    @Test(expected = Exception.class)
    public void testNoConstraints() {
        getBuilder().build();
    }

    @Test(expected = Exception.class)
    public void testExecutionWindow() {
        getBuilder()
                .setExecutionWindow(3_000L, 2_000L)
                .build();
    }

    @Test(expected = Exception.class)
    public void testPeriodicTooLittleInterval() {
        getBuilder()
                .setPeriodic(59_999L)
                .build();
    }

    @Test(expected = Exception.class)
    public void testPeriodicNoBackoffCriteria() {
        getBuilder()
                .setPeriodic(60_000L)
                .setBackoffCriteria(20_000L, JobRequest.BackoffPolicy.LINEAR)
                .build();
    }

    @Test(expected = Exception.class)
    public void testPeriodicNoExecutionWindow() {
        getBuilder()
                .setExecutionWindow(3_000L, 4_000L)
                .setPeriodic(60_000L)
                .build();
    }

    @Test(expected = Exception.class)
    public void testExactNotPeriodic() {
        getBuilder()
                .setExact(4_000L)
                .setPeriodic(60_000L)
                .build();
    }

    @Test(expected = Exception.class)
    public void testExactNoExactTime() {
        getBuilder()
                .setExact(4_000L)
                .setExecutionWindow(3_000L, 4_000L)
                .build();
    }

    @Test(expected = Exception.class)
    public void testExactNoDeviceIdle() {
        getBuilder()
                .setExact(4_000L)
                .setRequiresDeviceIdle(true)
                .build();
    }

    @Test(expected = Exception.class)
    public void testExactNoCharging() {
        getBuilder()
                .setExact(4_000L)
                .setRequiresCharging(true)
                .build();
    }

    @Test(expected = Exception.class)
    public void testExactNoNetworkType() {
        getBuilder()
                .setExact(4_000L)
                .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                .build();
    }

    @Test(expected = Exception.class)
    public void testExactNoRequirementsEnforced() {
        getBuilder()
                .setExact(4_000L)
                .setRequirementsEnforced(true)
                .build();
    }

    private JobRequest.Builder getBuilder() {
        return new JobRequest.Builder(TestJob.TAG);
    }

    private static final class TestJob extends Job {

        private static final String TAG = "tag";

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            return Result.FAILURE;
        }
    }
}
