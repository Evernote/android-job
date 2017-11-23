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
package com.evernote.android.job.gcm;

import android.content.Context;
import android.support.annotation.NonNull;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobUtil;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;

/**
 * @author rwondratschek
 */
public class JobProxyGcm implements JobProxy {

    private static final JobCat CAT = new JobCat("JobProxyGcm");

    /*
     * Requires charging doesn't work reliable. Like the documentation says, the job doesn't run if
     * not plugged in. However, the job never runs until you schedule a new one although the device is
     * already plugged in again.
     */

    private final Context mContext;
    private final GcmNetworkManager mGcmNetworkManager;

    public JobProxyGcm(Context context) {
        mContext = context;
        mGcmNetworkManager = GcmNetworkManager.getInstance(context);
    }

    @Override
    public void plantOneOff(JobRequest request) {
        long startMs = Common.getStartMs(request);
        long startSeconds = startMs / 1_000;

        long endMs = Common.getEndMs(request);
        long endSeconds = Math.max(endMs / 1_000, startSeconds + 1); // endSeconds must be greater than startSeconds

        OneoffTask task = prepareBuilder(new OneoffTask.Builder(), request)
                .setExecutionWindow(startSeconds, endSeconds)
                .build();

        mGcmNetworkManager.schedule(task);

        CAT.d("Scheduled OneoffTask, %s, start %s, end %s (from now), reschedule count %d", request, JobUtil.timeToString(startMs),
                JobUtil.timeToString(endMs), Common.getRescheduleCount(request));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PeriodicTask task = prepareBuilder(new PeriodicTask.Builder(), request)
                .setPeriod(request.getIntervalMs() / 1_000)
                .setFlex(request.getFlexMs() / 1_000)
                .build();

        mGcmNetworkManager.schedule(task);

        CAT.d("Scheduled PeriodicTask, %s, interval %s, flex %s", request, JobUtil.timeToString(request.getIntervalMs()),
                JobUtil.timeToString(request.getFlexMs()));
    }

    @Override
    public void plantPeriodicFlexSupport(JobRequest request) {
        CAT.w("plantPeriodicFlexSupport called although flex is supported");

        long startMs = Common.getStartMsSupportFlex(request);
        long endMs = Common.getEndMsSupportFlex(request);

        OneoffTask task = prepareBuilder(new OneoffTask.Builder(), request)
                .setExecutionWindow(startMs / 1_000, endMs / 1_000)
                .build();

        mGcmNetworkManager.schedule(task);

        CAT.d("Scheduled periodic (flex support), %s, start %s, end %s, flex %s", request, JobUtil.timeToString(startMs),
                JobUtil.timeToString(endMs), JobUtil.timeToString(request.getFlexMs()));
    }

    @Override
    public void cancel(int jobId) {
        mGcmNetworkManager.cancelTask(createTag(jobId), PlatformGcmService.class);
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        // there is no option to check whether a task is scheduled, assume it is
        return true;
    }

    protected <T extends Task.Builder> T prepareBuilder(T builder, JobRequest request) {
        builder.setTag(createTag(request))
                .setService(PlatformGcmService.class)
                .setUpdateCurrent(true)
                .setRequiredNetwork(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(JobUtil.hasBootPermission(mContext))
                .setRequiresCharging(request.requiresCharging())
                .setExtras(request.getTransientExtras());
        return builder;
    }

    protected String createTag(JobRequest request) {
        return createTag(request.getJobId());
    }

    protected String createTag(int jobId) {
        return String.valueOf(jobId);
    }

    protected int convertNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case ANY:
                return Task.NETWORK_STATE_ANY;
            case CONNECTED:
                return Task.NETWORK_STATE_CONNECTED;
            case UNMETERED:
                return Task.NETWORK_STATE_UNMETERED;
            case NOT_ROAMING:
                return Task.NETWORK_STATE_UNMETERED; // use as fallback, NOT_ROAMING not supported
            default:
                throw new IllegalStateException("not implemented");
        }
    }
}
