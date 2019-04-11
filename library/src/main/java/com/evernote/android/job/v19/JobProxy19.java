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
package com.evernote.android.job.v19;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.os.Build;
import androidx.annotation.RestrictTo;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobUtil;
import com.evernote.android.job.v14.JobProxy14;

/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.KITKAT)
@RestrictTo(RestrictTo.Scope.LIBRARY)
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
