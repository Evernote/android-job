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
package com.evernote.android.job;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Application;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;

import com.evernote.android.job.util.JobApi;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobPreconditions;
import com.google.android.gms.gcm.GcmNetworkManager;

import net.vrallev.android.cat.Cat;
import net.vrallev.android.cat.CatGlobal;
import net.vrallev.android.cat.CatLog;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Entry point for scheduling jobs. Depending on the platform and SDK version it uses different APIs
 * to schedule jobs. The {@link JobScheduler} is preferred, if the OS is running Lollipop or above.
 * Otherwise it uses the {@link AlarmManager} as fallback. It's also possible to use the
 * {@link GcmNetworkManager}, if the manager can be found in your classpath, the Google Play Services
 * are installed and the service was added in the manifest. Take a look at the
 * <a href="https://github.com/evernote/android-job#using-the-gcmnetworkmanager">README</a> for more
 * help.
 *
 * <br>
 * <br>
 *
 * Before you can use the {@code JobManager} you must call {@link #create(Context, JobCreator)} first.
 * It's recommended to do this in the {@link Application#onCreate()} method.
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
    private static final CatLog CAT = new JobCat("JobManager");

    private static volatile JobManager instance;

    /**
     * Initializes the singleton. It's necessary to call this function before using the {@code JobManager}.
     * Calling it multiple times has not effect.
     *
     * @param context Any {@link Context} to instantiate the singleton object.
     * @param jobCreator The mapping between a specific job tag and the job class.
     * @return The new or existing singleton object.
     */
    public static JobManager create(Context context, JobCreator jobCreator) {
        if (instance == null) {
            synchronized (JobManager.class) {
                if (instance == null) {
                    JobPreconditions.checkNotNull(context, "Context cannot be null");
                    JobPreconditions.checkNotNull(jobCreator, "JobCreator cannot be null");
                    CatGlobal.setDefaultCatLogPackage(PACKAGE, new JobCat());

                    if (context.getApplicationContext() != null) {
                        // could be null in unit tests
                        context = context.getApplicationContext();
                    }

                    instance = new JobManager(context, jobCreator);

                    if (!instance.hasWakeLockPermission()) {
                        Cat.w("No wake lock permission");
                    }
                    if (!instance.hasBootPermission()) {
                        Cat.w("No boot permission");
                    }
                }
            }
        }

        return instance;
    }

    /**
     * Ensure that you've called {@link #create(Context, JobCreator)} first. Otherwise this method
     * throws an exception.
     *
     * @return The {@code JobManager} object.
     */
    public static JobManager instance() {
        if (instance == null) {
            synchronized (JobManager.class) {
                if (instance == null) {
                    throw new IllegalStateException("You need to call create() at least once to create the singleton");
                }
            }
        }

        return instance;
    }

    private final Context mContext;
    private final JobCreator mJobCreator;
    private final JobStorage mJobStorage;
    private final JobExecutor mJobExecutor;

    private JobApi mApi;

    private JobManager(Context context, JobCreator jobCreator) {
        mContext = context;
        mJobCreator = jobCreator;
        mJobStorage = new JobStorage(context);
        mJobExecutor = new JobExecutor();

        setJobProxy(JobApi.getDefault(mContext));

        new Thread() {
            @Override
            public void run() {
                rescheduleTasksIfNecessary();
            }
        }.start();
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
        request.setScheduledAt(System.currentTimeMillis());
        mJobStorage.put(request);

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
     * @return A duplicate {@link Set} containing all pending JobRequests or an empty set.
     * Never returns {@code null}. The set may be modified without direct effects to the actual
     * backing store.
     * @see #getJobRequest(int)
     */
    @NonNull
    public Set<JobRequest> getAllJobRequests() {
        return mJobStorage.getAllJobRequests();
    }

    /**
     * @param tag The tag of the pending requests.
     * @return A duplicate {@link Set} containing all pending JobRequests associated with this
     * {@code tag} or an empty set. Never returns {@code null}. The set may be modified without
     * direct effects to the actual backing store.
     */
    public Set<JobRequest> getAllJobRequestsForTag(@NonNull String tag) {
        return mJobStorage.getAllJobRequestsForTag(tag);
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
     * restored after the app has relaunched.
     *
     * @return A duplicate {@link Set} containing all running and cached finished jobs or an empty set.
     * Never returns {@code null}. The set may be modified without direct effects to the actual
     * backing store.
     */
    @NonNull
    public Set<Job> getAllJobs() {
        return mJobExecutor.getAllJobs();
    }

    /**
     * Jobs are cached in memory even if they already have finished. But finished jobs are never
     * restored after the app has relaunched.
     *
     * @param tag The tag of the running or finished jobs.
     * @return A duplicate {@link Set} containing all running and cached finished jobs associated with
     * this tag or an empty set. Never returns {@code null}. The set may be modified without direct
     * effects to the actual backing store.
     */
    @NonNull
    public Set<Job> getAllJobsForTag(@NonNull String tag) {
        return mJobExecutor.getAllJobsForTag(tag);
    }

    /**
     * <b>WARNING:</b> You shouldn't call this method. It only exists for testing and debugging
     * purposes. The {@link JobManager} automatically decides which API suits best for a {@link Job}.
     *
     * @param api The {@link JobApi} which will be used for future scheduled JobRequests.
     */
    public void forceApi(@NonNull JobApi api) {
        setJobProxy(JobPreconditions.checkNotNull(api));
        CAT.w("Changed API to %s", api);
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
        // call both methods
        return cancelInner(getJobRequest(jobId)) | cancelInner(getJob(jobId));
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
     * Cancel all pending JobRequests and running jobs.
     *
     * @param tag The tag of the pending job requests and running jobs.
     * @return The count of canceled requests and running jobs.
     */
    public int cancelAllForTag(@NonNull String tag) {
        return cancelAllInner(tag);
    }

    private boolean cancelInner(@Nullable JobRequest request) {
        if (request != null) {
            CAT.i("Found pending job %s, canceling", request);
            getJobProxy(request).cancel(request);
            getJobStorage().remove(request);
            return true;
        } else {
            return false;
        }
    }

    private boolean cancelInner(@Nullable Job job) {
        if (job != null && !job.isFinished() && !job.isCanceled()) {
            CAT.i("Cancel running %s", job);
            job.cancel();
            return true;
        } else {
            return false;
        }
    }

    private int cancelAllInner(@Nullable String tag) {
        int canceled = 0;

        Set<JobRequest> requests = TextUtils.isEmpty(tag) ? getAllJobRequests() : getAllJobRequestsForTag(tag);
        for (JobRequest request : requests) {
            if (cancelInner(request)) {
                canceled++;
            }
        }

        Set<Job> jobs = TextUtils.isEmpty(tag) ? getAllJobs() : getAllJobsForTag(tag);
        for (Job job : jobs) {
            if (cancelInner(job)) {
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

    /*package*/ JobCreator getJobCreator() {
        return mJobCreator;
    }

    /*package*/ boolean hasBootPermission() {
        int result = mContext.getPackageManager()
                .checkPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED, mContext.getPackageName());

        return result == PackageManager.PERMISSION_GRANTED;
    }

    /*package*/ boolean hasWakeLockPermission() {
        int result = mContext.getPackageManager()
                .checkPermission(Manifest.permission.WAKE_LOCK, mContext.getPackageName());

        return result == PackageManager.PERMISSION_GRANTED;
    }

    private JobProxy getJobProxy(JobRequest request) {
        return request.getJobApi().getCachedProxy(mContext);
    }

    private void rescheduleTasksIfNecessary() {
        PowerManager powerManager = (PowerManager) mContext.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, JobManager.class.getName());

        try {
            if (hasWakeLockPermission()) {
                wakeLock.acquire(TimeUnit.SECONDS.toMillis(3));
            }

            /*
             * Delay this slightly. This avoids a race condition if the app was launched by the
             * AlarmManager. Then the alarm was already removed, but the JobRequest might still
             * be available in the storage. We still catch this case, because we never execute
             * a job with the same ID twice. However, the still save resources with the delay.
             */
            SystemClock.sleep(10_000L);

            Set<JobRequest> requests = JobManager.instance().getAllJobRequests();

            int rescheduledCount = 0;
            for (JobRequest request : requests) {
                if (!getJobProxy(request).isPlatformJobScheduled(request)) {
                    // update execution window
                    request.cancelAndEdit()
                            .build()
                            .schedule();

                    rescheduledCount++;
                }
            }

            CAT.d("Reschedule %d jobs of %d jobs", rescheduledCount, requests.size());

        } finally {
            if (wakeLock.isHeld()) {
                wakeLock.release();
            }
        }
    }
}
