package com.evernote.android.job;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.PowerManager;
import android.test.mock.MockContext;

import com.evernote.android.job.test.DummyJobs;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@FixMethodOrder(MethodSorters.JVM)
@SuppressWarnings("deprecation")
public class JobRequirementTest {

    @Test
    public void verifyRequirementNetworkMeteredOnRoaming() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.METERED, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkMeteredOnMobile() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.METERED, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkMeteredOnWifi() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.METERED, true, ConnectivityManager.TYPE_WIFI, false);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkMeteredNoConnection() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.METERED, false, ConnectivityManager.TYPE_WIFI, false);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingOnRoaming() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.NOT_ROAMING, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingOnMobile() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.NOT_ROAMING, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingOnWifi() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.NOT_ROAMING, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingNoConnection() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.NOT_ROAMING, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredOnRoaming() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.UNMETERED, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredOnMobile() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.UNMETERED, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredOnWifi() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.UNMETERED, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredNoConnection() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.UNMETERED, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkConnectedOnRoaming() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkConnectedOnMobile() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkConnectedWifi() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkConnectedNoConnection() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkAnyOnRoaming() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.ANY, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkAnyOnMobile() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.ANY, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkAnyWifi() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.ANY, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkAnyNoConnection() {
        Job job = createMockedJob();
        setupNetworkRequirement(job, JobRequest.NetworkType.ANY, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementDeviceIdleIsIdle() {
        Job job = createMockedJob();
        setupDeviceIdle(job, true, true);
        assertThat(job.isRequirementDeviceIdleMet()).isTrue();
    }

    @Test
    public void verifyRequirementDeviceIdleIsNotIdle() {
        Job job = createMockedJob();
        setupDeviceIdle(job, true, false);
        assertThat(job.isRequirementDeviceIdleMet()).isFalse();
    }

    @Test
    public void verifyRequirementDeviceNoRequirement() {
        Job job = createMockedJob();
        setupDeviceIdle(job, false, false);
        assertThat(job.isRequirementDeviceIdleMet()).isTrue();

        setupDeviceIdle(job, false, true);
        assertThat(job.isRequirementDeviceIdleMet()).isTrue();
    }

    @Test
    public void verifyMeetsRequirementsAllMet() {
        Job job = createMockedJob();
        setupDeviceIdle(job, true, true);
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_WIFI, false);

        assertThat(job.isRequirementDeviceIdleMet()).isTrue();
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
        assertThat(job.meetsRequirements()).isTrue();
    }

    @Test
    public void verifyMeetsRequirementsOnlyIdle() {
        Job job = createMockedJob();
        setupDeviceIdle(job, true, true);
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, false, ConnectivityManager.TYPE_WIFI, false);

        assertThat(job.isRequirementDeviceIdleMet()).isTrue();
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
        assertThat(job.meetsRequirements()).isFalse();
    }

    @Test
    public void verifyMeetsRequirementsOnlyNetwork() {
        Job job = createMockedJob();
        setupDeviceIdle(job, true, false);
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_WIFI, false);

        assertThat(job.isRequirementDeviceIdleMet()).isFalse();
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
        assertThat(job.meetsRequirements()).isFalse();
    }

    @Test
    public void verifyMeetsRequirementsEnforcedIgnored() {
        Job job = createMockedJob();
        when(job.getParams().getRequest().requirementsEnforced()).thenReturn(false);
        setupDeviceIdle(job, true, false);
        setupNetworkRequirement(job, JobRequest.NetworkType.CONNECTED, false, ConnectivityManager.TYPE_WIFI, false);

        assertThat(job.isRequirementDeviceIdleMet()).isFalse();
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
        assertThat(job.meetsRequirements()).isFalse();
        assertThat(job.meetsRequirements(true)).isTrue();
    }

    private void setupNetworkRequirement(Job job, JobRequest.NetworkType requirement, boolean connected, int networkType, boolean roaming) {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(connected);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(connected);
        when(networkInfo.getType()).thenReturn(networkType);
        when(networkInfo.isRoaming()).thenReturn(roaming);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        when(job.getContext().getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        when(job.getParams().getRequest().requiredNetworkType()).thenReturn(requirement);
    }

    @SuppressWarnings("deprecation")
    private void setupDeviceIdle(Job job, boolean requirement, boolean deviceIdle) {
        PowerManager powerManager = mock(PowerManager.class);
        when(powerManager.isDeviceIdleMode()).thenReturn(deviceIdle);
        when(powerManager.isInteractive()).thenReturn(!deviceIdle);
        when(powerManager.isScreenOn()).thenReturn(!deviceIdle);
        when(powerManager.isInteractive()).thenReturn(!deviceIdle);

        when(job.getParams().getRequest().requiresDeviceIdle()).thenReturn(requirement);

        when(job.getContext().getSystemService(Context.POWER_SERVICE)).thenReturn(powerManager);
    }

    private Job createMockedJob() {
        Context context = mock(MockContext.class);

        JobRequest request = mock(JobRequest.class);
        Job.Params params = mock(Job.Params.class);
        when(params.getRequest()).thenReturn(request);

        Job job = spy(new DummyJobs.SuccessJob());
        when(job.getParams()).thenReturn(params);
        doReturn(context).when(job).getContext();

        return job;
    }
}
