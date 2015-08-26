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

import java.lang.ref.WeakReference;

/**
 * Base class for running delayed jobs. Each concrete class must provide a public default constructor.
 * A {@link Job} is executed in a background thread.
 *
 * @author rwondratschek
 */
public abstract class Job {

    public enum Result {
        /**
         * Indicates that {@link #onRunJob(Params)} was successful.
         */
        SUCCESS,
        /**
         * Indicates that {@link #onRunJob(Params)} failed, but the {@link Job} shouldn't be rescheduled.
         */
        FAILURE,
        /**
         * Indicates that {@link #onRunJob(Params)} failed and the {@link Job} should be rescheduled
         * with the defined back-off criteria. Not that returning {@code RESCHEDULE} for a periodic
         * {@link Job} is invalid and ignored.
         */
        RESCHEDULE
    }

    private Params mParams;
    private WeakReference<Context> mContextReference;
    private Context mApplicationContext;

    private boolean mCanceled;
    private long mFinishedTimeStamp = -1;

    private Result mResult = Result.FAILURE;

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
    protected abstract Result onRunJob(Params params);

    /*package*/ final Result runJob() {
        try {
            if (meetsRequirements()) {
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
    protected void onReschedule(int newJobId) {
        // override me
    }

    private boolean meetsRequirements() {
        if (!getParams().getRequest().requirementsEnforced()) {
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
            Cat.w("Job requires network to be %s, but was %s", getParams().getRequest().requiredNetworkType(),
                    Device.getNetworkType(getContext()));
            return false;
        }

        return true;
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be charging and it isn't charging.
     * Otherwise always returns {@code true}.
     */
    protected boolean isRequirementChargingMet() {
        return !(getParams().getRequest().requiresCharging() && !Device.isCharging(getContext()));
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be idle and it isn't idle. Otherwise
     * always returns {@code true}.
     */
    protected boolean isRequirementDeviceIdleMet() {
        return !(getParams().getRequest().requiresDeviceIdle() && !Device.isIdle(getContext()));
    }

    /**
     * @return {@code false} if the {@link Job} requires the device to be in a specific network state and it
     * isn't in this state. Otherwise always returns {@code true}.
     */
    protected boolean isRequirementNetworkTypeMet() {
        JobRequest.NetworkType requirement = getParams().getRequest().requiredNetworkType();
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

    /*package*/ final Job setRequest(JobRequest request) {
        mParams = new Params(request);
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
        if (!isFinished()) {
            mCanceled = true;
        }
    }

    /**
     * @return {@code true} if this {@link Job} was canceled.
     */
    protected final boolean isCanceled() {
        return mCanceled;
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
        return "job{"
                + "id=" + mParams.getId()
                + ", finished=" + isFinished()
                + ", result=" + mResult
                + ", canceled=" + mCanceled
                + ", periodic=" + mParams.isPeriodic()
                + ", class=" + getClass().getSimpleName()
                + ", tag=" + getClass().getSimpleName()
                + '}';
    }

    /**
     * Holds several parameters for the executing {@link Job}.
     */
    protected static final class Params {

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
         * @return The specific tag for this {@link Job} or {@code null} if not set.
         */
        public String getTag() {
            return mRequest.getTag();
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
