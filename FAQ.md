### Can I run a job immediately?

No, it's recommended to extract the logic from your job instead and to reuse it in a background thread.

```java
public class JobSample extends Job {

    @NonNull
    @Override
    protected Result onRunJob(Params params) {
        runNow();
        return Result.SUCCESS;
    }

    public static void runNow() {
        new MyAwesomeTask().run();
    }

    public static class MyAwesomeTask {
        public void run() {
            // do stuff here
        }
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