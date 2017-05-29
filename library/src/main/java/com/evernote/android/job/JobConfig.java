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
package com.evernote.android.job;

import android.os.Build;
import android.support.annotation.NonNull;

import com.evernote.android.job.util.JobCat;

import java.util.EnumMap;

/**
 * A global configuration for the job library.
 * <br>
 * <br>
 * See {@link JobCat} for settings to enable/disable logging.
 *
 * @author rwondratschek
 */
@SuppressWarnings({"WeakerAccess", "unused"})
public final class JobConfig {

    private JobConfig() {
        throw new UnsupportedOperationException();
    }

    private static final EnumMap<JobApi, Boolean> ENABLED_APIS;
    private static final JobCat CAT = new JobCat("JobConfig");

    private static volatile boolean allowSmallerIntervals;
    private static volatile boolean forceAllowApi14 = false;

    static {
        ENABLED_APIS = new EnumMap<>(JobApi.class);
        for (JobApi api : JobApi.values()) {
            ENABLED_APIS.put(api, Boolean.TRUE);
        }
    }

    /**
     * @return Whether the given API is enabled. By default all APIs are enabled, although the current
     * device may not support it.
     */
    public static boolean isApiEnabled(@NonNull JobApi api) {
        return ENABLED_APIS.get(api);
    }

    /**
     * <b>WARNING:</b> Please use this method carefully. It's only meant to be used for testing purposes
     * and could break how the library works.
     * <br>
     * <br>
     * Programmatic switch to enable or disable the given API. This only has an impact for new scheduled jobs.
     *
     * @param api The API which should be enabled or disabled.
     * @param enabled Whether the API should be enabled or disabled.
     */
    public static void setApiEnabled(@NonNull JobApi api, boolean enabled) {
        ENABLED_APIS.put(api, enabled);
        CAT.w("setApiEnabled - %s, %b", api, enabled);
    }

    /**
     * <b>WARNING:</b> You shouldn't call this method. It only exists for testing and debugging
     * purposes. The {@link JobManager} automatically decides which API suits best for a {@link Job}.
     *
     * @param api The {@link JobApi} which will be used for future scheduled JobRequests.
     */
    public static void forceApi(@NonNull JobApi api) {
        for (JobApi jobApi : JobApi.values()) {
            ENABLED_APIS.put(jobApi, jobApi == api);
        }
        CAT.w("forceApi - %s", api);
    }

    /**
     * Checks whether a smaller interval and flex are allowed for periodic jobs. That's helpful
     * for testing purposes.
     *
     * @return Whether a smaller interval and flex than the minimum values are allowed for periodic jobs
     * are allowed. The default value is {@code false}.
     */
    public static boolean isAllowSmallerIntervalsForMarshmallow() {
        return allowSmallerIntervals && Build.VERSION.SDK_INT < Build.VERSION_CODES.N;
    }

    /**
     * Option to override the minimum period and minimum flex for periodic jobs. This is useful for testing
     * purposes. This method only works for Android M and earlier. Later versions throw an exception.
     *
     * @param allowSmallerIntervals Whether a smaller interval and flex than the minimum values are allowed
     *                              for periodic jobs are allowed. The default value is {@code false}.
     */
    public static void setAllowSmallerIntervalsForMarshmallow(boolean allowSmallerIntervals) {
        if (allowSmallerIntervals && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            throw new IllegalStateException("This method is only allowed to call on Android M or earlier");
        }
        JobConfig.allowSmallerIntervals = allowSmallerIntervals;
    }

    /**
     * On some devices for some reason all broadcast receiver and services are disabled. This library
     * cannot work properly in this case. This switch allows to use the AlarmManager as fallback even
     * in such a weird state.
     *
     * <br>
     * <br>
     *
     * If the value is {@code true}, then this suppresses the {@link JobManagerCreateException} during
     * the creation of the job manager.
     *
     * @param forceAllowApi14 Whether API 14 should be used as fallback in all scenarios. The default
     *                        value is {@code false}.
     */
    public static void setForceAllowApi14(boolean forceAllowApi14) {
        JobConfig.forceAllowApi14 = forceAllowApi14;
    }

    /**
     * @return Whether API 14 should be used as fallback in all scenarios. The default value is {@code false}.
     */
    public static boolean isForceAllowApi14() {
        return forceAllowApi14;
    }

    /**
     * Resets all adjustments in the config.
     */
    public static void reset() {
        for (JobApi api : JobApi.values()) {
            ENABLED_APIS.put(api, Boolean.TRUE);
        }
        allowSmallerIntervals = false;
        forceAllowApi14 = false;
    }
}
