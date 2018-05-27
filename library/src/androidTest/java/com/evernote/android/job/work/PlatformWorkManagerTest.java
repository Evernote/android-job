package com.evernote.android.job.work;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.test.InstrumentationRegistry;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.PlatformWorkManagerRule;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import androidx.work.State;
import androidx.work.WorkManager;
import androidx.work.WorkStatus;

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
    public void testCancel() {
        int jobId = new JobRequest.Builder(TAG)
                .setExecutionWindow(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(5))
                .build()
                .schedule();

        JobRequest request = mWorkManagerRule.getManager().getJobRequest(jobId);
        JobProxyWorkManager jobProxyWorkManager = new JobProxyWorkManager(InstrumentationRegistry.getTargetContext());
        assertThat(jobProxyWorkManager.isPlatformJobScheduled(request)).isTrue();

        String tag = JobProxyWorkManager.createTag(jobId);
        List<WorkStatus> statuses = getWorkStatus(tag);

        assertThat(statuses).isNotNull().hasSize(1);
        assertThat(statuses.get(0).getState()).isEqualTo(State.ENQUEUED);

        mWorkManagerRule.getManager().cancel(jobId);
        assertThat(getWorkStatus(tag).get(0).getState()).isEqualTo(State.CANCELLED);
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

        mWorkManagerRule.initTestWorkManager();

        jobId = builder.build().schedule();
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

        mWorkManagerRule.initTestWorkManager();

        int jobId = new JobRequest.Builder(TAG)
                .setExecutionWindow(TimeUnit.HOURS.toMillis(4), TimeUnit.HOURS.toMillis(5))
                .build()
                .schedule();

        String tag = JobProxyWorkManager.createTag(jobId);
        State state = getWorkStatus(tag).get(0).getState();

        assertThat(executed.get()).isTrue();
        assertThat(state).isEqualTo(State.SUCCEEDED);
    }

    @SuppressWarnings("ConstantConditions")
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
        List<WorkStatus> statuses = getWorkStatus(tag);

        assertThat(statuses).isNotNull().hasSize(1);
        assertThat(statuses.get(0).getState()).isEqualTo(State.ENQUEUED);

        mWorkManagerRule.getManager().cancelAllForTag(TAG);
        assertThat(getWorkStatus(tag).get(0).getState()).isEqualTo(State.CANCELLED);
    }

    private List<WorkStatus> getWorkStatus(String tag) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<List<WorkStatus>> reference = new AtomicReference<>();

        final LiveData<List<WorkStatus>> liveData = WorkManager.getInstance().getStatusesByTag(tag);
        liveData.observeForever(new Observer<List<WorkStatus>>() {
            @Override
            public void onChanged(@Nullable List<WorkStatus> workStatuses) {
                if (reference.get() == null) {
                    reference.set(workStatuses);
                }
                liveData.removeObserver(this);
                latch.countDown();
            }
        });

        try {
            assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
        return reference.get();
    }
}
