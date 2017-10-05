/*
 * Copyright 2007-present Evernote Corporation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.android.job.util;

import android.annotation.TargetApi;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.v4.net.ConnectivityManagerCompat;

import com.evernote.android.job.JobRequest;

/**
 * Helper for checking the device state.
 *
 * @author rwondratschek
 */
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
    public static JobRequest.NetworkType getNetworkType(@NonNull Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
        if (networkInfo == null || !networkInfo.isConnectedOrConnecting()) {
            return JobRequest.NetworkType.ANY;
        }

        boolean metered = ConnectivityManagerCompat.isActiveNetworkMetered(connectivityManager);
        if (!metered) {
            return JobRequest.NetworkType.UNMETERED;
        }

        if (networkInfo.isRoaming()) {
            return JobRequest.NetworkType.CONNECTED;
        } else {
            return JobRequest.NetworkType.NOT_ROAMING;
        }
    }

    public static boolean isStorageLow() {
        // figure this out
        return false;
    }
}
