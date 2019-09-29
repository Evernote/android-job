/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;
import androidx.core.net.ConnectivityManagerCompat;

import com.evernote.android.job.JobRequest;

/**
 * Helper for checking the device state.
 *
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class Device {

    private Device() {
        // no op
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    public static BatteryStatus getBatteryStatus(Context context) {
        Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (intent == null) {
            // should not happen
            return BatteryStatus.DEFAULT;
        }

        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        float batteryPct = level / (float) scale;

        // 0 is on battery
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        boolean charging = plugged == BatteryManager.BATTERY_PLUGGED_AC
                || plugged == BatteryManager.BATTERY_PLUGGED_USB
                || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS);

        return new BatteryStatus(charging, batteryPct);
    }

    @SuppressWarnings("deprecation")
    public static boolean isIdle(Context context) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            /*
             * isDeviceIdleMode() is a very strong requirement and could cause a job
             * to be never run. isDeviceIdleMode() returns true in doze mode, but jobs
             * are delayed until the device leaves doze mode
             */
            return powerManager.isDeviceIdleMode() || !powerManager.isInteractive();
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
            return !powerManager.isInteractive();
        } else {
            return !powerManager.isScreenOn();
        }
    }

    /**
     * Checks the network condition of the device and returns the best type. If the device
     * is connected to a WiFi and mobile network at the same time, then it would assume
     * that the connection is unmetered because of the WiFi connection.
     *
     * @param context Any context, e.g. the application context.
     * @return The current network type of the device.
     */
    @NonNull
    @SuppressWarnings("deprecation")
    public static JobRequest.NetworkType getNetworkType(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo;
        try {
            networkInfo = connectivityManager.getActiveNetworkInfo();
        } catch (Throwable t) {
            return JobRequest.NetworkType.ANY;
        }

        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            return JobRequest.NetworkType.ANY;
        }

        boolean metered = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager);
        if (!metered) {
            return JobRequest.NetworkType.UNMETERED;
        }

        if (isRoaming(connectivityManager, networkInfo)) {
            return JobRequest.NetworkType.CONNECTED;
        } else {
            return JobRequest.NetworkType.NOT_ROAMING;
        }
    }

    @SuppressWarnings("deprecation")
    private static boolean isRoaming(ConnectivityManager connectivityManager, NetworkInfo networkInfo) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            return networkInfo.isRoaming();
        }

        try {
            NetworkCapabilities capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.getActiveNetwork());
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING);
        } catch (Exception e) {
            return networkInfo.isRoaming();
        }
    }

    public static boolean isStorageLow() {
        // figure this out
        return false;
    }
}
