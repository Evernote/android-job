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
package com.evernote.android.job.v14;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobConfig;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.util.JobCat;

import java.util.HashSet;
import java.util.Set;

/**
 * @author rwondratschek
 */
public final class PlatformAlarmServiceExact extends Service {

    private static final JobCat CAT = new JobCat("PlatformAlarmServiceExact");

    public static Intent createIntent(Context context, int jobId, @Nullable Bundle transientExtras) {
        Intent intent = new Intent(context, PlatformAlarmServiceExact.class);
        intent.putExtra(PlatformAlarmReceiver.EXTRA_JOB_ID, jobId);
        if (transientExtras != null) {
            intent.putExtra(PlatformAlarmReceiver.EXTRA_TRANSIENT_EXTRAS, transientExtras);
        }
        return intent;
    }

    private final Object mMonitor = new Object();

    private volatile Set<Integer> mStartIds;
    private volatile int mLastStartId;

    @Override
    public void onCreate() {
        super.onCreate();
        mStartIds = new HashSet<>();
    }

    @Override
    public int onStartCommand(@Nullable final Intent intent, int flags, final int startId) {
        synchronized (mMonitor) {
            mStartIds.add(startId);
            mLastStartId = startId;
        }

        JobConfig.getExecutorService().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    PlatformAlarmService.runJob(intent, PlatformAlarmServiceExact.this, CAT);
                } finally {
                    // call here, our own wake lock could be acquired too late
                    JobProxy.Common.completeWakefulIntent(intent);
                    stopSelfIfNecessary(startId);
                }
            }
        });
        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        synchronized (mMonitor) {
            mStartIds = null;
            mLastStartId = 0;
        }
    }

    @Override
    public final IBinder onBind(Intent intent) {
        return null;
    }

    private void stopSelfIfNecessary(int startId) {
        synchronized (mMonitor) {
            Set<Integer> startIds = mStartIds;
            if (startIds != null) {
                // service not destroyed
                startIds.remove(startId);
                if (startIds.isEmpty()) {
                    stopSelfResult(mLastStartId);
                }
            }
        }
    }
}
