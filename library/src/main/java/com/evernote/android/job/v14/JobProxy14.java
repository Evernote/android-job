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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobUtil;

import net.vrallev.android.cat.CatLog;

/**
 * @author rwondratschek
 */
public class JobProxy14 implements JobProxy {

    private static final CatLog CAT = new JobCat("JobProxy14");

    private final Context mContext;
    private AlarmManager mAlarmManager;

    public JobProxy14(Context context) {
        mContext = context;
    }

    @Override
    public void plantOneOff(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, false);
        setAlarm(request, System.currentTimeMillis() + Common.getAverageDelayMs(request), pendingIntent);

        CAT.d("Scheduled alarm, %s, delay %s, exact %b", request,
                JobUtil.timeToString(Common.getAverageDelayMs(request)), request.isExact());
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, true);
        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager != null) {
            alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + request.getIntervalMs(), request.getIntervalMs(), pendingIntent);
        }

        CAT.d("Scheduled repeating alarm, %s, interval %s", request, JobUtil.timeToString(request.getIntervalMs()));
    }

    @Override
    public void cancel(JobRequest request) {
        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager != null) {
            alarmManager.cancel(getPendingIntent(request, request.isPeriodic()));
        }
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, PendingIntent.FLAG_NO_CREATE);
        return pendingIntent != null;
    }

    protected PendingIntent getPendingIntent(JobRequest request, boolean repeating) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (!repeating) {
            flags |= PendingIntent.FLAG_ONE_SHOT;
        }
        return getPendingIntent(request, flags);
    }

    protected PendingIntent getPendingIntent(JobRequest request, int flags) {
        Intent intent = PlatformAlarmReceiver.createIntent(request);

        // repeating PendingIntent with service seams to have problems
        return PendingIntent.getBroadcast(mContext, request.getJobId(), intent, flags);
    }

    protected void setAlarm(JobRequest request, long triggerAtMillis, PendingIntent pendingIntent) {
        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager == null) {
            return;
        }

        try {
            if (request.isExact()) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                } else {
                    alarmManager.set(AlarmManager.RTC_WAKEUP, triggerAtMillis, pendingIntent);
                }

            } else {
                alarmManager.set(AlarmManager.RTC, triggerAtMillis, pendingIntent);
            }
        } catch (Exception e) {
            // https://gist.github.com/vRallev/621b0b76a14ddde8691c
            CAT.e(e);
        }
    }

    @Nullable
    protected AlarmManager getAlarmManager() {
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        }
        if (mAlarmManager == null) {
            // https://gist.github.com/vRallev/5daef6e8a3b0d4a7c366
            CAT.e("AlarmManager is null");
        }
        return mAlarmManager;
    }
}
