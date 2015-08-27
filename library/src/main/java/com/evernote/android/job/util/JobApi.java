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

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.v14.JobProxy14;
import com.evernote.android.job.v21.JobProxy21;

import com.evernote.android.job.gcm.JobProxyGcm;

/**
 * All available APIs.
 *
 * @author rwondratschek
 */
public enum JobApi {
    /**
     * Uses the {@link JobScheduler} for scheduling jobs.
     */
    V_21,
    /**
     * Uses the {@link AlarmManager} for scheduling jobs.
     */
    V_14,
    /**
     * Uses the {@link GcmNetworkManager} for scheduling jobs.
     */
    GCM;

    private JobProxy mCachedProxy;

    public boolean isSupported(Context context) {
        switch (this) {
            case V_21:
                return Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
            case V_14:
                return true;
            case GCM:
                return GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS;
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    public JobProxy createProxy(Context context) {
        switch (this) {
            case V_21:
                return new JobProxy21(context);
            case V_14:
                return new JobProxy14(context);
            case GCM:
                return new JobProxyGcm(context);
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    public synchronized JobProxy getCachedProxy(Context context) {
        if (mCachedProxy == null) {
            mCachedProxy = createProxy(context);
        }
        return mCachedProxy;
    }

    public static JobApi getDefault(Context context) {
        if (V_21.isSupported(context)) {
            return V_21;
        } else if (GCM.isSupported(context)) {
            return GCM;
        } else {
            return V_14;
        }
    }
}
