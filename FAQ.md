### Can I run a job immediately?

No, it's recommended to extract the logic from your job instead and to reuse it in a background thread.

```java
public class DemoSyncJob extends Job {

    public static final String TAG = "job_demo_tag";

    @Override
    @NonNull
    protected Result onRunJob(final Params params) {
        boolean success = new DemoSyncEngine(getContext()).sync();
        return success ? Result.SUCCESS : Result.FAILURE;
    }
}

public class DemoSyncEngine {
    public boolean sync() {
        // do something fancy
        return true;
    }
}

public class SyncHistoryActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_history);

        syncAsynchronously();
    }

    private void syncAsynchronously() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return new DemoSyncEngine(SyncHistoryActivity.this).sync();
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                refreshView();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }
}
```

### How can I run a job at a specific time once a day?

See this sample, which schedules a job between 1 AM and 6 AM each day. Note that this sample isn't using a periodic job, because the periodic jobs don't support a flex parameter (yet).

```java
public class JobSample extends Job {

    public static final String TAG = "JobSample";

    public static void schedule() {
        schedule(true);
    }

    private static void schedule(boolean updateCurrent) {
        Calendar calendar = Calendar.getInstance();
        int hour = calendar.get(Calendar.HOUR_OF_DAY);
        int minute = calendar.get(Calendar.MINUTE);

        // 1 AM - 6 AM, ignore seconds
        long startMs = TimeUnit.MINUTES.toMillis(60 - minute)
                + TimeUnit.HOURS.toMillis((24 - hour) % 24);
        long endMs = startMs + TimeUnit.HOURS.toMillis(5);

        new JobRequest.Builder(TAG)
                .setExecutionWindow(startMs, endMs)
                .setPersisted(true)
                .setUpdateCurrent(updateCurrent)
                .build()
                .schedule();
    }

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        try {
            // do something
            return Result.SUCCESS;
        } finally {
            schedule(false); // don't update current, it would cancel this currently running job
        }
    }
}
```

### Do NOT use `Long.MAX_VALUE` as argument!

Don't use `Long.MAX_VALUE` as argument for the execution window. The `AlarmManager` doesn't allow setting a start date, instead the execution time is the arithmetic average between start and end date.

Your job might work as expected on Android 5+, but maybe won't run at all on older devices.

```java
// bad, execution time on Android 4.X = startMs + (endMs - startMs) / 2
new JobRequest.Builder(TAG)
        .setExecutionWindow(3_000L, Long.MAX_VALUE)
        .build()
        .schedule();

// better, execution time on Android 4.X is 2 days
new JobRequest.Builder(TAG)
        .setExecutionWindow(TimeUnit.DAYS.toMillis(1), TimeUnit.DAYS.toMillis(3))
        .build()
        .schedule();
```

### Why can't an interval be smaller than 15 minutes for periodic jobs?

This library is a subset of 3 different APIs. Since Android Nougat the minimum interval of periodic jobs is 15 minutes. Although pre Nougat devices support a smaller intervals, the least common was chosen as minimum for this library so that all devices behave the same for all APIs.

The `JobScheduler` with Android Nougat allows setting a smaller interval, but the value is silently adjusted and a warning is being logged. This library throws an exception instead, so that misbehaving jobs are caught early. You can read more about it [here](https://developer.android.com/reference/android/app/job/JobInfo.html#getMinPeriodMillis()).