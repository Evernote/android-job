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
package com.evernote.android.job.v21;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.v14.PlatformAlarmServiceExact;

import java.util.concurrent.TimeUnit;

/**
 * Dirty workaround. We schedule an alarm with the AlarmManager really far in the future.
 * The job will still be started by the JobScheduler, but executed by the PlatformAlarmService
 * with the help of the PendingIntent, because the PendingIntent holds the transient Bundle.
 *
 * If the PendingIntent is gone, that means our transient state is lost.
 *
 * Created by rwondratschek on 01.05.17.
 */
@SuppressWarnings("WeakerAccess")
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
/*package*/ final class TransientBundleCompat {

    private static final JobCat CAT = new JobCat("TransientBundleCompat");

    private TransientBundleCompat() {
        throw new UnsupportedOperationException();
    }

    public static void persistBundle(@NonNull Context context, @NonNull JobRequest request) {
        Intent intent = PlatformAlarmServiceExact.createIntent(context, request.getJobId(), request.getTransientExtras());
        PendingIntent pendingIntent = PendingIntent.getService(context, request.getJobId(), intent, PendingIntent.FLAG_UPDATE_CURRENT);

        long when = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1000);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExact(AlarmManager.RTC, when, pendingIntent);
    }

    public static boolean startWithTransientBundle(@NonNull Context context, @NonNull JobRequest request) {
        // transientExtras are not necessary in this case
        Intent intent = PlatformAlarmServiceExact.createIntent(context, request.getJobId(), null);
        PendingIntent pendingIntent = PendingIntent.getService(context, request.getJobId(), intent, PendingIntent.FLAG_NO_CREATE);

        if (pendingIntent == null) {
            return false;
        }

        try {
            CAT.i("Delegating transient job %s to API 14", request);
            pendingIntent.send();
        } catch (PendingIntent.CanceledException e) {
            CAT.e(e);
            return false;
        }

        if (!request.isPeriodic()) {
            cancel(context, request.getJobId(), pendingIntent);
        }

        return true;
    }

    public static boolean isScheduled(Context context, int jobId) {
        Intent intent = PlatformAlarmServiceExact.createIntent(context, jobId, null);
        return PendingIntent.getService(context, jobId, intent, PendingIntent.FLAG_NO_CREATE) != null;
    }

    public static void cancel(@NonNull Context context, int jobId, @Nullable PendingIntent pendingIntent) {
        try {
            if (pendingIntent == null) {
                Intent intent = PlatformAlarmServiceExact.createIntent(context, jobId, null);
                pendingIntent = PendingIntent.getService(context, jobId, intent, PendingIntent.FLAG_NO_CREATE);

                if (pendingIntent == null) {
                    return;
                }
            }

            AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
            alarmManager.cancel(pendingIntent);

            pendingIntent.cancel();
        } catch (Exception e) {
            CAT.e(e); // we don't care if it fails, we don't want to crash the library
        }
    }
}
