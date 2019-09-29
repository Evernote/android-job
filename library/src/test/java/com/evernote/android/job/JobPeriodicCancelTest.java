package com.evernote.android.job;

import android.os.Build;
import androidx.annotation.NonNull;

import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.test.TestLogger;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.annotation.Config;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.assertj.core.api.Java6Assertions.fail;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobPeriodicCancelTest extends BaseJobManagerTest {

    private PeriodicJob mJob;

    @Before
    public void setup() {
        mJob = new PeriodicJob();
        manager().addJobCreator(new JobCreator() {
            @Override
            public Job create(@NonNull String tag) {
                return mJob;
            }
        });
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void verifyPeriodicFlexNotRescheduledN() {
        runJobAndCancelAllDuringExecution(true, false);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void verifyPeriodicNotRescheduledN() {
        runJobAndCancelAllDuringExecution(false, false);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void verifyPeriodicFlexNotRescheduledNSwap() {
        runJobAndCancelAllDuringExecution(true, true);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void verifyPeriodicNotRescheduledNSwao() {
        runJobAndCancelAllDuringExecution(false, true);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.M)
    public void verifyPeriodicFlexNotRescheduledM() {
        runJobAndCancelAllDuringExecution(true, false);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.M)
    public void verifyPeriodicNotRescheduledM() {
        runJobAndCancelAllDuringExecution(false, false);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.M)
    public void verifyPeriodicFlexNotRescheduledMSwap() {
        runJobAndCancelAllDuringExecution(true, true);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.M)
    public void verifyPeriodicNotRescheduledMSwap() {
        runJobAndCancelAllDuringExecution(false, true);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void verifyPeriodicFlexNotRescheduledK() {
        runJobAndCancelAllDuringExecution(true, false);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void verifyPeriodicNotRescheduledK() {
        runJobAndCancelAllDuringExecution(false, false);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void verifyPeriodicFlexNotRescheduledKSwap() {
        runJobAndCancelAllDuringExecution(true, true);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void verifyPeriodicNotRescheduledKSwap() {
        runJobAndCancelAllDuringExecution(false, true);
        assertThat(manager().getAllJobRequests()).isEmpty();
    }

    private void runJobAndCancelAllDuringExecution(boolean flex, boolean swapCancelOrder) {
        try {
            final int jobId = new JobRequest.Builder("any")
                .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(flex ? 5 : 15))
                .build()
                .schedule();

            final JobProxy.Common common = new JobProxy.Common(context(), TestLogger.INSTANCE, jobId);
            final JobRequest request = common.getPendingRequest(true, true);
            assertThat(request).isNotNull();

            final CountDownLatch waitFinishExecution = new CountDownLatch(1);
            new Thread() {
                @Override
                public void run() {
                    common.executeJobRequest(request, null);
                    waitFinishExecution.countDown();
                }
            }.start();

            assertThat(mJob.mStartedLatch.await(30, TimeUnit.SECONDS)).isTrue();

            if (swapCancelOrder) {
                manager().getJob(request.getJobId()).cancel(); // this might be called in onStopJob()
                manager().cancelAll();
            } else {
                manager().cancelAll();
                manager().getJob(request.getJobId()).cancel(); // this might be called in onStopJob()
            }

            mJob.mBlockingLatch.countDown();
            assertThat(waitFinishExecution.await(30, TimeUnit.SECONDS)).isTrue();


        } catch (InterruptedException e) {
            fail("Shouldn't happen");
        }
    }

    private static class PeriodicJob extends Job {

        private final CountDownLatch mStartedLatch = new CountDownLatch(1);
        private final CountDownLatch mBlockingLatch = new CountDownLatch(1);

        @NonNull
        @Override
        protected Result onRunJob(@NonNull Params params) {
            mStartedLatch.countDown();

            try {
                mBlockingLatch.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException ignored) {
            }
            return Result.SUCCESS;
        }
    }
}
