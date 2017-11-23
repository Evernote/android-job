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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.JobIntentService;

import com.evernote.android.job.JobIdsInternal;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;

/**
 * @author rwondratschek
 */
public final class PlatformAlarmService extends JobIntentService {

    private static final JobCat CAT = new JobCat("PlatformAlarmService");

    public static void start(Context context, int jobId, @Nullable Bundle transientExtras) {
        Intent intent = new Intent();
        intent.putExtra(PlatformAlarmReceiver.EXTRA_JOB_ID, jobId);
        if (transientExtras != null) {
            intent.putExtra(PlatformAlarmReceiver.EXTRA_TRANSIENT_EXTRAS, transientExtras);
        }

        enqueueWork(context, PlatformAlarmService.class, JobIdsInternal.JOB_ID_PLATFORM_ALARM_SERVICE, intent);
    }

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        runJob(intent, this, CAT);
    }

    /*package*/ static void runJob(@Nullable Intent intent, @NonNull Service service, @NonNull JobCat cat) {
        if (intent == null) {
            cat.i("Delivered intent is null");
            return;
        }

        int jobId = intent.getIntExtra(PlatformAlarmReceiver.EXTRA_JOB_ID, -1);
        Bundle transientExtras = intent.getBundleExtra(PlatformAlarmReceiver.EXTRA_TRANSIENT_EXTRAS);
        final JobProxy.Common common = new JobProxy.Common(service, cat, jobId);

        // create the JobManager. Seeing sometimes exceptions, that it wasn't created, yet.
        final JobRequest request = common.getPendingRequest(true, true);
        if (request != null) {
            common.executeJobRequest(request, transientExtras);
        }
    }
}
