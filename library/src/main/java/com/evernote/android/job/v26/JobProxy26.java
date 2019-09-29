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
package com.evernote.android.job.v26;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;

import com.evernote.android.job.JobRequest;
import com.evernote.android.job.v24.JobProxy24;


/**
 * @author rwondratschek
 */
@SuppressWarnings("unused")
@TargetApi(Build.VERSION_CODES.O)
@RestrictTo(RestrictTo.Scope.LIBRARY)
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

    @SuppressWarnings("deprecation")
    @Override
    protected int convertNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case METERED:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    return JobInfo.NETWORK_TYPE_CELLULAR;
                } else {
                    return JobInfo.NETWORK_TYPE_METERED;
                }

            default:
                return super.convertNetworkType(networkType);
        }

    }
}
