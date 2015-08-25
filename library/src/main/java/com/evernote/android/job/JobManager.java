/*
 * Copyright 2012 Evernote Corporation.
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
package com.evernote.android.job;

import android.Manifest;
import android.app.AlarmManager;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.pm.PackageManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.util.JobApi;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobPreconditions;
import com.google.android.gms.gcm.GcmNetworkManager;

import net.vrallev.android.cat.Cat;
import net.vrallev.android.cat.CatGlobal;

import java.util.Set;

/**
 * Entry point for scheduling jobs. Depending on the platform and SDK version it uses different APIs
 * to schedule jobs. The {@link JobScheduler} is preferred, if the OS is running Lollipop or above.
 * Below Lollipop it uses the {@link GcmNetworkManager}, if the Google Play Services are installed.
 * The {@link AlarmManager} is the fallback.
 *
 * <br>
 * <br>
 *
 * In order to schedule a job you must extend the {@link Job} class. A {@link Job} is a simple class
 * which must provide a public default constructor in order to be instantiated. Thus a {@link Job}
 * doesn't need to be registered in the manifest. Create a {@link JobRequest} with the corresponding
 * {@link JobRequest.Builder}, set your desired parameters and call {@link #schedule(JobRequest)}. If you want
 * to update a pending request, call {@link JobRequest#cancelAndEdit()} on the request, update your
 * parameters and call {@link #schedule(JobRequest)} again.
 *
 * @author rwondratschek
 */
@SuppressWarnings("unused")
public final class JobManager {

    private static final String PACKAGE = JobManager.class.getPackage().getName();

    private static volatile JobManager instance;

    /**
     * @param context Any {@link Context} to instantiate the singleton object. Can be {@code null}
     *                if you are absolutely sure, that the manager was initialized.
     * @return The concrete {@link JobManager} as singleton.
     */
    public static JobManager instance(Context context) {
        if (instance == null) {
            synchronized (JobManager.class) {
                if (instance == null) {
                    JobPreconditions.checkNotNull(context, "Context cannot be null if the JobManager needs to be initialized");
                    CatGlobal.setDefaultCatLogPackage(PACKAGE, new JobCat());

                    if (context.getApplicationContext() != null) {
                        // could be null in unit tests
                        context = context.getApplicationContext();
                    }

                    instance = new JobManager(context);
                }
            }
        }

        return instance;
    }

    private final Context mContext;
    private final JobStorage mJobStorage;
    private final JobExecutor mJobExecutor;

    private JobApi mApi;

    private JobManager(Context context) {
        mContext = context;
        mJobStorage = new JobStorage(context);
        mJobExecutor = new JobExecutor();

        setJobProxy(JobApi.getDefault(mContext));
    }

    protected void setJobProxy(JobApi api) {
        mApi = api;
    }

    /**
     * Schedule a request which will be executed in the future. If you want to update an existing
     * {@link JobRequest}, call {@link JobRequest#cancelAndEdit()}, update your parameters and call
     * this method again. Note that after a {@link JobRequest} was updated, it has a new unique ID.
     * {@code JobRequest}
     *
     * @param request The {@link JobRequest} which will be run in the future.
     */
    public void schedule(JobRequest request) {
        if (request.isSingle()) {
            Set<JobRequest> requests = getAllJobRequestsForClass(request.getJobClass());
            if (!requests.isEmpty()) {
                Cat.i("Tried scheduling job request %d, but request %d for class %s was already scheduled",
                        request.getJobId(), requests.iterator().next().getJobId(), request.getJobClass());
                return;
            }
        }

        mJobStorage.put(request);
        request.setScheduledAt(System.currentTimeMillis());

        JobProxy proxy = getJobProxy(request);
        if (request.isPeriodic()) {
            proxy.plantPeriodic(request);
        } else {
            proxy.plantOneOff(request);
        }
    }

    /**
     * @param jobId The unique ID of the pending {@link JobRequest}.
     * @return The {@link JobRequest} if it's pending or {@code null} otherwise.
     */
    public JobRequest getJobRequest(int jobId) {
        return mJobStorage.get(jobId);
    }

    /**
     * @return All pending JobRequests or an empty set. Never returns {@code null}.
     * @see #getJobRequest(int)
     */
    @NonNull
    public Set<JobRequest> getAllJobRequests() {
        return mJobStorage.getAllJobs(null);
    }

    /**
     * @param clazz The desired class which works as filter.
     * @return All pending JobRequests which would run the job {@code clazz} or an empty set.
     * Never returns {@code null}.
     */
    @NonNull
    public Set<JobRequest> getAllJobRequestsForClass(@NonNull Class<? extends Job> clazz) {
        return mJobStorage.getAllJobs(JobPreconditions.checkNotNull(clazz));
    }

    /**
     * Jobs are cached in memory even if they already have finished. But finished jobs are never
     * restored after the app has launched.
     *
     * @param jobId The unique ID of the running or finished {@link Job}.
     * @return The {@link Job} if it's running or has been finished and is still cached. Returns
     * {@code null} otherwise.
     */
    public Job getJob(int jobId) {
        return mJobExecutor.getJob(jobId);
    }

    /**
     * Jobs are cached in memory even if they already have finished. But finished jobs are never
     * restored after the app has launched.
     *
     * @return All running and cached finished jobs or an empty set. Never returns {@code null}.
     */
    @NonNull
    public Set<Job> getAllJobs() {
        return mJobExecutor.getAllJobs(null);
    }

    /**
     * Jobs are cached in memory even if they already have finished. But finished jobs are never
     * restored after the app has launched.
     *
     * @param clazz The desired class which works as filter.
     * @return All running and cached finished jobs which are instance of {@code clazz} or an empty
     * set. Never return {@code null}.
     */
    public Set<Job> getAllJobsForClass(Class<? extends Job> clazz) {
        return mJobExecutor.getAllJobs(JobPreconditions.checkNotNull(clazz));
    }

    /**
     * <b>WARNING:</b> You shouldn't call this method. It only exists for testing and debugging
     * purposes. The {@link JobManager} automatically decides which API suits best for a {@link Job}.
     *
     * @param api The {@link JobApi} which will be used for future scheduled JobRequests.
     */
    public void forceApi(@NonNull JobApi api) {
        setJobProxy(JobPreconditions.checkNotNull(api));
        Cat.w("Changed API to %s", api);
    }

    /**
     * <b>WARNING:</b> Don't rely your logic on a specific {@link JobApi}. You shouldn't be worrying
     * about it.
     *
     * @return The current {@link JobApi} which will be used for future schedules JobRequests.
     */
    public JobApi getApi() {
        return mApi;
    }

    /**
     * Cancel either the pending {@link JobRequest} or the running {@link Job}.
     *
     * @param jobId The unique ID of the {@link JobRequest} or running {@link Job}.
     * @return {@code true} if a request or job were found and canceled.
     */
    public boolean cancel(int jobId) {
        boolean canceled = false;

        JobRequest request = getJobRequest(jobId);
        if (request != null) {
            Cat.i("Found pending job request %d, canceling", jobId);
            getJobProxy(request).cancel(request);
            getJobStorage().remove(jobId);
            canceled = true;
        }

        Job job = mJobExecutor.getJob(jobId);
        if (job != null && !job.isFinished() && !job.isCanceled()) {
            Cat.i("Cancel running %s", job);
            job.cancel();
            canceled = true;
        }

        return canceled;
    }

    /**
     * Cancel all pending JobRequests and running jobs.
     *
     * @return The count of canceled requests and running jobs.
     */
    public int cancelAll() {
        return cancelAllInner(null);
    }

    /**
     * Cancel all pending JobRequests and running jobs for this {@code clazz}.
     *
     * @param clazz The desired class which works as filter.
     * @return The count of canceled requests and running jobs.
     * @see #getAllJobRequestsForClass(Class)
     * @see #getAllJobsForClass(Class)
     */
    public int cancelAllForClass(Class<? extends Job> clazz) {
        return cancelAllInner(JobPreconditions.checkNotNull(clazz));
    }

    private int cancelAllInner(@Nullable Class<? extends Job> clazz) {
        int canceled = 0;

        Set<JobRequest> requests = clazz == null ? getAllJobRequests() : getAllJobRequestsForClass(clazz);
        for (JobRequest request : requests) {
            if (cancel(request.getJobId())) {
                canceled++;
            }
        }

        Set<Job> jobs = clazz == null ? getAllJobs() : getAllJobsForClass(clazz);
        for (Job job : getAllJobs()) {
            if (!job.isFinished() && !job.isCanceled()) {
                Cat.i("Cancel running %s", job);
                job.cancel();
                canceled++;
            }
        }
        return canceled;
    }

    /**
     * Global switch to enable or disable logging.
     *
     * @param verbose Whether or not to print log messages.
     */
    public void setVerbose(boolean verbose) {
        CatGlobal.setPackageEnabled(PACKAGE, verbose);
    }

    /*package*/ JobStorage getJobStorage() {
        return mJobStorage;
    }

    /*package*/ JobExecutor getJobExecutor() {
        return mJobExecutor;
    }

    /*package*/ boolean hasBootPermission() {
        int result = mContext.getPackageManager()
                .checkPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED, mContext.getPackageName());

        return result == PackageManager.PERMISSION_GRANTED;
    }

    private JobProxy getJobProxy(JobRequest request) {
        return request.getJobApi().getCachedProxy(mContext);
    }
}
