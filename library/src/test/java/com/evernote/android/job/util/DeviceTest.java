package com.evernote.android.job.util;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.test.mock.MockContext;

import com.evernote.android.job.JobRequest;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@FixMethodOrder(MethodSorters. JVM)
@SuppressWarnings("deprecation")
public class DeviceTest {

    @Test
    public void testNetworkStateNotConnectedWithNullNetworkInfo() {
        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.ANY);
    }

    @Test
    public void testNetworkStateNotConnected() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(false);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(false);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.ANY);
    }

    @Test
    public void testNetworkStateUnmeteredWifi() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(true);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.UNMETERED);
    }

    @Test
    public void testNetworkStateMeteredNotRoaming() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(true);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_MOBILE);
        when(networkInfo.isRoaming()).thenReturn(false);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.NOT_ROAMING);
    }

    @Test
    public void testNetworkStateRoaming() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(true);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_MOBILE);
        when(networkInfo.isRoaming()).thenReturn(true);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.CONNECTED);
    }

    @Test
    public void testNetworkStateWifiAndMobile() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(true);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        when(networkInfo.isRoaming()).thenReturn(false);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.UNMETERED);
    }

    @Test
    public void testNetworkStateWifiAndRoaming() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(true);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_WIFI);
        when(networkInfo.isRoaming()).thenReturn(true);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.UNMETERED);
    }

    @Test
    public void testNetworkStateVpn() {
        NetworkInfo networkInfo = mock(NetworkInfo.class);
        when(networkInfo.isConnected()).thenReturn(true);
        when(networkInfo.isConnectedOrConnecting()).thenReturn(true);
        when(networkInfo.getType()).thenReturn(ConnectivityManager.TYPE_VPN);

        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenReturn(networkInfo);

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.NOT_ROAMING);
    }

    @Test
    public void testPlatformBug() {
        ConnectivityManager connectivityManager = mock(ConnectivityManager.class);
        when(connectivityManager.getActiveNetworkInfo()).thenThrow(new NullPointerException());

        Context context = mock(MockContext.class);
        when(context.getSystemService(Context.CONNECTIVITY_SERVICE)).thenReturn(connectivityManager);

        assertThat(Device.getNetworkType(context)).isEqualTo(JobRequest.NetworkType.ANY);
    }
}
