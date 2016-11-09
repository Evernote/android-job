### Can I run a job immediately?

No, it's recommended to extract the logic from your job instead and to reuse it in a background thread.

```java
public class DemoSyncJob extends Job {

    public static final String TAG = "job_demo_tag";

    @Override
    @NonNull
    protected Result onRunJob(final Params params) {
        boolean success = new DemoSyncEngine().sync();
        return success ? Result.SUCCESS : Result.FAILURE;
    }
}

public class DemoSyncEngine {

    @WorkerThread
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
                return new DemoSyncEngine().sync();
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

This library is a subset of 3 different APIs. Since Android Nougat the minimum interval of periodic jobs is 15 minutes. Although pre Nougat devices support smaller intervals, the least common was chosen as minimum for this library so that periodic jobs run with the same frequency on all devices.

The `JobScheduler` with Android Nougat allows setting a smaller interval, but the value is silently adjusted and a warning is being logged. This library throws an exception instead, so that misbehaving jobs are caught early. You can read more about it [here](https://developer.android.com/reference/android/app/job/JobInfo.html#getMinPeriodMillis()).

### How can I run async operations in a job?

This library automatically creates a wake lock for you so that the system stays on until your job finished. When your job returns a result, then this wakelock is being released and async operations may not finish. The easiest solution is to not return a result until the async operation finished. Don't forget that your job is already executed on a background thread!

```java
public class AsyncJob extends Job {

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        final CountDownLatch countDownLatch = new CountDownLatch(1);

        new Thread() {
            @Override
            public void run() {
                // do async operation here

                SystemClock.sleep(3_000L);
                countDownLatch.countDown();
            }
        }.start();

        try {
            countDownLatch.await();
        } catch (InterruptedException ignored) {
        }

        return Result.SUCCESS;
    }
}
```

### How can I remove the GCM dependency from my app?

You need to be careful, if you remove this dependency after it has been already used for a while.

```groovy
dependencies {
    compile 'com.google.android.gms:play-services-gcm:9.8.0'
}
```

The reason is that jobs probably were scheduled with the GCM API on Android 4.X and after removing the dependency, the Play Services still look for the platform service, but can't find the class anymore. The result is that your app will crash with a runtime exception similar like this:

```
java.lang.RuntimeException: Unable to instantiate service com.evernote.android.job.gcm.PlatformGcmService: java.lang.ClassNotFoundException: Didn't find class "com.evernote.android.job.gcm.PlatformGcmService" on path: DexPathList[[zip file "/data/app/com.evernote.android.job.demo-2/base.apk"],nativeLibraryDirectories=[/vendor/lib, /system/lib]]
```

Fortunately, there is a workaround to prevent the crash. You need to remove the GCM service declaration from the manifest like this and then the Play Services won't try to instantiate the missing class.

```xml
<application
    ...>

    <service
        android:name="com.evernote.android.job.gcm.PlatformGcmService"
        tools:node="remove"/>

</application>
```

### Why does my job run while the device is offline, although I've requested a network connection?

That's expected. The job should run once during a period or within the specified execution window. The timing is a higher requirement than the network type, which is more like a hint when it's best to run your job. To make sure that all requirements are met, you can call `.setRequirementsEnforced(true)`. This will make sure that your job won't run, if one check fails, e.g.

```java
new JobRequest.Builder(DemoSyncJob.TAG)
        .setExecutionWindow(60_000L, 90_000L)
        .setRequiresCharging(true)
        .setRequiredNetworkType(JobRequest.NetworkType.UNMETERED)
        .setRequirementsEnforced(true)
        .build()
        .schedule();
```

### I cannot override the Application class. How can I add my `JobCreator`?

There is an alternative. You can register a `BroadcastReceiver` to get notified about that you should add your `JobCreator`, e.g.

```xml
<receiver
    android:name=".AddReceiver"
    android:exported="false">
        <intent-filter>
            <action android:name="com.evernote.android.job.ADD_JOB_CREATOR"/>
        </intent-filter>
</receiver>
```
```java
public final class AddReceiver extends AddJobCreatorReceiver {
    @Override
    protected void addJobCreator(@NonNull Context context, @NonNull JobManager manager) {
        manager.addJobCreator(new DemoJobCreator());
    }
}
```