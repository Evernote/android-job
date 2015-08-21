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

import android.app.Service;
import android.content.Context;
import android.os.PowerManager.WakeLock;
import android.support.annotation.NonNull;

import com.evernote.android.job.util.Device;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import net.vrallev.android.cat.Cat;

/**
 * Base class for running delayed jobs. Each concrete class must provide a public default constructor.
 * A {@link Job} is executed in a background thread.
 *
 * @author rwondratschek
 */
public final class Job {

    public enum Result {
        /**
         * Indicates that {@link Action#onRunJob(Params)} was successful.
         */
        SUCCESS,
        /**
         * Indicates that {@link Action#onRunJob(Params)} failed, but the {@link Job} shouldn't be rescheduled.
         */
        FAILURE,
        /**
         * Indicates that {@link Action#onRunJob(Params)} failed and the {@link Job} should be rescheduled
         * with the defined back-off criteria. Not that returning {@code RESCHEDULE} for a periodic
         * {@link Job} is invalid and ignored.
         */
        RESCHEDULE
    }

    private final Action action;

    private final Params mParams;
    private final Context mContext;

    private boolean mCanceled;
    private long mFinishedTimeStamp = -1;

    private Result mResult = Result.FAILURE;

    public Job(Action action, Context context, JobRequest jobRequest) {
        this.action = action;
        this.mContext = context.getApplicationContext();
        this.mParams = new Params(jobRequest);
    }

    public interface Action {
        /**
         * This method is invoked from a background thread. You should run your desired task here. This
         * method is thread safe. Each time a job starts executing a new instance of your {@link Job} is
         * instantiated. You can identify your {@link Job} with the passed {@code params}.
         * <p/>
         * You should call {@link Params#isCanceled()} frequently for long running jobs and stop your
         * task if necessary.
         * <p/>
         * A {@link WakeLock} is acquired for 3 minutes for each {@link Job}. If your task needs more
         * time, then you need to create an extra {@link WakeLock}.
         *
         * @param params The parameters for this concrete job.
         * @return The result of this {@link Job}. Note that returning {@link Result#RESCHEDULE} for a
         * periodic {@link Job} is invalid and ignored.
         */
        @NonNull
        Result onRunJob(Params params);

        /**
         * This method is called if you returned {@link Result#RESCHEDULE} in {@link #onRunJob(Params)}
         * and the {@link Job} was successfully rescheduled. The new rescheduled {@link JobRequest} has
         * a new ID. Override this method if you want to be notified about the change.
         *
         * @param newJobId The new ID of the rescheduled {@link JobRequest}.
         */
        void onReschedule(int newJobId);
    }

    /*package*/ final Result runJob() {
        try {
            if (meetsRequirements()) {
                mResult = action.onRunJob(mParams);
            } else {
                mResult = mParams.isPeriodic() ? Result.FAILURE : Result.RESCHEDULE;
            }

            return mResult;

        } finally {
            mFinishedTimeStamp = System.currentTimeMillis();
        }
    }

    public void reschedule() {
        JobRequest request = mParams.getRequest();
        if (!request.isPeriodic()) {
            action.onReschedule(request.reschedule(true));
        }
    }

    private boolean meetsRequirements() {
        if (!mParams.getRequest().requirementsEnforced()) {
            return true;
        }

        if (!isRequirementChargingMet()) {
            Cat.w("Job requires charging, reschedule");
            return false;
        }
        if (!isRequirementDeviceIdleMet()) {
            Cat.w("Job requires device to be idle, reschedule");
            return false;
        }
        if (!isRequirementNetworkTypeMet()) {
            Cat.w("Job requires network to be %s, but was %s", mParams.getRequest().requiredNetworkType(),
                    Device.getNetworkType(getContext()));
            return false;
        }

        return true;
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be charging and it isn't charging.
     * Otherwise always returns {@code true}.
     */
    private boolean isRequirementChargingMet() {
        return !(mParams.getRequest().requiresCharging() && !Device.isCharging(getContext()));
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be idle and it isn't idle. Otherwise
     * always returns {@code true}.
     */
    private boolean isRequirementDeviceIdleMet() {
        return !(mParams.getRequest().requiresDeviceIdle() && !Device.isIdle(getContext()));
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be in a specific network state and it
     * isn't in this state. Otherwise always returns {@code true}.
     */
    private boolean isRequirementNetworkTypeMet() {
        JobRequest.NetworkType requirement = mParams.getRequest().requiredNetworkType();
        switch (requirement) {
            case ANY:
                return true;
            case UNMETERED:
                JobRequest.NetworkType current = Device.getNetworkType(getContext());
                return JobRequest.NetworkType.UNMETERED.equals(current);
            case CONNECTED:
                current = Device.getNetworkType(getContext());
                return !JobRequest.NetworkType.ANY.equals(current);
            default:
                throw new IllegalStateException("not implemented");
        }
    }

    /**
     * @return The {@link Context} running this {@link Job}. In most situations it's a {@link Service}.
     */
    @NonNull
    protected final Context getContext() {
        return mContext;
    }

    /**
     * Cancel this {@link Job} if it hasn't finished, yet.
     */
    public final void cancel() {
        if (!isFinished()) {
            mCanceled = true;
        }
    }

    /**
     * @return {@code true} if the {@link Job} finished.
     */
    public final boolean isFinished() {
        return mFinishedTimeStamp > 0;
    }

    /*package*/ final long getFinishedTimeStamp() {
        return mFinishedTimeStamp;
    }

    /*package*/ final Result getResult() {
        return mResult;
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
        return "Job{"
                + "mId=" + mParams.getId()
                + ", mFinished=" + isFinished()
                + ", mResult=" + mResult
                + ", mCanceled=" + mCanceled
                + ", mPeriodic=" + mParams.isPeriodic()
                + '}';
    }

    /**
     * Holds several parameters for the executing {@link Job}.
     */
    public final class Params {

        private final JobRequest mRequest;
        private PersistableBundleCompat mExtras;

        private Params(@NonNull JobRequest request) {
            mRequest = request;
            mExtras = request.getExtras();
        }

        /**
         * @return The unique ID for this {@link Job}.
         */
        public int getId() {
            return mRequest.getJobId();
        }

        /**
         * @return Whether this {@link Job} is periodic or not. If this {@link Job} is periodic, then
         * you shouldn't return {@link Result#RESCHEDULE} as result.
         */
        public boolean isPeriodic() {
            return mRequest.isPeriodic();
        }

        /**
         * @return Extra arguments for this {@link Job}. Never returns {@code null}.
         */
        @NonNull
        public PersistableBundleCompat getExtras() {
            if (mExtras == null) {
                mExtras = new PersistableBundleCompat();
            }
            return mExtras;
        }

        /*package*/ JobRequest getRequest() {
            return mRequest;
        }

        public Context getContext() {
            return Job.this.getContext();
        }

        /** @return {@code true} if this {@link Job} was canceled. */
        public boolean isCanceled() {
            return Job.this.mCanceled;
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
