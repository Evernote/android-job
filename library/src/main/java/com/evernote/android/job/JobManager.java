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
package com.evernote.android.job;

import android.annotation.SuppressLint;
import android.app.AlarmManager;
import android.app.Application;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.text.TextUtils;
import android.util.SparseArray;

import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobPreconditions;
import com.evernote.android.job.util.JobUtil;
import com.google.android.gms.gcm.GcmNetworkManager;

import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
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
 * Before you can use the {@code JobManager} you must call {@link #create(Context)} first and add a
 * {@link JobCreator} to map tags to your desired jobs with {@link #addJobCreator(JobCreator)}.
 * It's recommended to do this in the {@link Application#onCreate()} method.
 *
 * <br>
 * <br>
 *
 * In order to schedule a job you must extend the {@link Job} class. A {@link Job} is a simple class
 * with one abstract method which is invoked on a background thread. Thus a {@link Job}
 * doesn't need to be registered in the manifest. Create a {@link JobRequest} with the corresponding
 * {@link JobRequest.Builder}, set your desired parameters and call {@link #schedule(JobRequest)}. If you want
 * to update a pending request, call {@link JobRequest#cancelAndEdit()} on the request, update your
 * parameters and call {@link #schedule(JobRequest)} again.
 *
 * @author rwondratschek
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public final class JobManager {

    private static final JobCat CAT = new JobCat("JobManager");

    @SuppressLint("StaticFieldLeak")
    private static volatile JobManager instance;

    /**
     * Initializes the singleton. It's necessary to call this function before using the {@code JobManager}.
     * Calling it multiple times has not effect.
     *
     * @param context Any {@link Context} to instantiate the singleton object.
     * @return The new or existing singleton object.
     * @throws JobManagerCreateException When the singleton couldn't be created.
     */
    public static JobManager create(@NonNull Context context) throws JobManagerCreateException {
        if (instance == null) {
            synchronized (JobManager.class) {
                if (instance == null) {
                    JobPreconditions.checkNotNull(context, "Context cannot be null");

                    if (context.getApplicationContext() != null) {
                        // could be null in unit tests
                        context = context.getApplicationContext();
                    }

                    JobApi api = JobApi.getDefault(context);
                    if (api == JobApi.V_14 && !api.isSupported(context)) {
                        throw new JobManagerCreateException("All APIs are disabled, cannot schedule any job");
                    }

                    instance = new JobManager(context);

                    if (!JobUtil.hasWakeLockPermission(context)) {
                        CAT.w("No wake lock permission");
                    }
                    if (!JobUtil.hasBootPermission(context)) {
                        CAT.w("No boot permission");
                    }

                    sendAddJobCreatorIntent(context);
                }
            }
        }

        return instance;
    }

    /**
     * Ensure that you've called {@link #create(Context)} first. Otherwise this method
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
    private final JobCreatorHolder mJobCreatorHolder;
    private final JobExecutor mJobExecutor;

    private volatile JobStorage mJobStorage;
    private final CountDownLatch mJobStorageLatch;

    private JobManager(final Context context) {
        mContext = context;
        mJobCreatorHolder = new JobCreatorHolder();
        mJobExecutor = new JobExecutor();

        if (!JobConfig.isSkipJobReschedule()) {
            JobRescheduleService.startService(mContext);
        }

        mJobStorageLatch = new CountDownLatch(1);
        new Thread("AndroidJob-storage-init") {
            @Override
            public void run() {
                mJobStorage = new JobStorage(context);
                mJobStorageLatch.countDown();
            }
        }.start();
    }

    /**
     * Schedule a request which will be executed in the future. If you want to update an existing
     * {@link JobRequest}, call {@link JobRequest#cancelAndEdit()}, update your parameters and call
     * this method again. Calling this method with the same request multiple times without cancelling
     * it is idempotent.
     *
     * @param request The {@link JobRequest} which will run in the future.
     */
    public synchronized void schedule(@NonNull JobRequest request) {
        // call must be synchronized, otherwise with isUpdateCurrent() true it's possible to end up in a race condition with multiple jobs scheduled

        if (mJobCreatorHolder.isEmpty()) {
            CAT.w("you haven't registered a JobCreator with addJobCreator(), it's likely that your job never will be executed");
        }

        if (request.getScheduledAt() > 0) {
            return;
        }

        if (request.isUpdateCurrent()) {
            cancelAllForTag(request.getTag());
        }

        JobProxy.Common.cleanUpOrphanedJob(mContext, request.getJobId());

        JobApi jobApi = request.getJobApi();
        boolean periodic = request.isPeriodic();
        boolean flexSupport = periodic && jobApi.isFlexSupport() && request.getFlexMs() < request.getIntervalMs();

        request.setScheduledAt(JobConfig.getClock().currentTimeMillis());
        request.setFlexSupport(flexSupport);
        getJobStorage().put(request);

        try {
            scheduleWithApi(request, jobApi, periodic, flexSupport);
            return;
        } catch (JobProxyIllegalStateException e) {
            // try again below, the other cases stop

        } catch (Exception e) {
            // if something fails, don't keep the job in the database, it would be rescheduled later
            getJobStorage().remove(request);
            throw e;
        }

        try {
            // try to reload the proxy
            jobApi.invalidateCachedProxy();

            scheduleWithApi(request, jobApi, periodic, flexSupport);
            return;
        } catch (Exception e) {
            if (jobApi == JobApi.V_14 || jobApi == JobApi.V_19) {
                // at this stage we cannot do anything
                getJobStorage().remove(request);
                throw e;
            } else {
                jobApi = JobApi.V_19.isSupported(mContext) ? JobApi.V_19 : JobApi.V_14; // try one last time
            }
        }

        try {
            scheduleWithApi(request, jobApi, periodic, flexSupport);
        } catch (Exception e) {
            // if something fails, don't keep the job in the database, it would be rescheduled later
            getJobStorage().remove(request);
            throw e;
        }
    }

    private void scheduleWithApi(JobRequest request, JobApi jobApi, boolean periodic, boolean flexSupport) {
        JobProxy proxy = getJobProxy(jobApi);
        if (periodic) {
            if (flexSupport) {
                proxy.plantPeriodicFlexSupport(request);
            } else {
                proxy.plantPeriodic(request);
            }
        } else {
            proxy.plantOneOff(request);
        }
    }

    /**
     * @param jobId The unique ID of the pending {@link JobRequest}.
     * @return The {@link JobRequest} if it's pending or {@code null} otherwise.
     */
    public JobRequest getJobRequest(int jobId) {
        JobRequest request = getJobRequest(jobId, false);
        if (request != null && request.isTransient() && !request.getJobApi().getProxy(mContext).isPlatformJobScheduled(request)) {
            getJobStorage().remove(request);
            return null;
        } else {
            return request;
        }
    }

    /*package*/ JobRequest getJobRequest(int jobId, boolean includeStarted) {
        JobRequest jobRequest = getJobStorage().get(jobId);
        if (!includeStarted && jobRequest != null && jobRequest.isStarted()) {
            return null;
        } else {
            return jobRequest;
        }
    }

    /**
     * @return A duplicate {@link Set} containing all pending JobRequests or an empty set.
     * Never returns {@code null}. The set may be modified without direct effects to the actual
     * backing store.
     * @see #getJobRequest(int)
     */
    @NonNull
    public Set<JobRequest> getAllJobRequests() {
        return getAllJobRequests(null, false, true);
    }

    /**
     * @param tag The tag of the pending requests.
     * @return A duplicate {@link Set} containing all pending JobRequests associated with this
     * {@code tag} or an empty set. Never returns {@code null}. The set may be modified without
     * direct effects to the actual backing store.
     */
    public Set<JobRequest> getAllJobRequestsForTag(@NonNull String tag) {
        return getAllJobRequests(tag, false, true);
    }

    /*package*/ Set<JobRequest> getAllJobRequests(@Nullable String tag, boolean includeStarted, boolean cleanUpTransient) {
        Set<JobRequest> requests = getJobStorage().getAllJobRequests(tag, includeStarted);

        if (cleanUpTransient) {
            Iterator<JobRequest> iterator = requests.iterator();
            while (iterator.hasNext()) {
                JobRequest request = iterator.next();
                if (request.isTransient() && !request.getJobApi().getProxy(mContext).isPlatformJobScheduled(request)) {
                    getJobStorage().remove(request);
                    iterator.remove();
                }
            }
        }

        return requests;
    }

    /**
     * Jobs are cached in memory even if they already have finished. But finished jobs are never
     * restored after the app has launched. Since finished jobs could cause memory leaks, they wrapped
     * inside of a {@link WeakReference} and can be removed from memory. If you need to know the results
     * of finished jobs or whether a job has been run, you can call {@link #getAllJobResults()}.
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
     * restored after the app has relaunched. Since finished jobs could cause memory leaks, they wrapped
     * inside of a {@link WeakReference} and can be removed from memory. If you need to know the results
     * of finished jobs or whether a job has been run, you can call {@link #getAllJobResults()}.
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
     * restored after the app has relaunched. Since finished jobs could cause memory leaks, they wrapped
     * inside of a {@link WeakReference} and can be removed from memory. If you need to know the results
     * of finished jobs or whether a job has been run, you can call {@link #getAllJobResults()}.
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
     * Finished jobs are kept in memory until the garbage collector cleans them up. This method returns
     * the results of all finished jobs even after they have been cleaned up. However, neither finished jobs
     * nor their results are restored after the app has been relaunched.
     *
     * @return The results of all finished jobs. They key is the corresponding job ID.
     */
    @NonNull
    public SparseArray<Job.Result> getAllJobResults() {
        return mJobExecutor.getAllJobResults();
    }

    /**
     * Cancel either the pending {@link JobRequest} or the running {@link Job}.
     *
     * @param jobId The unique ID of the {@link JobRequest} or running {@link Job}.
     * @return {@code true} if a request or job were found and canceled.
     */
    public boolean cancel(int jobId) {
        // call both methods
        boolean result = cancelInner(getJobRequest(jobId, true)) | cancelInner(getJob(jobId));
        JobProxy.Common.cleanUpOrphanedJob(mContext, jobId); // do this as well, just in case
        return result;
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
            getJobProxy(request.getJobApi()).cancel(request.getJobId());
            getJobStorage().remove(request);
            request.setScheduledAt(0); // reset value
            return true;
        } else {
            return false;
        }
    }

    private boolean cancelInner(@Nullable Job job) {
        if (job != null && job.cancel(true)) {
            CAT.i("Cancel running %s", job);
            return true;
        } else {
            return false;
        }
    }

    private synchronized int cancelAllInner(@Nullable String tag) {
        int canceled = 0;

        Set<JobRequest> requests = getAllJobRequests(tag, true, false);
        for (JobRequest request : requests) {
            if (cancelInner(request)) {
                canceled++;
            }
        }

        //noinspection ConstantConditions
        Set<Job> jobs = TextUtils.isEmpty(tag) ? getAllJobs() : getAllJobsForTag(tag);
        for (Job job : jobs) {
            if (cancelInner(job)) {
                canceled++;
            }
        }
        return canceled;
    }

    /**
     * Registers this instance to create jobs for a specific tag. It's possible to have multiple
     * {@link JobCreator}s with a first come first serve order.
     *
     * @param jobCreator The mapping between a specific job tag and the job class.
     */
    public void addJobCreator(JobCreator jobCreator) {
        mJobCreatorHolder.addJobCreator(jobCreator);
    }

    /**
     * Remove the mapping to stop it from creating new jobs.
     *
     * @param jobCreator The mapping between a specific job tag and the job class.
     */
    public void removeJobCreator(JobCreator jobCreator) {
        mJobCreatorHolder.removeJobCreator(jobCreator);
    }

    @NonNull
    /*package*/ JobStorage getJobStorage() {
        if (mJobStorage == null) {
            try {
                mJobStorageLatch.await(20, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }
        if (mJobStorage == null) {
            throw new IllegalStateException("Job storage shouldn't be null");
        }

        return mJobStorage;
    }

    /*package*/ JobExecutor getJobExecutor() {
        return mJobExecutor;
    }

    /*package*/ JobCreatorHolder getJobCreatorHolder() {
        return mJobCreatorHolder;
    }

    /*package*/ Context getContext() {
        return mContext;
    }

    /*package*/ void destroy() {
        synchronized (JobManager.class) {
            instance = null;
            for (JobApi api : JobApi.values()) {
                api.invalidateCachedProxy();
            }
        }
    }

    /*package*/ JobProxy getJobProxy(JobApi api) {
        return api.getProxy(mContext);
    }

    private static void sendAddJobCreatorIntent(@NonNull Context context) {
        final String myPackage = context.getPackageName();

        Intent intent = new Intent(JobCreator.ACTION_ADD_JOB_CREATOR);
        intent.setPackage(myPackage);

        List<ResolveInfo> resolveInfos;
        try {
            resolveInfos = context.getPackageManager().queryBroadcastReceivers(intent, 0);
        } catch (Exception e) {
            // just in case this crashes, skip the intent, most apps don't use this mechanism anyways
            // this also prevents crash loops (package manager has died)
            resolveInfos = Collections.emptyList();
        }

        for (ResolveInfo resolveInfo : resolveInfos) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null || activityInfo.exported || !myPackage.equals(activityInfo.packageName)
                    || TextUtils.isEmpty(activityInfo.name)) {
                continue;
            }

            try {
                JobCreator.AddJobCreatorReceiver receiver =
                        (JobCreator.AddJobCreatorReceiver) Class.forName(activityInfo.name).newInstance();

                receiver.addJobCreator(context, instance);
            } catch (Exception ignored) {
            }
        }
    }
}
