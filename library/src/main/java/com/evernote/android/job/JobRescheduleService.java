package com.evernote.android.job;

import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.JobIntentService;

import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;
import net.vrallev.android.cat.instance.CatEmpty;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * This service reschedules your jobs in case this should be necessary. Usually rescheduling is necessary
 * after a reboot. If you don't want that your jobs are rescheduled, then you should use a transient job
 * or cancel your job manually.
 *
 * @author rwondratschek
 */
public final class JobRescheduleService extends JobIntentService {

    private static final CatLog CAT = BuildConfig.DEBUG ? new JobCat("JobRescheduleService") : new CatEmpty();

    /*package*/ static void startService(Context context) {
        try {
            enqueueWork(context, JobRescheduleService.class, JobIdsInternal.JOB_ID_JOB_RESCHEDULE_SERVICE, new Intent());
            latch = new CountDownLatch(1);
        } catch (Exception e) {
            /*
             * Caused by: java.lang.SecurityException: Unable to start service Intent
             * { cmp=com.evernote/.android.job.JobRescheduleService (has extras) }: Unable to launch
             * app com.evernote/1210016 for service Intent { cmp=com.evernote/.android.job.JobRescheduleService }:
             * user 12 is stopped
             *
             * It's bad to catch all exceptions. But this service is only a safety check and
             * if it fails, then better try next time and don't handle the exception upstream
             * where it's hard to deal with this case.
             */
            CAT.e(e);
        }
    }

    @VisibleForTesting
    /*package*/ static CountDownLatch latch;

    @Override
    protected void onHandleWork(@NonNull Intent intent) {
        /*
         * Delay this slightly. This avoids a race condition if the app was launched by the
         * AlarmManager. Then the alarm was already removed, but the JobRequest might still
         * be available in the storage. We still catch this case, because we never execute
         * a job with the same ID twice. Nonetheless, add the delay to save resources.
         */
        try {
            CAT.d("Reschedule service started");
            SystemClock.sleep(JobConfig.getJobReschedulePause());

            JobManager manager;
            try {
                manager = JobManager.create(this);
            } catch (JobManagerCreateException e) {
                return;
            }

            Set<JobRequest> requests = manager.getAllJobRequests(null, true, true);

            int rescheduledCount = rescheduleJobs(manager, requests);

            CAT.d("Reschedule %d jobs of %d jobs", rescheduledCount, requests.size());
        } finally {
            if (latch != null) {
                // latch can be null, if the service was restarted after a process death
                latch.countDown();
            }
        }
    }

    @SuppressWarnings("UnusedReturnValue")
    /*package*/ int rescheduleJobs(JobManager manager) {
        return rescheduleJobs(manager, manager.getAllJobRequests(null, true, true));
    }

    /*package*/ int rescheduleJobs(JobManager manager, Collection<JobRequest> requests) {
        int rescheduledCount = 0;
        boolean exceptionThrown = false;
        for (JobRequest request : requests) {
            boolean reschedule;
            if (request.isStarted()) {
                Job job = manager.getJob(request.getJobId());
                reschedule = job == null;
            } else {
                reschedule = !manager.getJobProxy(request.getJobApi()).isPlatformJobScheduled(request);
            }

            if (reschedule) {
                // update execution window
                try {
                    request.cancelAndEdit()
                            .build()
                            .schedule();
                } catch (Exception e) {
                    // this may crash (e.g. more than 100 jobs with JobScheduler), but it's not catchable for the user
                    // better catch here, otherwise app will end in a crash loop
                    if (!exceptionThrown) {
                        CAT.e(e);
                        exceptionThrown = true;
                    }
                }

                rescheduledCount++;
            }
        }
        return rescheduledCount;
    }
}
