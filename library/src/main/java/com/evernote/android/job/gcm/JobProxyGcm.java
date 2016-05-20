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

import com.evernote.android.job.util.JobCat;
import com.google.android.gms.gcm.GcmNetworkManager;
import com.google.android.gms.gcm.OneoffTask;
import com.google.android.gms.gcm.PeriodicTask;
import com.google.android.gms.gcm.Task;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobUtil;

import net.vrallev.android.cat.CatLog;

/**
 * @author rwondratschek
 */
public class JobProxyGcm implements JobProxy {

    private static final CatLog CAT = new JobCat("JobProxyGcm");

    /*
     * Requires charging doesn't work reliable. Like the documentation says, the job doesn't run if
     * not plugged in. However, the job never runs until you schedule a new one although the device is
     * already plugged in again.
     */

    private final GcmNetworkManager mGcmNetworkManager;

    public JobProxyGcm(Context context) {
        mGcmNetworkManager = GcmNetworkManager.getInstance(context);
    }

    @Override
    public void plantOneOff(JobRequest request) {
        OneoffTask task = new OneoffTask.Builder()
                .setTag(createTag(request))
                .setService(PlatformGcmService.class)
                .setUpdateCurrent(true)
                .setExecutionWindow(Common.getStartMs(request) / 1_000, Common.getEndMs(request) / 1_000)
                .setRequiredNetwork(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(request.isPersisted())
                .setRequiresCharging(request.requiresCharging())
                .build();

        mGcmNetworkManager.schedule(task);

        CAT.d("Scheduled OneoffTask, %s, start %s, end %s", request,
                JobUtil.timeToString(Common.getStartMs(request)), JobUtil.timeToString(Common.getEndMs(request)));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PeriodicTask task = new PeriodicTask.Builder()
                .setTag(createTag(request))
                .setService(PlatformGcmService.class)
                .setUpdateCurrent(true)
                .setPeriod(request.getIntervalMs() / 1_000)
                .setRequiredNetwork(convertNetworkType(request.requiredNetworkType()))
                .setPersisted(request.isPersisted())
                .setRequiresCharging(request.requiresCharging())
                .build();

        mGcmNetworkManager.schedule(task);

        CAT.d("Scheduled PeriodicTask, %s, interval %s", request, JobUtil.timeToString(request.getIntervalMs()));
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
            default:
                throw new IllegalStateException("not implemented");
        }
    }
}
