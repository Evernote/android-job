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

    private static final String TAG = "JobProxy21";

    protected final Context mContext;
    protected final CatLog mCat;

    public JobProxy21(Context context) {
        this(context, TAG);
    }

    protected JobProxy21(Context context, String logTag) {
        mContext = context;
        mCat = new JobCat(logTag);
    }

    @Override
    public void plantOneOff(JobRequest request) {
        long startMs = Common.getStartMs(request);
        long endMs = Common.getEndMs(request);

        JobInfo jobInfo = createBuilderOneOff(createBaseBuilder(request), startMs, endMs).build();
        int scheduleResult = schedule(jobInfo);

        mCat.d("Schedule one-off jobInfo %s, %s, start %s, end %s", scheduleResultToString(scheduleResult),
                request, JobUtil.timeToString(startMs), JobUtil.timeToString(endMs));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        long intervalMs = request.getIntervalMs();
        long flexMs = request.getFlexMs();

        JobInfo jobInfo = createBuilderPeriodic(createBaseBuilder(request), intervalMs, flexMs).build();
        int scheduleResult = schedule(jobInfo);

        mCat.d("Schedule periodic jobInfo %s, %s, interval %s, flex %s", scheduleResultToString(scheduleResult),
                request, JobUtil.timeToString(intervalMs), JobUtil.timeToString(flexMs));
    }

    @Override
    public void plantPeriodicFlexSupport(JobRequest request) {
        long startMs = Common.getStartMsSupportFlex(request);
        long endMs = Common.getEndMsSupportFlex(request);

        JobInfo jobInfo = createBuilderOneOff(createBaseBuilder(request), startMs, endMs).build();
        int scheduleResult = schedule(jobInfo);

        mCat.d("Schedule periodic (flex support) jobInfo %s, %s, start %s, end %s, flex %s", scheduleResultToString(scheduleResult),
                request, JobUtil.timeToString(startMs), JobUtil.timeToString(endMs), JobUtil.timeToString(request.getFlexMs()));
    }

    @Override
    public void cancel(int jobId) {
        try {
            getJobScheduler().cancel(jobId);
        } catch (Exception e) {
            // https://gist.github.com/vRallev/5d48a4a8e8d05067834e
            mCat.e(e);
        }
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        List<JobInfo> pendingJobs;
        try {
            pendingJobs = getJobScheduler().getAllPendingJobs();
        } catch (Exception e) {
            // it's possible that this throws an exception, see https://gist.github.com/vRallev/a59947dd3932d2642641
            mCat.e(e);
            return false;
        }

        //noinspection ConstantConditions
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
        return new JobInfo.Builder(request.getJobId(), new ComponentName(mContext, PlatformJobService.class))
                .setRequiresCharging(request.requiresCharging())
                .setRequiresDeviceIdle(request.requiresDeviceIdle())
                .setRequiredNetworkType(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(request.isPersisted());
    }

    protected JobInfo.Builder createBuilderOneOff(JobInfo.Builder builder, long startMs, long endMs) {
        return builder.setMinimumLatency(startMs).setOverrideDeadline(endMs);
    }

    protected JobInfo.Builder createBuilderPeriodic(JobInfo.Builder builder, long intervalMs, long flexMs) {
        return builder.setPeriodic(intervalMs);
    }

    protected int convertNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case ANY:
                return JobInfo.NETWORK_TYPE_NONE;
            case CONNECTED:
                return JobInfo.NETWORK_TYPE_ANY;
            case UNMETERED:
                return JobInfo.NETWORK_TYPE_UNMETERED;
            case NOT_ROAMING:
                return JobInfo.NETWORK_TYPE_UNMETERED; // use unmetered here, is overwritten in v24
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    protected final JobScheduler getJobScheduler() {
        return (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    protected final int schedule(JobInfo jobInfo) {
        try {
            return getJobScheduler().schedule(jobInfo);
        } catch (Exception e) {
            mCat.e(e);
            return JobScheduler.RESULT_FAILURE;
        }
    }

    protected static String scheduleResultToString(int scheduleResult) {
        return scheduleResult == JobScheduler.RESULT_SUCCESS ? "success" : "failure";
    }
}
