package com.evernote.android.job;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;

import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;

import java.util.Set;

/**
 * @author rwondratschek
 */
public final class JobRescheduleService extends IntentService {

    private static final String TAG = "JobRescheduleService";
    private static final CatLog CAT = new JobCat(TAG);

    /*package*/ static void startService(Context context) {
        Intent intent = new Intent(context, JobRescheduleService.class);
        WakeLockUtil.startWakefulService(context, intent);
    }

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

            JobManager manager = JobManager.create(this);
            Set<JobRequest> requests = manager.getJobStorage().getAllJobRequests(null, true);

            int rescheduledCount = 0;
            for (JobRequest request : requests) {
                boolean reschedule;
                if (request.isTransient()) {
                    Job job = manager.getJob(request.getJobId());
                    reschedule = job == null;
                } else {
                    reschedule = !manager.getJobProxy(request).isPlatformJobScheduled(request);
                }

                if (reschedule) {
                    // update execution window
                    request.cancelAndEdit()
                            .build()
                            .schedule();

                    rescheduledCount++;
                }
            }

            CAT.d("Reschedule %d jobs of %d jobs", rescheduledCount, requests.size());

        } finally {
            WakeLockUtil.completeWakefulIntent(intent);
        }

    }
}
