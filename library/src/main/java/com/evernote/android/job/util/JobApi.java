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

import android.app.AlarmManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.gcm.JobProxyGcm;
import com.evernote.android.job.v14.JobProxy14;
import com.evernote.android.job.v19.JobProxy19;
import com.evernote.android.job.v21.JobProxy21;
import com.evernote.android.job.v24.JobProxy24;
import com.google.android.gms.gcm.GcmNetworkManager;

/**
 * All available APIs.
 *
 * @author rwondratschek
 */
public enum JobApi {
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_24(true, false),
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_21(true, true),
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_19(true, true),
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_14(false, true),
    /**
     * Uses the {@link GcmNetworkManager} for scheduling jobs.
     */
    GCM(true, false);

    private JobProxy mCachedProxy;

    private final boolean mSupportsExecutionWindow;
    private final boolean mFlexSupport;

    JobApi(boolean supportsExecutionWindow, boolean flexSupport) {
        mSupportsExecutionWindow = supportsExecutionWindow;
        mFlexSupport = flexSupport;
    }

    public boolean supportsExecutionWindow() {
        return mSupportsExecutionWindow;
    }

    public boolean isFlexSupport() {
        return mFlexSupport;
    }

    public boolean isSupported(Context context) {
        switch (this) {
            case V_24:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.N;
            case V_21:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
            case V_19:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT;
            case V_14:
                return true;
            case GCM:
                return GcmAvailableHelper.isGcmApiSupported(context);
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    @NonNull
    public JobProxy createProxy(Context context) {
        switch (this) {
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
    public synchronized JobProxy getCachedProxy(Context context) {
        if (mCachedProxy == null) {
            mCachedProxy = createProxy(context);
        }
        return mCachedProxy;
    }

    /**
     * @deprecated Use {@link #getDefault(Context, boolean)} instead.
     */
    @SuppressWarnings("unused")
    @NonNull
    @Deprecated
    public static JobApi getDefault(Context context) {
        return getDefault(context, JobManager.instance().getConfig().isGcmApiEnabled());
    }

    @NonNull
    public static JobApi getDefault(Context context, boolean gcmEnabled) {
        if (V_24.isSupported(context)) {
            return V_24;
        } else if (V_21.isSupported(context)) {
            return V_21;
        } else if (gcmEnabled && GCM.isSupported(context)) {
            return GCM;
        } else if (V_19.isSupported(context)) {
            return V_19;
        } else {
            return V_14;
        }
    }
}
