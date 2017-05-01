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
package com.evernote.android.job.v24;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.v21.JobProxy21;


/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.N)
public class JobProxy24 extends JobProxy21 {

    private static final String TAG = "JobProxy24";

    public JobProxy24(Context context) {
        this(context, TAG);
    }

    public JobProxy24(Context context, String tag) {
        super(context, tag);
    }

    @Override
    public void plantPeriodicFlexSupport(JobRequest request) {
        mCat.w("plantPeriodicFlexSupport called although flex is supported");
        super.plantPeriodicFlexSupport(request);
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        try {
            return isJobInfoScheduled(getJobScheduler().getPendingJob(request.getJobId()), request);
        } catch (Exception e) {
            mCat.e(e);
            return false;
        }
    }

    @Override
    protected JobInfo.Builder createBuilderPeriodic(JobInfo.Builder builder, long intervalMs, long flexMs) {
        return builder.setPeriodic(intervalMs, flexMs);
    }

    @Override
    protected int convertNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case NOT_ROAMING:
                return JobInfo.NETWORK_TYPE_NOT_ROAMING;
            default:
                return super.convertNetworkType(networkType);
        }
    }
}
