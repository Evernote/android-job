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

import android.app.AlarmManager;
import android.app.Service;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.os.Build;
import android.support.annotation.NonNull;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobManagerCreateException;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.gcm.JobProxyGcm;
import com.evernote.android.job.util.Device;
import com.evernote.android.job.v14.JobProxy14;
import com.evernote.android.job.v14.PlatformAlarmReceiver;
import com.evernote.android.job.v14.PlatformAlarmService;
import com.evernote.android.job.v19.JobProxy19;
import com.evernote.android.job.v21.JobProxy21;
import com.evernote.android.job.v21.PlatformJobService;
import com.evernote.android.job.v24.JobProxy24;
import com.evernote.android.job.v26.JobProxy26;
import com.google.android.gms.gcm.GcmNetworkManager;

import java.util.List;

/**
 * All available APIs.
 *
 * @author rwondratschek
 */
public enum JobApi {
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_26(true, false, true),
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_24(true, false, false),
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_21(true, true, false),
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_19(true, true, true),
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_14(false, true, true),
    /**
     * Uses the {@link GcmNetworkManager} for scheduling jobs.
     */
    GCM(true, false, true);

    private static final String JOB_SCHEDULER_PERMISSION = "android.permission.BIND_JOB_SERVICE";

    private static volatile boolean forceAllowApi14 = false;

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
     * @param forceAllowApi14 Whether API 14 should be used as fallback in all scenarios. The default value
     *                   is {@code false}.
     * @deprecated In version 1.2.0 this method will be moved to the Config class.
     */
    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    public static void setForceAllowApi14(boolean forceAllowApi14) {
        JobApi.forceAllowApi14 = forceAllowApi14;
    }

    /**
     * @return Whether API 14 should be used as fallback in all scenarios. The default value is {@code false}.
     *
     * @deprecated In version 1.2.0 this method will be moved to the Config class.
     */
    @Deprecated
    public static boolean isForceAllowApi14() {
        return forceAllowApi14;
    }

    private volatile JobProxy mCachedProxy;

    private final boolean mSupportsExecutionWindow;
    private final boolean mFlexSupport;
    private final boolean mSupportsTransientJobs;

    JobApi(boolean supportsExecutionWindow, boolean flexSupport, boolean supportsTransientJobs) {
        mSupportsExecutionWindow = supportsExecutionWindow;
        mFlexSupport = flexSupport;
        mSupportsTransientJobs = supportsTransientJobs;
    }

    /*package*/ boolean supportsExecutionWindow() {
        return mSupportsExecutionWindow;
    }

    /*package*/ boolean isFlexSupport() {
        return mFlexSupport;
    }

    /*package*/ boolean supportsTransientJobs() {
        return mSupportsTransientJobs;
    }

    public boolean isSupported(Context context) {
        switch (this) {
            case V_26:
                return Device.isAtLeastO() && isServiceEnabled(context, PlatformJobService.class);
            case V_24:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N && isServiceEnabledAndHasPermission(context, PlatformJobService.class, JOB_SCHEDULER_PERMISSION);
            case V_21:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP && isServiceEnabledAndHasPermission(context, PlatformJobService.class, JOB_SCHEDULER_PERMISSION);
            case V_19:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT && isServiceEnabled(context, PlatformAlarmService.class)
                        && isBroadcastEnabled(context, PlatformAlarmReceiver.class);
            case V_14:
                return forceAllowApi14
                        || (isServiceEnabled(context, PlatformAlarmService.class) && isBroadcastEnabled(context, PlatformAlarmReceiver.class));
            case GCM:
                return GcmAvailableHelper.isGcmApiSupported(context);
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    @NonNull
    private JobProxy createProxy(Context context) {
        switch (this) {
            case V_26:
                return new JobProxy26(context);
            case V_24:
                return new JobProxy24(context);
            case V_21:
                return new JobProxy21(context);
            case V_19:
                return new JobProxy19(context);
            case V_14:
                return new JobProxy14(context);
            case GCM:
                return new JobProxyGcm(context);
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    @NonNull
    /*package*/ synchronized JobProxy getProxy(Context context) {
        if (mCachedProxy == null) {
            mCachedProxy = createProxy(context);
        }
        return mCachedProxy;
    }

    public synchronized void invalidateCachedProxy() {
        mCachedProxy = null;
    }

    private boolean isServiceEnabled(@NonNull Context context, @NonNull Class<? extends Service> clazz) {
        // on some rooted devices user can disable services
        try {
            Intent intent = new Intent(context, clazz);
            List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
            return resolveInfos != null && !resolveInfos.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private static boolean isServiceEnabledAndHasPermission(@NonNull Context context, @NonNull Class<? extends Service> clazz, @NonNull String permission) {
        try {
            Intent intent = new Intent(context, clazz);
            List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
            if (resolveInfos == null || resolveInfos.isEmpty()) {
                return false;
            }

            for (ResolveInfo info : resolveInfos) {
                if (info.serviceInfo != null && permission.equals(info.serviceInfo.permission)) {
                    return true;
                }
            }
            return false;

        } catch (Exception e) {
            return false;
        }
    }

    private boolean isBroadcastEnabled(@NonNull Context context, @NonNull Class<? extends BroadcastReceiver> clazz) {
        // on some rooted devices user can disable receivers
        try {
            Intent intent = new Intent(context, clazz);
            List<ResolveInfo> resolveInfos = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            return resolveInfos != null && !resolveInfos.isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    public static JobApi getDefault(Context context) {
        if (V_26.isSupported(context) && JobConfig.isApiEnabled(V_26)) {
            return V_26;
        } else if (V_24.isSupported(context) && JobConfig.isApiEnabled(V_24)) {
            return V_24;
        } else if (V_21.isSupported(context) && JobConfig.isApiEnabled(V_21)) {
            return V_21;
        } else if (GCM.isSupported(context) && JobConfig.isApiEnabled(GCM)) {
            return GCM;
        } else if (V_19.isSupported(context) && JobConfig.isApiEnabled(V_19)) {
            return V_19;
        } else if (JobConfig.isApiEnabled(V_14)) {
            return V_14;
        } else {
            throw new IllegalStateException("All supported APIs are disabled");
        }
    }
}
