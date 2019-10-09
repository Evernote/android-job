package com.evernote.android.job.work;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import androidx.work.WorkInfo;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.PlatformWorkManagerRule;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class PlatformWorkManagerTest {

    private static final String TAG = "my-tag";

    @Rule
    public PlatformWorkManagerRule mWorkManagerRule = new PlatformWorkManagerRule();

    @Test
    public void testConstraintsOneOff() {
        testConstraints(new JobRequest.Builder(TAG)
                .setExecutionWindow(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(5))
                .setBackoffCriteria(TimeUnit.MINUTES.toMillis(4), JobRequest.BackoffPolicy.EXPONENTIAL)
        );
    }

    @Test
    public void testConstraintsPeriodic() {
        testConstraints(new JobRequest.Builder(TAG)
                .setPeriodic(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(2))
        );
    }

    @Test
    @SuppressWarnings("ConstantConditions")
    public void testCancel() {
        int jobId = new JobRequest.Builder(TAG)
                .setExecutionWindow(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(5))
                .build()
                .schedule();

        JobRequest request = mWorkManagerRule.getManager().getJobRequest(jobId);
        JobProxyWorkManager jobProxyWorkManager = new JobProxyWorkManager(ApplicationProvider.getApplicationContext());
        assertThat(jobProxyWorkManager.isPlatformJobScheduled(request)).isTrue();

        String tag = JobProxyWorkManager.createTag(jobId);
        List<WorkInfo> statuses = mWorkManagerRule.getWorkStatus(tag);

        assertThat(statuses).isNotNull().hasSize(1);
        assertThat(statuses.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);

        mWorkManagerRule.getManager().cancel(jobId);
        assertThat(mWorkManagerRule.getWorkStatus(tag).get(0).getState()).isEqualTo(WorkInfo.State.CANCELLED);
        assertThat(jobProxyWorkManager.isPlatformJobScheduled(request)).isFalse();
    }

    @Test
    public void testTransientExtras() {
        Bundle extras = new Bundle();
        extras.putInt("key", 5);

        JobRequest.Builder builder = new JobRequest.Builder(TAG)
                .setExecutionWindow(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(5))
                .setTransientExtras(extras);

        int jobId = builder.build().schedule();

        Bundle bundle = TransientBundleHolder.getBundle(jobId);
        assertThat(bundle).isNotNull();
        assertThat(bundle.getInt("key")).isEqualTo(5);

        mWorkManagerRule.getManager().cancel(jobId);
        assertThat(TransientBundleHolder.getBundle(jobId)).isNull();

        jobId = builder.build().schedule();
        mWorkManagerRule.runJob(JobProxyWorkManager.createTag(jobId));

        assertThat(TransientBundleHolder.getBundle(jobId)).isNull();
    }

    @Test
    public void testExecution() {
        final AtomicBoolean executed = new AtomicBoolean(false);
        final Job job = new Job() {
            @NonNull
            @Override
            protected Result onRunJob(@NonNull Params params) {
                executed.set(true);
                return Result.SUCCESS;
            }
        };

        mWorkManagerRule.getManager().addJobCreator(new JobCreator() {
            @Nullable
            @Override
            public Job create(@NonNull String tag) {
                if (tag.equals(TAG)) {
                    return job;
                } else {
                    return null;
                }
            }
        });

        int jobId = new JobRequest.Builder(TAG)
                .setExecutionWindow(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(5))
                .build()
                .schedule();

        String tag = JobProxyWorkManager.createTag(jobId);
        mWorkManagerRule.runJob(tag);

        WorkInfo.State state = mWorkManagerRule.getWorkStatus(tag).get(0).getState();

        assertThat(executed.get()).isTrue();
        assertThat(state).isEqualTo(WorkInfo.State.SUCCEEDED);
    }

    private void testConstraints(JobRequest.Builder builder) {
        int jobId = builder
                .setRequiredNetworkType(JobRequest.NetworkType.METERED)
                .setRequiresBatteryNotLow(true)
                .setRequiresCharging(true)
                .setRequiresDeviceIdle(true)
                .setRequiresStorageNotLow(true)
                .build()
                .schedule();

        String tag = JobProxyWorkManager.createTag(jobId);
        List<WorkInfo> statuses = mWorkManagerRule.getWorkStatus(tag);

        assertThat(statuses).isNotNull().hasSize(1);
        assertThat(statuses.get(0).getState()).isEqualTo(WorkInfo.State.ENQUEUED);

        mWorkManagerRule.getManager().cancelAllForTag(TAG);
        assertThat(mWorkManagerRule.getWorkStatus(tag).get(0).getState()).isEqualTo(WorkInfo.State.CANCELLED);
    }
}
