package com.evernote.android.job;

import android.database.sqlite.SQLiteDatabase;
import android.os.Build;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class JobConfigTest extends BaseJobManagerTest {

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void verifyReset() {
        assertThat(JobConfig.isApiEnabled(JobApi.V_19)).isTrue(); // default
        JobConfig.setApiEnabled(JobApi.V_19, false);
        assertThat(JobConfig.isApiEnabled(JobApi.V_19)).isFalse(); // did change

        assertThat(JobConfig.isAllowSmallerIntervalsForMarshmallow()).isFalse(); // default
        JobConfig.setAllowSmallerIntervalsForMarshmallow(true);
        assertThat(JobConfig.isAllowSmallerIntervalsForMarshmallow()).isTrue(); // did change

        JobConfig.reset();
        assertThat(JobConfig.isApiEnabled(JobApi.V_19)).isTrue(); // default
        assertThat(JobConfig.isAllowSmallerIntervalsForMarshmallow()).isFalse(); // default
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void verifyMinIntervalChanged() {
        assertThat(JobRequest.getMinInterval()).isEqualTo(JobRequest.MIN_INTERVAL);
        assertThat(JobRequest.getMinFlex()).isEqualTo(JobRequest.MIN_FLEX);

        JobConfig.setAllowSmallerIntervalsForMarshmallow(true);

        assertThat(JobRequest.getMinInterval()).isLessThan(JobRequest.MIN_INTERVAL);
        assertThat(JobRequest.getMinFlex()).isLessThan(JobRequest.MIN_FLEX);
    }

    @Test(expected = IllegalStateException.class)
    @Config(sdk = Build.VERSION_CODES.N)
    public void verifyMinIntervalCantBeChangedAfterN() {
        JobConfig.setAllowSmallerIntervalsForMarshmallow(true);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.O)
    public void verifyApi26Supported() {
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_26);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.N)
    public void verifyApi24Supported() {
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_24);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.LOLLIPOP)
    public void verifyApi21Supported() {
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_21);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.KITKAT)
    public void verifyApi19Supported() {
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_19);
    }

    @Test
    @Config(sdk = Build.VERSION_CODES.O)
    public void verifyApiDisabled() {
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_26);

        JobConfig.setApiEnabled(JobApi.V_26, false);
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_24);

        JobConfig.setApiEnabled(JobApi.V_24, false);
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_21);

        JobConfig.setApiEnabled(JobApi.V_21, false);
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_19);

        JobConfig.setApiEnabled(JobApi.V_19, false);
        assertThat(JobApi.getDefault(context())).isEqualTo(JobApi.V_14);
    }

    @Test
    public void verifyForceApiDisabledOtherApis() {
        JobApi forcedApi = JobApi.GCM;
        for (JobApi api : JobApi.values()) {
            assertThat(JobConfig.isApiEnabled(api)).isTrue();
        }

        JobConfig.forceApi(forcedApi);

        for (JobApi api : JobApi.values()) {
            assertThat(JobConfig.isApiEnabled(api)).isEqualTo(api == forcedApi);
        }
    }

    @Test
    public void verifyJobIdOffset() {
        assertThat(JobConfig.getJobIdOffset()).isEqualTo(0);
        assertThat(manager().getJobStorage().getMaxJobId()).isEqualTo(0);

        int jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(200_000L, 400_000L)
                .build()
                .schedule();

        assertThat(jobId).isEqualTo(1);

        JobConfig.setJobIdOffset(100);
        assertThat(JobConfig.getJobIdOffset()).isEqualTo(100);

        jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(200_000L, 400_000L)
                .build()
                .schedule();

        assertThat(jobId).isEqualTo(101);

        JobConfig.setJobIdOffset(0);
        assertThat(JobConfig.getJobIdOffset()).isEqualTo(0);

        jobId = DummyJobs.createBuilder(DummyJobs.SuccessJob.class)
                .setExecutionWindow(200_000L, 400_000L)
                .build()
                .schedule();

        assertThat(jobId).isEqualTo(102);
    }

    @Test(expected = IllegalArgumentException.class)
    public void verifyJobIdOffsetUpperBound() {
        JobConfig.setJobIdOffset(2147480000 - 500 + 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void verifyJobIdOffsetLowerBound() {
        JobConfig.setJobIdOffset(-1);
    }

    @Test
    public void verifyJobIdOffsetBounds() {
        JobConfig.setJobIdOffset(0);
        JobConfig.setJobIdOffset(2147480000 - 500);
    }

    @Test
    public void verifyCloseDatabase() {
        JobConfig.setCloseDatabase(false);
        assertThat(JobConfig.isCloseDatabase()).isFalse(); // default

        SQLiteDatabase database = mock(SQLiteDatabase.class);

        JobStorage storage = manager().getJobStorage();
        storage.injectDatabase(database);

        storage.get(1);
        verify(database, times(1)).query(anyString(), nullable(String[].class), anyString(),
                any(String[].class), nullable(String.class), nullable(String.class), nullable(String.class));
        verify(database, times(0)).close();

        JobConfig.setCloseDatabase(true);

        storage.get(1);
        verify(database, times(2)).query(anyString(), nullable(String[].class), anyString(),
                any(String[].class), nullable(String.class), nullable(String.class), nullable(String.class));
        verify(database, times(1)).close();
    }
}
