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
package com.evernote.android.job.v21;

import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.v14.PlatformAlarmServiceExact;

import java.util.concurrent.TimeUnit;

import static com.evernote.android.job.PendingIntentUtil.flagImmutable;

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
@RestrictTo(RestrictTo.Scope.LIBRARY)
/*package*/ final class TransientBundleCompat {

    private static final JobCat CAT = new JobCat("TransientBundleCompat");

    private TransientBundleCompat() {
        throw new UnsupportedOperationException();
    }

    @SuppressLint("MissingPermission")
    public static void persistBundle(@NonNull Context context, @NonNull JobRequest request) {
        Intent intent = PlatformAlarmServiceExact.createIntent(context, request.getJobId(), request.getTransientExtras());
        PendingIntent pendingIntent = PendingIntent.getService(context, request.getJobId(), intent, PendingIntent.FLAG_UPDATE_CURRENT | flagImmutable());

        long when = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1000);

        AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        alarmManager.setExact(AlarmManager.RTC, when, pendingIntent);
    }

    public static boolean startWithTransientBundle(@NonNull Context context, @NonNull JobRequest request) {
        // transientExtras are not necessary in this case
        Intent intent = PlatformAlarmServiceExact.createIntent(context, request.getJobId(), null);
        PendingIntent pendingIntent = PendingIntent.getService(context, request.getJobId(), intent, PendingIntent.FLAG_NO_CREATE | flagImmutable());

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
        return PendingIntent.getService(context, jobId, intent, PendingIntent.FLAG_NO_CREATE | flagImmutable()) != null;
    }

    public static void cancel(@NonNull Context context, int jobId, @Nullable PendingIntent pendingIntent) {
        try {
            if (pendingIntent == null) {
                Intent intent = PlatformAlarmServiceExact.createIntent(context, jobId, null);
                pendingIntent = PendingIntent.getService(context, jobId, intent, PendingIntent.FLAG_NO_CREATE | flagImmutable());

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
