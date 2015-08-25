/*
 * Copyright 2012 Evernote Corporation.
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

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobUtil;

import net.vrallev.android.cat.Cat;

/**
 * @author rwondratschek
 */
public class JobProxy14 implements JobProxy {

    private final Context mContext;
    private final AlarmManager mAlarmManager;

    public JobProxy14(Context context) {
        mContext = context;
        mAlarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    }

    @Override
    public void plantOneOff(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, false);
        setAlarm(request, AlarmManager.RTC, System.currentTimeMillis() + Common.getAverageDelayMs(request), pendingIntent);

        Cat.d("Scheduled alarm, %s, delay %s, exact %b", request,
                JobUtil.timeToString(Common.getAverageDelayMs(request)), request.isExact());
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, true);
        mAlarmManager.setRepeating(AlarmManager.RTC, System.currentTimeMillis() + request.getIntervalMs(), request.getIntervalMs(), pendingIntent);

        Cat.d("Scheduled repeating alarm, %s, interval %s", request, JobUtil.timeToString(request.getIntervalMs()));
    }

    @Override
    public void cancel(JobRequest request) {
        mAlarmManager.cancel(getPendingIntent(request, request.isPeriodic()));
    }

    protected PendingIntent getPendingIntent(JobRequest request, boolean repeating) {
        Intent intent = PlatformAlarmReceiver.createIntent(request);

        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (!repeating) {
            flags |= PendingIntent.FLAG_ONE_SHOT;
        }

        // repeating PendingIntent with service seams to have problems
        return PendingIntent.getBroadcast(mContext, request.getJobId(), intent, flags);
    }

    protected void setAlarm(JobRequest request, int type, long triggerAtMillis, PendingIntent pendingIntent) {
        if (request.isExact() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAlarmManager.setExactAndAllowWhileIdle(type, triggerAtMillis, pendingIntent);
        } else if (request.isExact() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            mAlarmManager.setExact(type, triggerAtMillis, pendingIntent);
        } else {
            mAlarmManager.set(type, triggerAtMillis, pendingIntent);
        }
    }
}
