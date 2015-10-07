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
package com.evernote.android.job.v21;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.support.annotation.NonNull;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobUtil;

import net.vrallev.android.cat.CatLog;

import java.util.List;


/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class JobProxy21 implements JobProxy {

    private static final CatLog CAT = new JobCat("JobProxy21");

    private final Context mContext;
    private final JobScheduler mJobScheduler;

    public JobProxy21(Context context) {
        mContext = context;
        mJobScheduler = (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    @Override
    public void plantOneOff(JobRequest request) {
        JobInfo jobInfo = createBaseBuilder(request)
                .setMinimumLatency(Common.getStartMs(request))
                .setOverrideDeadline(Common.getEndMs(request))
                .setRequiresCharging(request.requiresCharging())
                .setRequiresDeviceIdle(request.requiresDeviceIdle())
                .setRequiredNetworkType(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(request.isPersisted())
                .build();

        int scheduleResult = mJobScheduler.schedule(jobInfo);

        CAT.d("Schedule one-off jobInfo %s, %s, start %s, end %s", scheduleResult == JobScheduler.RESULT_SUCCESS ? "success" : "failure",
                request, JobUtil.timeToString(Common.getStartMs(request)), JobUtil.timeToString(Common.getEndMs(request)));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        JobInfo jobInfo = createBaseBuilder(request)
                .setPeriodic(request.getIntervalMs())
                .setRequiresCharging(request.requiresCharging())
                .setRequiresDeviceIdle(request.requiresDeviceIdle())
                .setRequiredNetworkType(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(request.isPersisted())
                .build();

        int scheduleResult = mJobScheduler.schedule(jobInfo);

        CAT.d("Schedule periodic jobInfo %s, %s, interval %s", scheduleResult == JobScheduler.RESULT_SUCCESS ? "success" : "failure",
                request, JobUtil.timeToString(request.getIntervalMs()));
    }

    @Override
    public void cancel(JobRequest request) {
        mJobScheduler.cancel(request.getJobId());
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        List<JobInfo> pendingJobs;
        try {
            pendingJobs = mJobScheduler.getAllPendingJobs();
        } catch (Exception e) {
            // it's possible that this throws an exception, see https://gist.github.com/vRallev/a59947dd3932d2642641
            CAT.e(e);
            return false;
        }

        if (pendingJobs == null || pendingJobs.isEmpty()) {
            return false;
        }

        int requestId = request.getJobId();
        for (JobInfo info : pendingJobs) {
            if (info.getId() == requestId) {
                return true;
            }
        }

        return false;
    }

    protected JobInfo.Builder createBaseBuilder(JobRequest request) {
        return new JobInfo.Builder(request.getJobId(), new ComponentName(mContext, PlatformJobService.class));
    }

    protected int convertNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case ANY:
                return JobInfo.NETWORK_TYPE_NONE;
            case CONNECTED:
                return JobInfo.NETWORK_TYPE_ANY;
            case UNMETERED:
                return JobInfo.NETWORK_TYPE_UNMETERED;
            default:
                throw new IllegalStateException("not implemented");
        }
    }
}
