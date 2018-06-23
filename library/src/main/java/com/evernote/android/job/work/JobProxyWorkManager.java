package com.evernote.android.job.work;

import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;

import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobProxyIllegalStateException;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.State;
import androidx.work.WorkManager;
import androidx.work.WorkStatus;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobProxyWorkManager implements JobProxy {

    private static final String PREFIX = "android-job-";

    private static final JobCat CAT = new JobCat("JobProxyWork");

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Context mContext;
    private final Handler mHandler;

    public JobProxyWorkManager(Context context) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public void plantOneOff(JobRequest request) {
        if (request.isTransient()) {
            TransientBundleHolder.putBundle(request.getJobId(), request.getTransientExtras());
        }

        OneTimeWorkRequest workRequest = new OneTimeWorkRequest.Builder(PlatformWorker.class)
                .setInitialDelay(request.getStartMs(), TimeUnit.MILLISECONDS) // don't use the average here, WorkManager will do the right thing
                .setConstraints(buildConstraints(request))
                .addTag(createTag(request.getJobId()))
                .build();

        // don't set the back-off criteria, android-job is handling this

        WorkManager workManager = getWorkManager();
        if (workManager == null) {
            throw new JobProxyIllegalStateException("WorkManager is null");
        }

        workManager.enqueue(workRequest);
    }

    @Override
    public void plantPeriodic(JobRequest request) {
        PeriodicWorkRequest workRequest = new PeriodicWorkRequest.Builder(PlatformWorker.class, request.getIntervalMs(), TimeUnit.MILLISECONDS,
                request.getFlexMs(), TimeUnit.MILLISECONDS)
                .setConstraints(buildConstraints(request))
                .addTag(createTag(request.getJobId()))
                .build();

        WorkManager workManager = getWorkManager();
        if (workManager == null) {
            throw new JobProxyIllegalStateException("WorkManager is null");
        }

        workManager.enqueue(workRequest);
    }

    @Override
    public void plantPeriodicFlexSupport(JobRequest request) {
        CAT.w("plantPeriodicFlexSupport called although flex is supported");
        plantPeriodic(request);
    }

    @Override
    public void cancel(int jobId) {
        WorkManager workManager = getWorkManager();
        if (workManager == null) {
            return;
        }

        workManager.cancelAllWorkByTag(createTag(jobId));
        TransientBundleHolder.cleanUpBundle(jobId);
    }

    @Override
    public boolean isPlatformJobScheduled(JobRequest request) {
        List<WorkStatus> statuses = getWorkStatusBlocking(createTag(request.getJobId()));
        if (statuses == null || statuses.isEmpty()) {
            return false;
        }

        State state = statuses.get(0).getState();
        return state == State.ENQUEUED;
    }

    /*package*/ static String createTag(int jobId) {
        return PREFIX + jobId;
    }

    /*package*/ static int getJobIdFromTag(String tag) {
        return Integer.parseInt(tag.substring(PREFIX.length()));
    }

    private static Constraints buildConstraints(JobRequest request) {
        Constraints.Builder constraintsBuilder = new Constraints.Builder()
                .setRequiresBatteryNotLow(request.requiresBatteryNotLow())
                .setRequiresCharging(request.requiresCharging())
                .setRequiresStorageNotLow(request.requiresStorageNotLow())
                .setRequiredNetworkType(mapNetworkType(request.requiredNetworkType()));

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            constraintsBuilder.setRequiresDeviceIdle(request.requiresDeviceIdle());
        }

        return constraintsBuilder.build();
    }

    @NonNull
    private static NetworkType mapNetworkType(@NonNull JobRequest.NetworkType networkType) {
        switch (networkType) {
            case ANY:
                return NetworkType.NOT_REQUIRED;
            case METERED:
                return NetworkType.METERED;
            case CONNECTED:
                return NetworkType.CONNECTED;
            case UNMETERED:
                return NetworkType.UNMETERED;
            case NOT_ROAMING:
                return NetworkType.NOT_ROAMING;
            default:
                throw new IllegalStateException("Not implemented");
        }
    }

    private WorkManager getWorkManager() {
        // don't cache the instance, it could change under the hood, e.g. during tests
        WorkManager workManager = WorkManager.getInstance();
        if (workManager == null) {
            WorkManager.initialize(mContext, new Configuration.Builder().build());
            workManager = WorkManager.getInstance();
            CAT.w("WorkManager getInstance() returned null, now: %s", workManager);
        }

        return workManager;
    }

    private List<WorkStatus> getWorkStatusBlocking(String tag) {
        final CountDownLatch latch = new CountDownLatch(1);
        final AtomicReference<List<WorkStatus>> reference = new AtomicReference<>();

        WorkManager workManager = getWorkManager();
        if (workManager == null) {
            return Collections.emptyList();
        }

        final LiveData<List<WorkStatus>> liveData = workManager.getStatusesByTag(tag);
        liveData.observeForever(new Observer<List<WorkStatus>>() {
            @Override
            public void onChanged(@Nullable List<WorkStatus> workStatuses) {
                if (reference.get() == null) {
                    reference.set(workStatuses);
                }

                final Observer<List<WorkStatus>> observer = this;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        liveData.removeObserver(observer);
                    }
                });

                latch.countDown();
            }
        });

        try {
            latch.await(3, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
        }
        return reference.get();
    }
}
