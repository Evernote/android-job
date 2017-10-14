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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobProxy;

/**
 * @author rwondratschek
 */
public class PlatformAlarmReceiver extends BroadcastReceiver {

    /*package*/ static final String EXTRA_JOB_ID = "EXTRA_JOB_ID";
    /*package*/ static final String EXTRA_JOB_EXACT = "EXTRA_JOB_EXACT";
    /*package*/ static final String EXTRA_TRANSIENT_EXTRAS = "EXTRA_TRANSIENT_EXTRAS";

    /*package*/ static Intent createIntent(Context context, int jobId, boolean exact, @Nullable Bundle transientExtras) {
        Intent intent = new Intent(context, PlatformAlarmReceiver.class).putExtra(EXTRA_JOB_ID, jobId).putExtra(EXTRA_JOB_EXACT, exact);
        if (transientExtras != null) {
            intent.putExtra(EXTRA_TRANSIENT_EXTRAS, transientExtras);
        }
        return intent;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null && intent.hasExtra(EXTRA_JOB_ID) && intent.hasExtra(EXTRA_JOB_EXACT)) {
            int jobId = intent.getIntExtra(EXTRA_JOB_ID, -1);
            Bundle transientExtras = intent.getBundleExtra(EXTRA_TRANSIENT_EXTRAS);

            if (intent.getBooleanExtra(EXTRA_JOB_EXACT, false)) {
                Intent serviceIntent = PlatformAlarmServiceExact.createIntent(context, jobId, transientExtras);
                JobProxy.Common.startWakefulService(context, serviceIntent);
            } else {
                PlatformAlarmService.start(context, jobId, transientExtras);
            }
        }
    }
}
