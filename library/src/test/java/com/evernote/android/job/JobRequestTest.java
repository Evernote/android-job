package com.evernote.android.job;

import android.support.annotation.NonNull;

import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.JobApi;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import org.junit.After;
import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.RuntimeEnvironment;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobRequestTest {

    @Before
    public void prepare() {
        JobManager.create(RuntimeEnvironment.application).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return new TestJob();
            }
        });
    }

    @After
    public void after() {
        JobManager.instance().cancelAll();
        JobManager.instance().destroy();
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
        long interval = JobRequest.MIN_INTERVAL * 5;
        JobRequest request = getBuilder()
                .setPeriodic(interval)
                .setExtras(new PersistableBundleCompat())
                .setPersisted(true)
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(TestJob.TAG);
        assertThat(request.isPersisted()).isTrue();
        assertThat(request.getIntervalMs()).isEqualTo(interval);
        assertThat(request.getFlexMs()).isEqualTo(interval);
        assertThat(request.isPeriodic()).isTrue();
        assertThat(request.isFlexSupport()).isFalse();

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
    public void testFlex() {
        JobManager.instance().forceApi(JobApi.V_14);

        long interval = JobRequest.MIN_INTERVAL * 5;
        long flex = JobRequest.MIN_FLEX * 5;
        JobRequest request = getBuilder()
                .setPeriodic(interval, flex)
                .build();

        JobManager.instance().schedule(request);

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(TestJob.TAG);
        assertThat(request.getIntervalMs()).isEqualTo(interval);
        assertThat(request.getFlexMs()).isEqualTo(flex);
        assertThat(request.isPeriodic()).isTrue();
        assertThat(request.isFlexSupport()).isTrue();
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
                .setPeriodic(JobRequest.MIN_INTERVAL - 1)
                .build();
    }

    @Test(expected = Exception.class)
    public void testPeriodicTooLittleFlex() {
        getBuilder()
                .setPeriodic(JobRequest.MIN_FLEX - 1)
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
