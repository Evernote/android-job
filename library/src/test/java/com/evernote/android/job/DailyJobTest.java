package com.evernote.android.job;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

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
}
