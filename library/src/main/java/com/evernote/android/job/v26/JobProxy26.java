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
package com.evernote.android.job.v26;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.v24.JobProxy24;


/**
 * @author rwondratschek
 */
@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.O)
public class JobProxy26 extends JobProxy24 {

    private static final String TAG = "JobProxy26";

    public JobProxy26(Context context) {
        super(context, TAG);
    }

    public JobProxy26(Context context, String tag) {
        super(context, tag);
    }

    @Override
    protected JobInfo.Builder setTransientBundle(JobRequest request, JobInfo.Builder builder) {
        return builder.setTransientExtras(request.getTransientExtras());
    }

    @Override
    protected boolean isJobInfoScheduled(@Nullable JobInfo info, @NonNull JobRequest request) {
        return info != null && info.getId() == request.getJobId();
    }

    @Override
    protected JobInfo.Builder createBaseBuilder(JobRequest request, boolean allowPersisting) {
        return super.createBaseBuilder(request, allowPersisting)
                .setRequiresBatteryNotLow(request.requiresBatteryNotLow())
                .setRequiresStorageNotLow(request.requiresStorageNotLow());
    }

    @Override
    protected int convertNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case METERED:
                return JobInfo.NETWORK_TYPE_METERED;
            default:
                return super.convertNetworkType(networkType);
        }

    }
}
