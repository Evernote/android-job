package com.evernote.android.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.support.annotation.VisibleForTesting;

import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;

import java.util.Set;
import java.util.concurrent.CountDownLatch;

/**
 * @author rwondratschek
 */
public final class JobRescheduleService extends IntentService {

    private static final String TAG = "JobRescheduleService";
    private static final CatLog CAT = new JobCat(TAG);

    /*package*/ static void startService(Context context) {
        try {
            Intent intent = new Intent(context, JobRescheduleService.class);
            WakeLockUtil.startWakefulService(context, intent);
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

    public JobRescheduleService() {
        super(TAG);
    }

    @Override
    protected void onHandleIntent(Intent intent) {
        try {
            /*
             * Delay this slightly. This avoids a race condition if the app was launched by the
             * AlarmManager. Then the alarm was already removed, but the JobRequest might still
             * be available in the storage. We still catch this case, because we never execute
             * a job with the same ID twice. However, the still save resources with the delay.
             */
            CAT.d("Reschedule service started");
            SystemClock.sleep(10_000L);

            JobManager manager;
            try {
                manager = JobManager.create(this);
            } catch (JobManagerCreateException e) {
                return;
            }

            Set<JobRequest> requests = manager.getJobStorage().getAllJobRequests(null, true);

            int rescheduledCount = 0;
            boolean exceptionThrown = false;

            for (JobRequest request : requests) {
                boolean reschedule;
                if (request.isTransient()) {
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

            CAT.d("Reschedule %d jobs of %d jobs", rescheduledCount, requests.size());
            latch.countDown();

        } catch (Exception ignore) {
            // IllegalArgumentException No such service ComponentInfo
            // JobService component disabled by third party app on rooted devices
        } finally {
            WakeLockUtil.completeWakefulIntent(intent);
        }

    }
}
