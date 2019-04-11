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
package com.evernote.android.job.v24;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.v21.JobProxy21;


/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.N)
@RestrictTo(RestrictTo.Scope.LIBRARY)
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
