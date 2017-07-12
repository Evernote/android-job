package com.evernote.android.job;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
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
public class JobRequirementTest {

    @Test
    public void verifyRequirementNetworkMeteredOnRoaming() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.METERED, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkMeteredOnMobile() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.METERED, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkMeteredOnWifi() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.METERED, true, ConnectivityManager.TYPE_WIFI, false);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkMeteredNoConnection() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.METERED, false, ConnectivityManager.TYPE_WIFI, false);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingOnRoaming() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.NOT_ROAMING, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingOnMobile() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.NOT_ROAMING, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingOnWifi() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.NOT_ROAMING, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkNotRoamingNoConnection() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.NOT_ROAMING, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredOnRoaming() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.UNMETERED, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredOnMobile() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.UNMETERED, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredOnWifi() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.UNMETERED, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkUnmeteredNoConnection() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.UNMETERED, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkConnectedOnRoaming() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkConnectedOnMobile() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkConnectedWifi() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.CONNECTED, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkConnectedNoConnection() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.CONNECTED, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isFalse();
    }

    @Test
    public void verifyRequirementNetworkAnyOnRoaming() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.ANY, true, ConnectivityManager.TYPE_MOBILE, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkAnyOnMobile() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.ANY, true, ConnectivityManager.TYPE_MOBILE, false);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkAnyWifi() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.ANY, true, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    @Test
    public void verifyRequirementNetworkAnyNoConnection() {
        Job job = setupNetworkRequirement(JobRequest.NetworkType.ANY, false, ConnectivityManager.TYPE_WIFI, true);
        assertThat(job.isRequirementNetworkTypeMet()).isTrue();
    }

    private Job setupNetworkRequirement(JobRequest.NetworkType requirement, boolean connected, int networkType, boolean roaming) {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(connected);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(connected);
        when(networkInfo.getType()).thenReturn(networkType);
        when(networkInfo.isRoaming()).thenReturn(roaming);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        JobRequest request = mock(JobRequest.class);
        when(request.requiredNetworkType()).thenReturn(requirement);

        Job.Params params = mock(Job.Params.class);
        when(params.getRequest()).thenReturn(request);

        Job job = spy(new DummyJobs.SuccessJob());
        when(job.getParams()).thenReturn(params);
        doReturn(context).when(job).getContext();

        return job;
    }
}
