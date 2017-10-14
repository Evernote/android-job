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
package com.evernote.android.job.v19;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobUtil;
import com.evernote.android.job.v14.JobProxy14;

/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
public class JobProxy19 extends JobProxy14 {

    private static final String TAG = "JobProxy19";

    public JobProxy19(Context context) {
        super(context, TAG);
    }

    @Override
    protected void plantOneOffInexact(JobRequest request, AlarmManager alarmManager, PendingIntent pendingIntent) {
        long currentTime = System.currentTimeMillis();
        long startMs = currentTime + Common.getStartMs(request);
        long lengthMs = Common.getEndMs(request) - Common.getStartMs(request);

        alarmManager.setWindow(AlarmManager.RTC, startMs, lengthMs, pendingIntent);

        mCat.d("Schedule alarm, %s, start %s, end %s", request,
                JobUtil.timeToString(Common.getStartMs(request)), JobUtil.timeToString(Common.getEndMs(request)));
    }

    @Override
    protected void plantOneOffFlexSupport(JobRequest request, AlarmManager alarmManager, PendingIntent pendingIntent) {
        long currentTime = System.currentTimeMillis();
        long startMs = currentTime + Common.getStartMsSupportFlex(request);
        long lengthMs = Common.getEndMsSupportFlex(request) - Common.getStartMsSupportFlex(request);

        alarmManager.setWindow(AlarmManager.RTC, startMs, lengthMs, pendingIntent);

        mCat.d("Scheduled repeating alarm (flex support), %s, start %s, end %s, flex %s", request,
                JobUtil.timeToString(Common.getStartMsSupportFlex(request)), JobUtil.timeToString(Common.getEndMsSupportFlex(request)),
                JobUtil.timeToString(request.getFlexMs()));
    }
}
