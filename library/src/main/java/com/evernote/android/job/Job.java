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

import android.app.Service;
import android.content.Context;
import android.os.Bundle;
import android.os.PowerManager.WakeLock;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.evernote.android.job.util.Device;
import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import java.lang.ref.WeakReference;

/**
 * Base class for running delayed jobs. A {@link Job} is executed in a background thread.
 *
 * @author rwondratschek
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public abstract class Job {

    private static final JobCat CAT = new JobCat("Job");

    public enum Result {
        /**
         * Indicates that {@link #onRunJob(Params)} was successful.
         */
        SUCCESS,
        /**
         * Indicates that {@link #onRunJob(Params)} failed, but the {@link Job} shouldn't be rescheduled.
         *
         * <br>
         * <br>
         *
         * Periodic jobs will continue to run. The failure count of the job is incremented, what
         * can be helpful in the next interval.
         *
         * @see Params#getFailureCount()
         */
        FAILURE,
        /**
         * Indicates that {@link #onRunJob(Params)} failed and the {@link Job} should be rescheduled
         * with the defined back-off criteria. Note that returning {@code RESCHEDULE} for a periodic
         * {@link Job} is invalid and ignored.
         *
         * <br>
         * <br>
         *
         * Returning RESCHEDULE for periodic jobs has the same effect as returning FAILURE.
         */
        RESCHEDULE
    }

    private Params mParams;
    private WeakReference<Context> mContextReference;
    private Context mApplicationContext;

    private volatile boolean mCanceled;
    private volatile boolean mDeleted;
    volatile long mFinishedTimeStamp = -1;

    private Result mResult = Result.FAILURE;

    private final Object mMonitor = new Object();

    /**
     * This method is invoked from a background thread. You should run your desired task here.
     * This method is thread safe. Each time a job starts executing a new instance of your {@link Job}
     * is instantiated. You can identify your {@link Job} with the passed {@code params}.
     *
     * <br>
     * <br>
     *
     * You should call {@link #isCanceled()} frequently for long running jobs and stop your
     * task if necessary.
     *
     * <br>
     * <br>
     *
     * A {@link WakeLock} is acquired for 3 minutes for each {@link Job}. If your task
     * needs more time, then you need to create an extra {@link WakeLock}.
     *
     * @param params The parameters for this concrete job.
     * @return The result of this {@link Job}. Note that returning {@link Result#RESCHEDULE} for a periodic
     * {@link Job} is invalid and ignored.
     */
    @NonNull
    @WorkerThread
    protected abstract Result onRunJob(@NonNull Params params);

    /**
     * This method is intended to be overwritten. It is called once when the job is still running, but was
     * canceled. This can happen when the system wants to stop the job or if you manually cancel the job
     * yourself. It's a good indicator to stop your work and maybe retry your job later again. Alternatively,
     * you can also call {@link #isCanceled()}.
     *
     * @see #isCanceled()
     */
    protected void onCancel() {
        // override me
    }

    /*package*/ final Result runJob() {
        try {
            // daily jobs check the requirements manually
            if (this instanceof DailyJob || meetsRequirements(true)) {
                mResult = onRunJob(getParams());
            } else {
                mResult = getParams().isPeriodic() ? Result.FAILURE : Result.RESCHEDULE;
            }

            return mResult;

        } finally {
            mFinishedTimeStamp = System.currentTimeMillis();
        }
    }

    /**
     * This method is called if you returned {@link Result#RESCHEDULE} in {@link #onRunJob(Params)}
     * and the {@link Job} was successfully rescheduled. The new rescheduled {@link JobRequest} has
     * a new ID. Override this method if you want to be notified about the change.
     *
     * @param newJobId The new ID of the rescheduled {@link JobRequest}.
     */
    @SuppressWarnings("UnusedParameters")
    @WorkerThread
    protected void onReschedule(int newJobId) {
        // override me
    }

    /**
     * Checks all requirements for this job. It's also possible to check all requirements separately
     * with the corresponding methods.
     *
     * @return Whether all set requirements are met.
     */
    protected boolean meetsRequirements() {
        return meetsRequirements(false);
    }

    /*package*/ boolean meetsRequirements(boolean checkRequirementsEnforced) {
        if (checkRequirementsEnforced && !getParams().getRequest().requirementsEnforced()) {
            return true;
        }

        if (!isRequirementChargingMet()) {
            CAT.w("Job requires charging, reschedule");
            return false;
        }
        if (!isRequirementDeviceIdleMet()) {
            CAT.w("Job requires device to be idle, reschedule");
            return false;
        }
        if (!isRequirementNetworkTypeMet()) {
            CAT.w("Job requires network to be %s, but was %s", getParams().getRequest().requiredNetworkType(),
                    Device.getNetworkType(getContext()));
            return false;
        }
        if (!isRequirementBatteryNotLowMet()) {
            CAT.w("Job requires battery not be low, reschedule");
            return false;
        }

        if (!isRequirementStorageNotLowMet()) {
            CAT.w("Job requires storage not be low, reschedule");
            return false;
        }

        return true;
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be charging and it isn't charging.
     * Otherwise always returns {@code true}.
     */
    protected boolean isRequirementChargingMet() {
        return !(getParams().getRequest().requiresCharging() && !Device.getBatteryStatus(getContext()).isCharging());
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be idle and it isn't idle. Otherwise
     * always returns {@code true}.
     */
    protected boolean isRequirementDeviceIdleMet() {
        return !(getParams().getRequest().requiresDeviceIdle() && !Device.isIdle(getContext()));
    }

    /**
     * @return Whether the battery not low requirement is met. That's true either if it's not a requirement
     * or if the battery actually isn't low. The battery is low, if less than 15% are left and the device isn't
     * charging.
     */
    protected boolean isRequirementBatteryNotLowMet() {
        return !(getParams().getRequest().requiresBatteryNotLow() && Device.getBatteryStatus(getContext()).isBatteryLow());
    }

    /**
     * @return Whether the storage not low requirement is met. That's true either if it's not a requirement
     * or if the storage actually isn't low.
     */
    protected boolean isRequirementStorageNotLowMet() {
        return !(getParams().getRequest().requiresStorageNotLow() && Device.isStorageLow());
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be in a specific network state and it
     * isn't in this state. Otherwise always returns {@code true}.
     */
    protected boolean isRequirementNetworkTypeMet() {
        JobRequest.NetworkType requirement = getParams().getRequest().requiredNetworkType();
        if (requirement == JobRequest.NetworkType.ANY) {
            return true;
        }

        JobRequest.NetworkType current = Device.getNetworkType(getContext());

        switch (requirement) {
            case CONNECTED:
                return current != JobRequest.NetworkType.ANY;
            case NOT_ROAMING:
                return current == JobRequest.NetworkType.NOT_ROAMING || current == JobRequest.NetworkType.UNMETERED || current == JobRequest.NetworkType.METERED;
            case UNMETERED:
                return current == JobRequest.NetworkType.UNMETERED;
            case METERED:
                return current == JobRequest.NetworkType.CONNECTED || current == JobRequest.NetworkType.NOT_ROAMING;
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    /*package*/ final Job setRequest(JobRequest request, @NonNull Bundle transientExtras) {
        mParams = new Params(request, transientExtras);
        return this;
    }

    /**
     * @return The same parameters like passed to {@link #onRunJob(Params)}.
     */
    @NonNull
    protected final Params getParams() {
        return mParams;
    }

    /*package*/ final Job setContext(Context context) {
        mContextReference = new WeakReference<>(context);
        mApplicationContext = context.getApplicationContext();
        return this;
    }

    /**
     * @return The {@link Context} running this {@link Job}. In most situations it's a {@link Service}.
     * If this context already was destroyed for some reason, then the application context is returned.
     * Never returns {@code null}.
     */
    @NonNull
    protected final Context getContext() {
        Context context = mContextReference.get();
        return context == null ? mApplicationContext : context;
    }

    /**
     * Cancel this {@link Job} if it hasn't finished, yet.
     */
    public final void cancel() {
        cancel(false);
    }

    /*package*/ final boolean cancel(boolean deleted) {
        synchronized (mMonitor) {
            if (!isFinished()) {
                if (!mCanceled) {
                    mCanceled = true;
                    onCancel();
                }
                mDeleted |= deleted;
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * @return {@code true} if this {@link Job} was canceled.
     */
    protected final boolean isCanceled() {
        synchronized (mMonitor) {
            return mCanceled;
        }
    }

    /**
     * @return {@code true} if the {@link Job} finished.
     */
    public final boolean isFinished() {
        synchronized (mMonitor) {
            return mFinishedTimeStamp > 0;
        }
    }

    /*package*/ final long getFinishedTimeStamp() {
        synchronized (mMonitor) {
            return mFinishedTimeStamp;
        }
    }

    /*package*/ final Result getResult() {
        return mResult;
    }

    /*package*/ final boolean isDeleted() {
        synchronized (mMonitor) {
            return mDeleted;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Job job = (Job) o;

        return mParams.equals(job.mParams);
    }

    @Override
    public int hashCode() {
        return mParams.hashCode();
    }

    @Override
    public String toString() {
        return "job{"
                + "id=" + mParams.getId()
                + ", finished=" + isFinished()
                + ", result=" + mResult
                + ", canceled=" + mCanceled
                + ", periodic=" + mParams.isPeriodic()
                + ", class=" + getClass().getSimpleName()
                + ", tag=" + mParams.getTag()
                + '}';
    }

    /**
     * Holds several parameters for the {@link Job} execution.
     */
    public static final class Params {

        private final JobRequest mRequest;
        private PersistableBundleCompat mExtras;
        private Bundle mTransientExtras;

        private Params(@NonNull JobRequest request, @NonNull Bundle transientExtras) {
            mRequest = request;
            mTransientExtras = transientExtras;
        }

        /**
         * @return The unique ID for this {@link Job}.
         * @see JobRequest#getJobId()
         */
        public int getId() {
            return mRequest.getJobId();
        }

        /**
         * @return The tag for this {@link Job} which was passed in the constructor of the {@link JobRequest.Builder}.
         * @see JobRequest#getTag()
         */
        public String getTag() {
            return mRequest.getTag();
        }

        /**
         * @return Whether this {@link Job} is periodic or not. If this {@link Job} is periodic, then
         * you shouldn't return {@link Result#RESCHEDULE} as result.
         * @see JobRequest#isPeriodic()
         */
        public boolean isPeriodic() {
            return mRequest.isPeriodic();
        }

        /**
         * @return {@code true} if this job was scheduled at an exact time by calling {@link JobRequest.Builder#setExact(long)}.
         * @see JobRequest#isExact()
         */
        public boolean isExact() {
            return mRequest.isExact();
        }

        /**
         * Only valid if the job isn't periodic.
         *
         * @return The start of the time frame when the job will run after it's been scheduled.
         * @see JobRequest#getStartMs()
         */
        public long getStartMs() {
            return mRequest.getStartMs();
        }

        /**
         * Only valid if the job isn't periodic.
         *
         * @return The end of the time frame when the job will run after it's been scheduled.
         * @see JobRequest#getEndMs()
         */
        public long getEndMs() {
            return mRequest.getEndMs();
        }

        /**
         * Only valid if the job is periodic.
         *
         * @return The interval in which the job runs once.
         * @see JobRequest#getIntervalMs()
         */
        public long getIntervalMs() {
            return mRequest.getIntervalMs();
        }

        /**
         * Flex time for this job. Only valid if this is a periodic job. The job can execute
         * at any time in a window of flex length at the end of the period.
         *
         * @return How close to the end of an interval a periodic job is allowed to run.
         * @see JobRequest#getFlexMs()
         */
        public long getFlexMs() {
            return mRequest.getFlexMs();
        }

        /**
         * Returns the time when this job was scheduled.
         * <br>
         * <br>
         * <b>Note</b> that this value is only useful for non-periodic jobs. The time for periodic
         * jobs is inconsistent. Sometimes it will return the value when the periodic job was scheduled
         * for the first time and sometimes it will be updated after each period. The reason for this
         * limitation is the flex parameter, which was backported to older Android versions. You can
         * only rely on this value during the first interval of the periodic job.
         *
         * @return The time when the job was scheduled.
         */
        public long getScheduledAt() {
            return mRequest.getScheduledAt();
        }

        /**
         * Only valid if the job isn't periodic.
         *
         * @return The initial back-off time which is increasing depending on the {@link #getBackoffPolicy()}
         * if the job fails multiple times.
         * @see JobRequest#getBackoffMs()
         */
        public long getBackoffMs() {
            return mRequest.getBackoffMs();
        }

        /**
         * Only valid if the job isn't periodic.
         *
         * @return The back-off policy if a job failed and is rescheduled.
         * @see JobRequest#getBackoffPolicy()
         */
        public JobRequest.BackoffPolicy getBackoffPolicy() {
            return mRequest.getBackoffPolicy();
        }

        /**
         * Call {@link #isRequirementChargingMet()} to check whether this requirement is fulfilled.
         *
         * @return If {@code true}, then the job should only run if the device is charging.
         * @see JobRequest#requiresCharging()
         */
        public boolean requiresCharging() {
            return mRequest.requiresCharging();
        }

        /**
         * Call {@link #isRequirementDeviceIdleMet()} to check whether this requirement is fulfilled.
         *
         * @return If {@code true}, then job should only run if the device is idle.
         * @see JobRequest#requiresDeviceIdle()
         */
        public boolean requiresDeviceIdle() {
            return mRequest.requiresDeviceIdle();
        }

        /**
         * Call {@link #isRequirementNetworkTypeMet()} to check whether this requirement is fulfilled.
         *
         * @return The network state which is required to run the job.
         * @see JobRequest#requiredNetworkType()
         */
        public JobRequest.NetworkType requiredNetworkType() {
            return mRequest.requiredNetworkType();
        }

        /**
         * @return If {@code true}, then the job should only run if the battery isn't low.
         */
        public boolean requiresBatteryNotLow() {
            return mRequest.requiresBatteryNotLow();
        }

        /**
         * @return If {@code true}, then the job should only run if the battery isn't low.
         */
        public boolean requiresStorageNotLow() {
            return mRequest.requiresStorageNotLow();
        }

        /**
         * @return If {@code true}, then all requirements are checked before the job runs. If one requirement
         * isn't met, then the job is rescheduled right away.
         * @see JobRequest#requirementsEnforced()
         */
        public boolean requirementsEnforced() {
            return mRequest.requirementsEnforced();
        }

        /**
         * The failure count increases if a non periodic {@link Job} was rescheduled or if a periodic
         * {@link Job} wasn't successful.
         *
         * @return How often the job already has failed.
         */
        public int getFailureCount() {
            return mRequest.getFailureCount();
        }

        /**
         * Returns the time the job did run the last time. This is only useful for periodic jobs, daily jobs
         * or jobs which were rescheduled. If the job didn't run, yet, then it returns 0.
         *
         * @return The last time the rescheduled or periodic job did run.
         */
        public long getLastRun() {
            return mRequest.getLastRun();
        }

        /**
         * Returns whether this is a transient jobs. <b>WARNING:</b> It's not guaranteed that a transient job
         * will run at all, e.g. rebooting the device or force closing the app will cancel the
         * job.
         *
         * @return If this is a transient job.
         */
        public boolean isTransient() {
            return mRequest.isTransient();
        }

        /**
         * Returns the transient extras you passed in when constructing this job with
         * {@link JobRequest.Builder#setTransientExtras(Bundle)}. <b>WARNING:</b> It's not guaranteed that a transient job
         * will run at all, e.g. rebooting the device or force closing the app will cancel the
         * job.
         *
         * <br>
         * <br>
         *
         * This will never be {@code null}. If you did not set any extras this will be an empty bundle.
         *
         * @return The transient extras you passed in when constructing this job.
         */
        @NonNull
        public Bundle getTransientExtras() {
            return mTransientExtras;
        }

        /**
         * @return Extra arguments for this {@link Job}. Never returns {@code null}.
         */
        @NonNull
        public PersistableBundleCompat getExtras() {
            if (mExtras == null) {
                mExtras = mRequest.getExtras();
                if (mExtras == null) {
                    mExtras = new PersistableBundleCompat();
                }
            }
            return mExtras;
        }

        /*package*/ JobRequest getRequest() {
            return mRequest;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            Params params = (Params) o;

            return mRequest.equals(params.mRequest);
        }

        @Override
        public int hashCode() {
            return mRequest.hashCode();
        }
    }
}
