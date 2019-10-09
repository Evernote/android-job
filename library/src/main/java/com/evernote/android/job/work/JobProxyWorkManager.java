package com.evernote.android.job.work;

import android.content.Context;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.annotation.RestrictTo;

import androidx.work.WorkInfo;
import com.evernote.android.job.JobProxy;
import com.evernote.android.job.JobProxyIllegalStateException;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobCat;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import androidx.work.Configuration;
import androidx.work.Constraints;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkManager;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobProxyWorkManager implements JobProxy {

    private static final String PREFIX = "android-job-";

    private static final JobCat CAT = new JobCat("JobProxyWork");

    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Context mContext;

    public JobProxyWorkManager(Context context) {
        mContext = context;
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
        List<WorkInfo> infos = getWorkStatusBlocking(createTag(request.getJobId()));
        if (infos == null || infos.isEmpty()) {
            return false;
        }

        WorkInfo.State state = infos.get(0).getState();
        return state == WorkInfo.State.ENQUEUED;
    }

    /*package*/ static String createTag(int jobId) {
        return PREFIX + jobId;
    }

    /*package*/ static int getJobIdFromTags(Collection<String> tags) {
        for (String tag : tags) {
            if (tag.startsWith(PREFIX)) {
                return Integer.parseInt(tag.substring(PREFIX.length()));
            }
        }
        return -1;
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
        WorkManager workManager;
        try {
            workManager = WorkManager.getInstance(mContext);
        } catch (Throwable t) {
            workManager = null;
        }
        if (workManager == null) {
            try {
                WorkManager.initialize(mContext, new Configuration.Builder().build());
                workManager = WorkManager.getInstance(mContext);
            } catch (Throwable ignored) {
            }
            CAT.w("WorkManager getInstance() returned null, now: %s", workManager);
        }

        return workManager;
    }

    private List<WorkInfo> getWorkStatusBlocking(String tag) {
        WorkManager workManager = getWorkManager();
        if (workManager == null) {
            return Collections.emptyList();
        }

        try {
            return workManager.getWorkInfosByTag(tag).get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
