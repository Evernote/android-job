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
import android.support.annotation.Nullable;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobProxyIllegalStateException;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobUtil;

import java.util.List;


/**
 * @author rwondratschek
 */
@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class JobProxy21 implements JobProxy {

    private static final String TAG = "JobProxy21";

    private static final int ERROR_BOOT_PERMISSION = -123;

    protected final Context mContext;
    protected final JobCat mCat;

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
        long endMs = Common.getEndMs(request, true);

        JobInfo jobInfo = createBuilderOneOff(createBaseBuilder(request, true), startMs, endMs).build();
        int scheduleResult = schedule(jobInfo);

        if (scheduleResult == ERROR_BOOT_PERMISSION) {
            jobInfo = createBuilderOneOff(createBaseBuilder(request, false), startMs, endMs).build();
            scheduleResult = schedule(jobInfo);
        }

        mCat.d("Schedule one-off jobInfo %s, %s, start %s, end %s (from now), reschedule count %d", scheduleResultToString(scheduleResult),
                request, JobUtil.timeToString(startMs), JobUtil.timeToString(endMs), Common.getRescheduleCount(request));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        long intervalMs = request.getIntervalMs();
        long flexMs = request.getFlexMs();

        JobInfo jobInfo = createBuilderPeriodic(createBaseBuilder(request, true), intervalMs, flexMs).build();
        int scheduleResult = schedule(jobInfo);

        if (scheduleResult == ERROR_BOOT_PERMISSION) {
            jobInfo = createBuilderPeriodic(createBaseBuilder(request, false), intervalMs, flexMs).build();
            scheduleResult = schedule(jobInfo);
        }

        mCat.d("Schedule periodic jobInfo %s, %s, interval %s, flex %s", scheduleResultToString(scheduleResult),
                request, JobUtil.timeToString(intervalMs), JobUtil.timeToString(flexMs));
    }

    @Override
    public void plantPeriodicFlexSupport(JobRequest request) {
        long startMs = Common.getStartMsSupportFlex(request);
        long endMs = Common.getEndMsSupportFlex(request);

        JobInfo jobInfo = createBuilderOneOff(createBaseBuilder(request, true), startMs, endMs).build();
        int scheduleResult = schedule(jobInfo);

        if (scheduleResult == ERROR_BOOT_PERMISSION) {
            jobInfo = createBuilderOneOff(createBaseBuilder(request, false), startMs, endMs).build();
            scheduleResult = schedule(jobInfo);
        }

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

        TransientBundleCompat.cancel(mContext, jobId, null);
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

        for (JobInfo info : pendingJobs) {
            if (isJobInfoScheduled(info, request)) {
                return true;
            }
        }

        return false;
    }

    @SuppressWarnings("SimplifiableIfStatement")
    protected boolean isJobInfoScheduled(@Nullable JobInfo info, @NonNull JobRequest request) {
        boolean correctInfo = info != null && info.getId() == request.getJobId();
        if (!correctInfo) {
            return false;
        }
        return !request.isTransient() || TransientBundleCompat.isScheduled(mContext, request.getJobId());
    }

    protected JobInfo.Builder createBaseBuilder(JobRequest request, boolean allowPersisting) {
        JobInfo.Builder builder = new JobInfo.Builder(request.getJobId(), new ComponentName(mContext, PlatformJobService.class))
                .setRequiresCharging(request.requiresCharging())
                .setRequiresDeviceIdle(request.requiresDeviceIdle())
                .setRequiredNetworkType(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(allowPersisting && !request.isTransient() && JobUtil.hasBootPermission(mContext));

        return setTransientBundle(request, builder);
    }

    protected JobInfo.Builder createBuilderOneOff(JobInfo.Builder builder, long startMs, long endMs) {
        return builder.setMinimumLatency(startMs).setOverrideDeadline(endMs);
    }

    protected JobInfo.Builder createBuilderPeriodic(JobInfo.Builder builder, long intervalMs, long flexMs) {
        return builder.setPeriodic(intervalMs);
    }

    protected JobInfo.Builder setTransientBundle(JobRequest request, JobInfo.Builder builder) {
        if (request.isTransient()) {
            TransientBundleCompat.persistBundle(mContext, request);
        }

        return builder;
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
            case METERED:
                return JobInfo.NETWORK_TYPE_ANY; // use any here as fallback
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    protected final JobScheduler getJobScheduler() {
        return (JobScheduler) mContext.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    }

    protected final int schedule(JobInfo jobInfo) {
        JobScheduler jobScheduler = getJobScheduler();
        if (jobScheduler == null) {
            throw new JobProxyIllegalStateException("JobScheduler is null");
        }

        try {
            return jobScheduler.schedule(jobInfo);
        } catch (IllegalArgumentException e) {
            mCat.e(e);

            String message = e.getMessage();
            if (message != null && message.contains("RECEIVE_BOOT_COMPLETED")) {
                return ERROR_BOOT_PERMISSION;

            } else if (message != null && message.contains("No such service ComponentInfo")) {
                // this will reset the proxy and in the worst case use the AlarmManager
                throw new JobProxyIllegalStateException(e);

            } else {
                throw e;
            }
        } catch (NullPointerException e) {
            /*
            Attempt to invoke interface method 'int android.app.job.IJobScheduler.schedule(android.app.job.JobInfo)' on a null object reference
            at android.app.JobSchedulerImpl.schedule(JobSchedulerImpl.java:42)
            at com.evernote.android.job.v21.JobProxy21.schedule(JobProxy21.java:198)
             */
            mCat.e(e);
            throw new JobProxyIllegalStateException(e);
        }
    }

    protected static String scheduleResultToString(int scheduleResult) {
        return scheduleResult == JobScheduler.RESULT_SUCCESS ? "success" : "failure";
    }
}
