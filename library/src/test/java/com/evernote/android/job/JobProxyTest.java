package com.evernote.android.job;

import android.app.AlarmManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;

import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.mockito.ArgumentMatcher;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowAlarmManager;

import java.lang.reflect.Field;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobProxyTest {

    @After
    public void cleanup() {
        try {
            JobManager instance = JobManager.instance();
            instance.destroy();
        } catch (Exception ignored) {
        }
    }

    @Test
    @Config(sdk = 21)
    public void verifyRecoverWithJobScheduler() throws Exception {
        Context context = BaseJobManagerTest.createMockContext();
        Context applicationContext = context.getApplicationContext();

        JobScheduler scheduler = (JobScheduler) applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
        when(applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null, null, scheduler);

        JobManager.create(context);

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .schedule();

        assertThat(scheduler.getAllPendingJobs()).isNotEmpty();
    }

    @Test
    @Config(sdk = 21)
    public void verifyRecoverWithAlarmManager() throws Exception {
        Context context = BaseJobManagerTest.createMockContext();
        Context applicationContext = context.getApplicationContext();

        AlarmManager alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);

        when(applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null);

        JobManager.create(context);

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .schedule();

        verifyAlarmCount(alarmManager, 1);
    }

    @Test
    @Config(sdk = 21)
    public void verifyNoRecoverWithAlarmManager() throws Exception {
        Context context = BaseJobManagerTest.createMockContext();
        Context applicationContext = context.getApplicationContext();

        AlarmManager alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);

        when(applicationContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(null);
        when(applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null);

        JobManager.create(context);

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .schedule();

        verifyAlarmCount(alarmManager, 0);
    }

    @Test
    @Config(sdk = 19)
    public void verifyRecoverWithAlarmManagerApi19() throws Exception {
        Context context = BaseJobManagerTest.createMockContext();
        Context applicationContext = context.getApplicationContext();

        AlarmManager alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);

        when(applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(null, null, alarmManager);

        JobManager.create(context);

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .schedule();

        verifyAlarmCount(alarmManager, 1);
    }

    @Test
    @Config(sdk = 19)
    public void verifyNoRecoverWithAlarmManagerApi19() throws Exception {
        Context context = BaseJobManagerTest.createMockContext();
        Context applicationContext = context.getApplicationContext();

        AlarmManager alarmManager = (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE);

        when(applicationContext.getSystemService(Context.ALARM_SERVICE)).thenReturn(null);

        JobManager.create(context);

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .schedule();

        verifyAlarmCount(alarmManager, 0);
    }

    private void verifyAlarmCount(AlarmManager alarmManager, int count) throws NoSuchFieldException, IllegalAccessException {
        Field declaredField = alarmManager.getClass().getDeclaredField("__robo_data__");
        declaredField.setAccessible(true);
        ShadowAlarmManager shadowAlarmManager = (ShadowAlarmManager) declaredField.get(alarmManager);
        assertThat(shadowAlarmManager.getScheduledAlarms()).hasSize(count);
    }

    @Test
    @Config(sdk = 21)
    public void verifyRecoverWithoutBootPermissionJobScheduler() throws Exception {
        Context context = BaseJobManagerTest.createMockContext();
        Context applicationContext = context.getApplicationContext();

        JobScheduler scheduler = spy((JobScheduler) applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE));
        when(applicationContext.getSystemService(Context.JOB_SCHEDULER_SERVICE)).thenReturn(scheduler);

        doThrow(new IllegalArgumentException("Error: requested job be persisted without holding RECEIVE_BOOT_COMPLETED permission."))
                .when(scheduler)
                .schedule(argThat(new ArgumentMatcher<JobInfo>() {
                    @Override
                    public boolean matches(JobInfo argument) {
                        return argument.isPersisted();
                    }
                }));

        JobManager.create(context);

        new JobRequest.Builder("tag")
                .setExecutionWindow(200_000, 300_000)
                .build()
                .schedule();

        assertThat(scheduler.getAllPendingJobs()).hasSize(1);
        assertThat(scheduler.getAllPendingJobs().get(0).isPersisted()).isFalse();
    }
}
