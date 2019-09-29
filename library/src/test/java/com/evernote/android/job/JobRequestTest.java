package com.evernote.android.job;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.JobLogger;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobRequestTest extends BaseJobManagerTest {

    @Test
    public void testSimpleJob() {
        JobRequest request = getBuilder()
                .setExecutionWindow(2_000L, 3_000L)
                .setBackoffCriteria(4_000, JobRequest.BackoffPolicy.LINEAR)
                .setExtras(new PersistableBundleCompat())
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(DummyJobs.SuccessJob.TAG);
        assertThat(request.getStartMs()).isEqualTo(2_000L);
        assertThat(request.getEndMs()).isEqualTo(3_000L);
        assertThat(request.getBackoffMs()).isEqualTo(4_000L);
        assertThat(request.getBackoffPolicy()).isEqualTo(JobRequest.BackoffPolicy.LINEAR);
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
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(DummyJobs.SuccessJob.TAG);
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
        JobConfig.forceApi(JobApi.V_14);

        long interval = JobRequest.MIN_INTERVAL * 5;
        long flex = JobRequest.MIN_FLEX * 5;
        JobRequest request = getBuilder()
                .setPeriodic(interval, flex)
                .build();

        JobManager.instance().schedule(request);

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(DummyJobs.SuccessJob.TAG);
        assertThat(request.getIntervalMs()).isEqualTo(interval);
        assertThat(request.getFlexMs()).isEqualTo(flex);
        assertThat(request.isPeriodic()).isTrue();
        assertThat(request.isFlexSupport()).isTrue();
    }

    @Test
    public void verifyStartNow() {
        JobRequest request = getBuilder()
                .setBackoffCriteria(4_000, JobRequest.BackoffPolicy.LINEAR)
                .setExtras(new PersistableBundleCompat())
                .startNow()
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(DummyJobs.SuccessJob.TAG);
        assertThat(request.getStartMs()).isEqualTo(JobRequest.START_NOW);
        assertThat(request.getEndMs()).isEqualTo(JobRequest.START_NOW);
        assertThat(request.getBackoffMs()).isEqualTo(4_000L);
        assertThat(request.getBackoffPolicy()).isEqualTo(JobRequest.BackoffPolicy.LINEAR);
        assertThat(request.getExtras()).isNotNull();
        assertThat(request.isExact()).isTrue();

        assertThat(request.getIntervalMs()).isZero();
        assertThat(request.isPeriodic()).isFalse();
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
                .setExact(2_000L)
                .build();

        assertThat(request.getJobId()).isGreaterThan(0);
        assertThat(request.getTag()).isEqualTo(DummyJobs.SuccessJob.TAG);
        assertThat(request.getStartMs()).isEqualTo(2_000L);
        assertThat(request.getEndMs()).isEqualTo(2_000L);
        assertThat(request.getBackoffMs()).isEqualTo(4_000L);
        assertThat(request.getBackoffPolicy()).isEqualTo(JobRequest.BackoffPolicy.LINEAR);
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

    @Test
    public void testWarningWhenTooFarInTheFuture() {
        class TestPrinter implements JobLogger {
            private final List<String> mMessages = new ArrayList<>();

            @Override
            public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
                mMessages.add(message);
            }
        }

        TestPrinter testPrinter = new TestPrinter();
        JobConfig.addLogger(testPrinter);

        getBuilder().setExecutionWindow(TimeUnit.DAYS.toMillis(366), TimeUnit.DAYS.toMillis(367)).build();
        getBuilder().setExact(TimeUnit.DAYS.toMillis(366)).build();

        JobConfig.removeLogger(testPrinter);

        assertThat(testPrinter.mMessages).containsSubsequence(
                "Warning: job with tag SuccessJob scheduled over a year in the future",
                "Warning: job with tag SuccessJob scheduled over a year in the future"
        );
    }

    private JobRequest.Builder getBuilder() {
        return DummyJobs.createBuilder(DummyJobs.SuccessJob.class);
    }
}
