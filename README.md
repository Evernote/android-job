# Android-Job

A utility library for Android to run jobs delayed in the background. Depending on the Android version either the `JobScheduler`, `GcmNetworkManager` or `AlarmManager` is getting used. You can find out in [this blog post](https://blog.evernote.com/tech/2015/10/26/unified-job-library-android/) or in [these slides](https://speakerdeck.com/vrallev/doo-z-z-z-z-z-e?slide=50) why you should prefer this library than each separate API. All features from Android Oreo are backward compatible back to Ice Cream Sandwich.

## Download

Download [the latest version](http://search.maven.org/#search|gav|1|g:"com.evernote"%20AND%20a:"android-job") or grab via Gradle:

```groovy
dependencies {
    implementation 'com.evernote:android-job:1.2.6'
}
```

If you didn't turn off the manifest merger from the Gradle build tools, then no further step is required to setup the library. Otherwise you manually need to add the permissions and services like in this [AndroidManifest](library/src/main/AndroidManifest.xml).

You can read the [JavaDoc here](https://evernote.github.io/android-job/javadoc/).

## Usage

The class `JobManager` serves as entry point. Your jobs need to extend the class `Job`. Create a `JobRequest` with the corresponding builder class and schedule this request with the `JobManager`.

Before you can use the `JobManager` you must initialize the singleton. You need to provide a `Context` and add a `JobCreator` implementation after that. The `JobCreator` maps a job tag to a specific job class. It's recommended to initialize the `JobManager` in the `onCreate()` method of your `Application` object, but there is [an alternative](https://github.com/evernote/android-job/wiki/FAQ#i-cannot-override-the-application-class-how-can-i-add-my-jobcreator), if you don't have access to the `Application` class.

```java
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        JobManager.create(this).addJobCreator(new DemoJobCreator());
    }
}
```

```java
public class DemoJobCreator implements JobCreator {

    @Override
    @Nullable
    public Job create(@NonNull String tag) {
        switch (tag) {
            case DemoSyncJob.TAG:
                return new DemoSyncJob();
            default:
                return null;
        }
    }
}
```

After that you can start scheduling jobs.

```java
public class DemoSyncJob extends Job {

    public static final String TAG = "job_demo_tag";

    @Override
    @NonNull
    protected Result onRunJob(Params params) {
        // run your job here
        return Result.SUCCESS;
    }

    public static void scheduleJob() {
        new JobRequest.Builder(DemoSyncJob.TAG)
                .setExecutionWindow(30_000L, 40_000L)
                .build()
                .schedule();
    }
}
```

## Advanced

The `JobRequest.Builder` class has many extra options, e.g. you can specify a required network connection, make the job periodic, pass some extras with a bundle, restore the job after a reboot or run the job at an exact time.

Each job has a unique ID. This ID helps to identify the job later to update requirements or to cancel the job.

```java
private void scheduleAdvancedJob() {
    PersistableBundleCompat extras = new PersistableBundleCompat();
    extras.putString("key", "Hello world");

    int jobId = new JobRequest.Builder(DemoSyncJob.TAG)
            .setExecutionWindow(30_000L, 40_000L)
            .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
            .setRequiresCharging(true)
            .setRequiresDeviceIdle(false)
            .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
            .setExtras(extras)
            .setRequirementsEnforced(true)
            .setUpdateCurrent(true)
            .build()
            .schedule();
}

private void schedulePeriodicJob() {
    int jobId = new JobRequest.Builder(DemoSyncJob.TAG)
            .setPeriodic(TimeUnit.MINUTES.toMillis(15), TimeUnit.MINUTES.toMillis(5))
            .build()
            .schedule();
}

private void scheduleExactJob() {
    int jobId = new JobRequest.Builder(DemoSyncJob.TAG)
            .setExact(20_000L)
            .build()
            .schedule();
}

private void runJobImmediately() {
    int jobId = new JobRequest.Builder(DemoSyncJob.TAG)
            .startNow()
            .build()
            .schedule();
}

private void cancelJob(int jobId) {
    JobManager.instance().cancel(jobId);
}
```

If a non periodic `Job` fails, then you can reschedule it with the defined back-off criteria.

```java
public class RescheduleDemoJob extends Job {

    @Override
    @NonNull
    protected Result onRunJob(Params params) {
        // something strange happened, try again later
        return Result.RESCHEDULE;
    }

    @Override
    protected void onReschedule(int newJobId) {
        // the rescheduled job has a new ID
    }
}
```

#### Proguard

The library doesn't use reflection, but it relies on three `Service`s and two `BroadcastReceiver`s. In order to avoid any issues, you shouldn't obfuscate those four classes. The library bundles its own Proguard config and you don't need to do anything, but just in case you can add [these rules](library/proguard.cfg) in your configuration.

## More questions?

See the [FAQ](https://github.com/evernote/android-job/wiki/FAQ) in the [Wiki](https://github.com/evernote/android-job/wiki).

## Google Play Services

This library does **not** automatically bundle the Google Play Services, because the dependency is really heavy and not all apps want to include them. That's why you need to add the dependency manually, if you want that the library uses the `GcmNetworkManager` on Android 4, then include the following dependency.
```groovy
dependencies {
    compile "com.google.android.gms:play-services-gcm:latest_version"
}
```
Because of recent changes in the support library, you must turn on the service manually in your `AndroidManifest.xml`
```xml
<service
    android:name="com.evernote.android.job.gcm.PlatformGcmService"
    android:enabled="true"
    tools:replace="android:enabled"/>
```
If you don't turn on the service, the library will always use the `AlarmManager` on Android 4.x.

Crashes after removing the GCM dependency is a known limitation of the Google Play Services. Please take a look at [this workaround](https://github.com/evernote/android-job/wiki/FAQ#how-can-i-remove-the-gcm-dependency-from-my-app) to avoid those crashes.

## WorkManager

[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) is a new architecture component from Google and tries to solve a very similar problem this library tries to solve: implementing background jobs only once for all Android versions. The API is very similar to this library, but provides more features like chaining work items and it runs its own executor.

If you start a new project, you should be using `WorkManager` instead of this library. You should also start migrating your code from this library to `WorkManager`. At some point in the future this library will deprecated.

## License
```
Copyright (c) 2007-2017 by Evernote Corporation, All rights reserved.

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```
