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
package com.evernote.android.job.gcm;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobProxyIllegalStateException;
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
@RestrictTo(RestrictTo.Scope.LIBRARY)
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

        scheduleTask(task);

        CAT.d("Scheduled OneoffTask, %s, start %s, end %s (from now), reschedule count %d", request, JobUtil.timeToString(startMs),
                JobUtil.timeToString(endMs), Common.getRescheduleCount(request));
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PeriodicTask task = prepareBuilder(new PeriodicTask.Builder(), request)
                .setPeriod(request.getIntervalMs() / 1_000)
                .setFlex(request.getFlexMs() / 1_000)
                .build();

        scheduleTask(task);

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

        scheduleTask(task);

        CAT.d("Scheduled periodic (flex support), %s, start %s, end %s, flex %s", request, JobUtil.timeToString(startMs),
                JobUtil.timeToString(endMs), JobUtil.timeToString(request.getFlexMs()));
    }

    @Override
    public void cancel(int jobId) {
        try {
            mGcmNetworkManager.cancelTask(createTag(jobId), PlatformGcmService.class);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("The GcmTaskService class you provided")) {
                throw new JobProxyIllegalStateException(e);
            } else {
                throw e;
            }
        }
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        // there is no option to check whether a task is scheduled, assume it is
        return true;
    }

    private void scheduleTask(Task task) {
        try {
            mGcmNetworkManager.schedule(task);
        } catch (IllegalArgumentException e) {
            if (e.getMessage() != null && e.getMessage().startsWith("The GcmTaskService class you provided")) {
                throw new JobProxyIllegalStateException(e);
            } else {
                throw e;
            }
        }
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
