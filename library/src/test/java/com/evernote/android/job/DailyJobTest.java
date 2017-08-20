package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.Calendar;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class DailyJobTest extends BaseJobManagerTest {

    @Test
    public void verifyScheduleInNextHour() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        long start = TimeUnit.HOURS.toMillis(hour + 1);
        long end = start + TimeUnit.MINUTES.toMillis(30);

        DailyJob.schedule(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), start, end);

        assertThat(manager().getAllJobRequests()).hasSize(1);

        JobRequest request = manager().getAllJobRequests().iterator().next();

        assertThat(request.getStartMs()).isLessThan(TimeUnit.HOURS.toMillis(1));
        assertThat(request.getEndMs()).isLessThan(TimeUnit.HOURS.toMillis(2));
    }

    @Test
    public void verifyScheduleOverMidnight() {
        long start = TimeUnit.HOURS.toMillis(24) - 1L;
        long end = 1L;

        DailyJob.schedule(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), start, end);

        assertThat(manager().getAllJobRequests()).hasSize(1);
        JobRequest request = manager().getAllJobRequests().iterator().next();

        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        long maxStart = TimeUnit.HOURS.toMillis(24 - hour);
        assertThat(request.getStartMs()).isLessThan(maxStart);
        assertThat(request.getEndMs()).isLessThan(maxStart + 3);
    }

    @Test(expected = IllegalArgumentException.class)
    public void verifyTooLargeValue() {
        long start = TimeUnit.HOURS.toMillis(24);
        long end = 1L;

        DailyJob.schedule(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), start, end);
    }

    @Test
    public void verifyScheduledAtMidnight() {
        long start = 0;
        long end = 1L;

        DailyJob.schedule(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), start, end);
        assertThat(manager().getAllJobRequests()).hasSize(1);
    }

    @Test
    public void verifyHasExtras() {
        long start = 0;
        long end = 1L;

        int jobId = DailyJob.schedule(DummyJobs.createBuilder(DummyJobs.SuccessJob.class), start, end);
        JobRequest request = manager().getJobRequest(jobId);

        assertThat(request).isNotNull();
        assertThat(request.getExtras().getLong(DailyJob.EXTRA_START_MS, -1)).isEqualTo(0L);
        assertThat(request.getExtras().getLong(DailyJob.EXTRA_END_MS, -1)).isEqualTo(1L);
        assertThat(request.getExtras().size()).isEqualTo(2);
    }

    @Test
    public void verifyExtraValuesAreOverwritten() {
        long start = 0;
        long end = 1L;

        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putLong("something", 9L); // make sure this value is not overwritten
        extras.putLong(DailyJob.EXTRA_START_MS, 9L); // make sure they're overwritten
        extras.putLong(DailyJob.EXTRA_END_MS, 9L);

        int jobId = DailyJob.schedule(DummyJobs.createBuilder(DummyJobs.SuccessJob.class).setExtras(extras), start, end);
        JobRequest request = manager().getJobRequest(jobId);

        assertThat(request).isNotNull();
        assertThat(request.getExtras().getLong(DailyJob.EXTRA_START_MS, -1)).isEqualTo(0L);
        assertThat(request.getExtras().getLong(DailyJob.EXTRA_END_MS, -1)).isEqualTo(1L);
        assertThat(request.getExtras().size()).isEqualTo(3);
    }
}
