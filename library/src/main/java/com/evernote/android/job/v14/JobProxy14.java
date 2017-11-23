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
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobConfig;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobUtil;

/**
 * @author rwondratschek
 */
@SuppressWarnings("WeakerAccess")
public class JobProxy14 implements JobProxy {

    private static final String TAG = "JobProxy14";

    protected final Context mContext;
    protected final JobCat mCat;

    private AlarmManager mAlarmManager;

    public JobProxy14(Context context) {
        this(context, TAG);
    }

    protected JobProxy14(Context context, String logTag) {
        mContext = context;
        mCat = new JobCat(logTag);
    }

    @Override
    public void plantOneOff(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, false);

        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager == null) {
            return;
        }

        try {
            if (request.isExact()) {
                if (request.getStartMs() == 1 && request.getFailureCount() <= 0) {
                    // this job should start immediately
                    PlatformAlarmService.start(mContext, request.getJobId(), request.getTransientExtras());
                } else {
                    plantOneOffExact(request, alarmManager, pendingIntent);
                }
            } else {
                plantOneOffInexact(request, alarmManager, pendingIntent);
            }
        } catch (Exception e) {
            // https://gist.github.com/vRallev/621b0b76a14ddde8691c
            mCat.e(e);
        }
    }

    protected void plantOneOffInexact(JobRequest request, AlarmManager alarmManager, PendingIntent pendingIntent) {
        alarmManager.set(getType(false), getTriggerAtMillis(request), pendingIntent);
        logScheduled(request);
    }

    protected void plantOneOffExact(JobRequest request, AlarmManager alarmManager, PendingIntent pendingIntent) {
        long triggerAtMillis = getTriggerAtMillis(request);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            alarmManager.setExactAndAllowWhileIdle(getType(true), triggerAtMillis, pendingIntent);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(getType(true), triggerAtMillis, pendingIntent);
        } else {
            alarmManager.set(getType(true), triggerAtMillis, pendingIntent);
        }
        logScheduled(request);
    }

    protected void plantOneOffFlexSupport(JobRequest request, AlarmManager alarmManager, PendingIntent pendingIntent) {
        long triggerAtMs = JobConfig.getClock().currentTimeMillis() + Common.getAverageDelayMsSupportFlex(request);
        alarmManager.set(AlarmManager.RTC, triggerAtMs, pendingIntent);

        mCat.d("Scheduled repeating alarm (flex support), %s, interval %s, flex %s", request,
                JobUtil.timeToString(request.getIntervalMs()), JobUtil.timeToString(request.getFlexMs()));
    }

    protected long getTriggerAtMillis(JobRequest request) {
        if (JobConfig.isForceRtc()) {
            return JobConfig.getClock().currentTimeMillis() + Common.getAverageDelayMs(request);
        } else {
            return JobConfig.getClock().elapsedRealtime() + Common.getAverageDelayMs(request);
        }
    }

    protected int getType(boolean wakeup) {
        if (wakeup) {
            return JobConfig.isForceRtc() ? AlarmManager.RTC_WAKEUP : AlarmManager.ELAPSED_REALTIME_WAKEUP;
        } else {
            return JobConfig.isForceRtc() ? AlarmManager.RTC : AlarmManager.ELAPSED_REALTIME;
        }
    }

    private void logScheduled(JobRequest request) {
        mCat.d("Scheduled alarm, %s, delay %s (from now), exact %b, reschedule count %d", request,
                JobUtil.timeToString(Common.getAverageDelayMs(request)), request.isExact(), Common.getRescheduleCount(request));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, true);
        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager != null) {
            alarmManager.setRepeating(getType(true), getTriggerAtMillis(request), request.getIntervalMs(), pendingIntent);
        }

        mCat.d("Scheduled repeating alarm, %s, interval %s", request, JobUtil.timeToString(request.getIntervalMs()));
    }

    @Override
    public void plantPeriodicFlexSupport(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, false);

        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager == null) {
            return;
        }

        try {
            plantOneOffFlexSupport(request, alarmManager, pendingIntent);
        } catch (Exception e) {
            // https://gist.github.com/vRallev/621b0b76a14ddde8691c
            mCat.e(e);
        }
    }

    @Override
    public void cancel(int jobId) {
        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager != null) {
            try {
                // exact parameter doesn't matter
                alarmManager.cancel(getPendingIntent(jobId, false, null, createPendingIntentFlags(true)));
                alarmManager.cancel(getPendingIntent(jobId, false, null, createPendingIntentFlags(false)));
            } catch (Exception e) {
                // java.lang.SecurityException: get application info: Neither user 1010133 nor
                // current process has android.permission.INTERACT_ACROSS_USERS.
                mCat.e(e);
            }
        }
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        PendingIntent pendingIntent = getPendingIntent(request, PendingIntent.FLAG_NO_CREATE);
        return pendingIntent != null;
    }

    protected int createPendingIntentFlags(boolean repeating) {
        int flags = PendingIntent.FLAG_UPDATE_CURRENT;
        if (!repeating) {
            flags |= PendingIntent.FLAG_ONE_SHOT;
        }
        return flags;
    }

    protected PendingIntent getPendingIntent(JobRequest request, boolean repeating) {
        return getPendingIntent(request, createPendingIntentFlags(repeating));
    }

    protected PendingIntent getPendingIntent(JobRequest request, int flags) {
        return getPendingIntent(request.getJobId(), request.isExact(), request.getTransientExtras(), flags);
    }

    protected PendingIntent getPendingIntent(int jobId, boolean exact, @Nullable Bundle transientExtras, int flags) {
        Intent intent = PlatformAlarmReceiver.createIntent(mContext, jobId, exact, transientExtras);

        // repeating PendingIntent with service seams to have problems
        try {
            return PendingIntent.getBroadcast(mContext, jobId, intent, flags);
        } catch (Exception e) {
            // java.lang.SecurityException: Permission Denial: getIntentSender() from pid=31482, uid=10057,
            // (need uid=-1) is not allowed to send as package com.evernote
            mCat.e(e);
            return null;
        }
    }

    @Nullable
    protected AlarmManager getAlarmManager() {
        if (mAlarmManager == null) {
            mAlarmManager = (AlarmManager) mContext.getSystemService(Context.ALARM_SERVICE);
        }
        if (mAlarmManager == null) {
            // https://gist.github.com/vRallev/5daef6e8a3b0d4a7c366
            mCat.e("AlarmManager is null");
        }
        return mAlarmManager;
    }
}
