package com.evernote.android.job;

import android.os.Build;

import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.annotation.Config;

import static org.assertj.core.api.Java6Assertions.assertThat;

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
}
