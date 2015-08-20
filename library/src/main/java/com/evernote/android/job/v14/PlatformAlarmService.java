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

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author rwondratschek
 */
public class PlatformAlarmService extends IntentService {

    private static final ExecutorService EXECUTOR_SERVICE = Executors.newCachedThreadPool();

    /*package*/ static Intent createIntent(Context context, int jobId) {
        Intent intent = new Intent(context, PlatformAlarmService.class);
        intent.putExtra(PlatformAlarmReceiver.EXTRA_JOB_ID, jobId);
        return intent;
    }

    public PlatformAlarmService() {
        super(PlatformAlarmService.class.getSimpleName());
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        int jobId = intent.getIntExtra(PlatformAlarmReceiver.EXTRA_JOB_ID, -1);

        final JobProxy.Common common = new JobProxy.Common(this, jobId);

        final JobRequest request = common.getPendingRequest();
        if (request != null) {
            // parallel execution
            EXECUTOR_SERVICE.execute(new Runnable() {
                @Override
                public void run() {
                    common.executeJobRequest(request);

                    // call here, our own wake lock could be acquired too late
                    PlatformAlarmReceiver.completeWakefulIntent(intent);
                }
            });
        }
    }
}
