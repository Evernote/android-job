package com.evernote.android.job.v21;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;

import com.evernote.android.job.JobApi;
import com.evernote.android.job.JobConfig;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.PlatformJobManagerRule;
import com.evernote.android.job.v14.PlatformAlarmServiceExact;

import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@SuppressWarnings("ConstantConditions")
@RunWith(AndroidJUnit4.class)
@FixMethodOrder(MethodSorters.JVM)
public class TransientBundleCompatTest {

    @Rule
    public PlatformJobManagerRule mJobManagerRule = new PlatformJobManagerRule();

    @Test
    public void verifyAlarmIsCanceled() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        JobConfig.forceApi(JobApi.V_21);

        int jobId = scheduleJob();

        final Intent intent = PlatformAlarmServiceExact.createIntent(context(), jobId, null);
        PendingIntent pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNotNull();

        mJobManagerRule.getManager().cancel(jobId);

        pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNull();
    }

    @Test
    public void verifyAlarmIsCanceledAfterStart() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        JobConfig.forceApi(JobApi.V_21);

        int jobId = scheduleJob();

        final Intent intent = PlatformAlarmServiceExact.createIntent(context(), jobId, null);
        PendingIntent pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNotNull();

        boolean started = TransientBundleCompat.startWithTransientBundle(context(), mJobManagerRule.getManager().getJobRequest(jobId));
        assertThat(started).isTrue();

        pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNull();
    }

    @Test
    public void verifyAlarmNotCanceledForPeriodicAfterStart() throws Exception {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        JobConfig.forceApi(JobApi.V_21);

        Bundle extras = new Bundle();
        extras.putString("key", "value");

        int jobId = new JobRequest.Builder("tag")
                .setPeriodic(TimeUnit.DAYS.toMillis(1))
                .setTransientExtras(extras)
                .build()
                .schedule();

        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).isNotNull().isNotEmpty();

        assertThat(mJobManagerRule.getManager().getJobRequest(jobId).isTransient()).isTrue();

        final Intent intent = PlatformAlarmServiceExact.createIntent(context(), jobId, null);
        PendingIntent pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNotNull();

        boolean started = TransientBundleCompat.startWithTransientBundle(context(), mJobManagerRule.getManager().getJobRequest(jobId));
        assertThat(started).isTrue();

        pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNotNull();
    }

    @Test
    public void verifyNativeImplementationIsUsedWithO() throws Exception {
        // ignore test if not supported
        assumeTrue(JobApi.V_26.isSupported(InstrumentationRegistry.getTargetContext()));
        JobConfig.forceApi(JobApi.V_26);

        int jobId = scheduleJob();

        final Intent intent = PlatformAlarmServiceExact.createIntent(context(), jobId, null);
        PendingIntent pendingIntent = PendingIntent.getService(context(), jobId, intent, PendingIntent.FLAG_NO_CREATE);
        assertThat(pendingIntent).isNull();
    }

    private Context context() {
        return InstrumentationRegistry.getContext();
    }

    private int scheduleJob() {
        Bundle extras = new Bundle();
        extras.putString("key", "value");

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(10_000, 20_000)
                .setTransientExtras(extras)
                .build()
                .schedule();

        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).isNotNull().isNotEmpty();

        assertThat(mJobManagerRule.getManager().getJobRequest(jobId).isTransient()).isTrue();

        return jobId;
    }
}
