## 1.4.3 (2022-04-27)
* Make `PendingIntent`s immutable, what is required for Target SDK 31, see #600

## 1.4.2 (2019-10-09)
* Bump libraries to the latest version, this fixes a binary incompatibility with `WorkManager`, see #591

## 1.4.1 (2019-09-30)
* Remove appcompat as dependency, which was accidentally added in 1.4.0.

## 1.4.0 (2019-09-29)
* Migrate to AndroidX, see #583

## 1.3.0 (2019-09-29)
* (No changes compared to 1.3.0-rc1)
* Implement an API that uses the `WorkManager` for scheduling work items
* Initialize the job storage on a background thread, see #471
* Restrict usage of internal classes for internal usage only, see #465
* Use a `JobIntentService` implementation that doesn't crash, see #255
* Offset the end time when rescheduling an inexact job, see #547

## 1.3.0-rc1 (2019-03-22)
* Offset the end time when rescheduling an inexact job, see #547
* Upgraded WorkManager to 1.0.0, see #561

## 1.3.0-alpha09 (2018-11-18)
* Upgraded WorkManager to 1.0.0-alpha11

## 1.3.0-alpha08 (2018-10-20)
* Upgraded WorkManager with API incompatible changes, see #539

## 1.3.0-alpha07 (2018-09-23)
* Handle crash when rescheduling jobs, see #510
* Upgraded WorkManager

## 1.3.0-alpha06 (2018-07-24)
* Find the right tag for the job with `WorkManager`, see #509
* Integrate `WorkManager` version `1.0.0-alpha05`

## 1.3.0-alpha05 (2018-07-23)
* Use synchronous method to query the workmanager statuses, see #464

## 1.3.0-alpha04 (2018-07-20)
* Fix rare NPE when `WorkManager` is null for some reason, see #477
* Fix rare NPE when `JobStorage` is null, see #492
* Fix class loading error for the GCM API, see #487
* Use a `JobIntentService` implementation that doesn't crash, see #255

## 1.3.0-alpha03 (2018-06-11)
* Remove wrong restriction for `PersistableBundleCompat`

## 1.3.0-alpha02 (2018-06-10)
* Initialize the job storage on a background thread, see #471
* Restrict usage of internal classes for internal usage only, see #465
* Add a workaround for the internal crash in `JobIntentService`, see #255
* Fix RuntimeException with WorkManager, see #464

## 1.3.0-alpha01 (2018-05-28)
* Implement an API that uses the `WorkManager` for scheduling work items

## 1.2.6 (2018-05-27)
* Make the license in Java files clearer, see #422
* Use own internal monitor for synchronizing access to variables in order to avoid deadlocks when using the library, see #414
* Cleanup jobs in the database if no job creator returns an instance during execution, see #413
* Make sure that the `JobManager` is created when canceling jobs, see #442
* Don't crash on Android 4.X with the recent Google Play Services, see #415
* Explain the relation to `WorkManager`, see [here](https://github.com/evernote/android-job#WorkManager)

## 1.2.5 (2018-03-19)
* Handle platform bug where querying the network state throws an NPE internally, see #380
* Fix database access on main thread, see #385
* Fix misleading log message for some internal improvements, see #391
* Fix race condition when scheduling a job with `setUpdateCurrent(true)` where multiple jobs could have been scheduled, see #396
* Fix bug where a daily job runs twice a day, see #406
* Fix a bug where periodic jobs in the flex support mode weren't properly canceled while the job was running, see #407

## 1.2.4 (2018-02-08)
* Add `scheduleAsync()` to the `DailyJob` class for scheduling daily jobs asynchronously to avoid IO operations on the main thread, see #371

## 1.2.3 (2018-02-07)
* Add an `onCancel()` method to get notified when the job is canceled, see #359
* Expose the `meetsRequirements()` method to have the option to check all requirements manually, see #349
* Don't close the database automatically after each interaction, but give an option in the `JobConfig` class to revert this behavior, see #344
* Add `scheduleAsync()` for scheduling jobs asynchronously to avoid IO operations on the main thread

## 1.2.2 (2018-01-13)
* Use only the `support-compat` instead of the full `support-v4` library, see #326
* Use a `ReadWriteLock` for synchronizing the database reads and writes, see #344
* Update the last run time for daily jobs, see #332
* Shift the max execution delay if the requirements are enforced, see #325

## 1.2.1 (2017-11-23)
* Add option to override the default background executor, see #292
* Don't keep a strong reference to finished jobs to allow freeing memory, see #299
* Allow running a daily job immediately once, this is helpful for testing purposes, see #317
* Allow enforcing requirements for daily jobs, see #313
* Remove the Cat dependency for logging, see 295
* Add `@NonNull` annotation to `param` parameter, see #321 (thanks for the contribution @Jawnnypoo)

## 1.2.0 (2017-10-05)

* Extract `JobManager.Config` class into `JobConfig` class to make it possible to change settings before the `JobManager` is created
* Add an option to disable any specific API and not just the GCM API (only useful for testing purposes)
* Remove deprecated methods
* Add the `startNow()` method to run a job immediately respecting all constraints in Android O
* Remove the persisted parameter, which didn't work reliable, all jobs are persisted anyway
* Remove `startWakefulService` from the `Job` class, `WakefulBroadcastReceiver` is now deprecated with `JobIntentService` as the better option
* Add feature to make jobs transient and to add a `Bundle`, see `setTransientExtras(bundle)`
* Add new `METERED` network type
* Add new requirements battery not low and storage not low
* Add helper job class `DailyJob` to make it easier to run jobs once a day, see #223
* Add option in `JobConfig` to add a logger
* Add option in `JobConfig` for a job ID offset to avoid clashes with other jobs in the `JobScheduler`
* Switch to elapsed real time with the `AlarmManager` to have a consistent behavior with the `JobScheduler`, see #237

## 1.1.12 (2017-10-05)

* Handle NPE inside of `JobScheduler`
* Handle 'Package manager has died' crash
* Save the highest job ID in a pref file so that it isn't lost when no job is in the database anymore (makes debugging easier)
* Fix rare NPE when rescheduling jobs after service has been restarted, see #234
* Fix rescheduled periodic job although it has been canceled pre Android N, see #241

## 1.1.11 (2017-06-05)

* Fix a race condition when canceling jobs, see #178
* Disable the JobScheduler API if the service is disabled, see #190
* Fix `SQLiteConstraintException` when rescheduling jobs, because job is already present in the database, see #176
* Improve job result documentation, see #192
* Prevent app ending in a crash loop, see #194
* Fallback to an older API if the `JobScheduler` is null on the device
* Don't persist jobs with the `JobScheduler`, if this device is weird and doesn't have the boot permission
* List `support-v4` as dependency, because it's required, see #200
* Make `Job.Params` public for better test support, see #201
* Allow to suppress the `JobManagerCreateException`, see `JobApi.setForceAllowApi14(boolean)`
* Make SimpleDateFormat thread-local to avoid possible crash, see #208

## 1.1.10 (2017-04-29)

* Fix a race condition when canceling jobs, see #178
* Make it possible to reuse builder objects, this may fix SQL exceptions, see #176
* Add `JobRequest.getLastRun()` returning the time when the job did run the last time, if it was rescheduled or it's a periodic job, see #141
* Fix crash on Android 4.0.3 where `SQLiteDatabase` doesn't implement `Closable`, see #182
* Updating wording for network type ANY to make it clearer that no specific network state is required, see #185
* Use a copy of the platform DefaultDatabaseErrorHandler. Some manufacturers replace this causing stack overflows, see #184

## 1.1.9 (2017-04-10)

* Improve logging by providing an option to add a custom logger
* Fix crash when rescheduling jobs, see #164
* Fix wrong returned network type, see #166
* Expose failure count in the `JobRequest` class, see #168
* Don't silently eat `JobScheduler`'s limit exception
* Make `schedule()` method idempotent
* Add a fallback if removing a job from the database fails for some reason, see #145

## 1.1.8 (2017-03-23)

* Catch wake lock crashes in all cases, fixes #153
* Use a better execution in parallel with the `AlarmManager`. This may prevent a process death.
* Use better thread names
* List for quick boot completed broad casts, see #157

## 1.1.7 (2017-02-27)

* Use a service to reschedule jobs and prevent a too early process death, fixes #142

## 1.1.6 (2017-02-13)

* Reschedule jobs after an app update occurred or the Google Play Services were updated, see #135

## 1.1.5 (2017-01-25)

* Use only back-off criteria when rescheduling jobs, see #134

## 1.1.4 (2017-01-05)

* Expose schedule time of a job

## 1.1.3 (2016-11-09)

* Add an alternative to register a `JobCreator`, if you don't have access to the `Application` class

## 1.1.2 (2016-10-19)

* Remove packaged `R.txt` file

## 1.0.15 (2016-10-19)

* Remove packaged `R.txt` file

## 1.1.1 (2016-10-19)

* Add test option to override minimum interval and flex for testing purposes
* Fix issue that periodic jobs were accidentally canceled

## 1.0.14 (2016-10-19)

* Fix issue that periodic jobs were accidentally canceled

## 1.1.0 (2016-09-23)

* Bump SDK version to 24
* Add option to specify flex parameter for periodic jobs
 * Add support for flex parameter with GCM proxy
 * Add API 24 proxy with support for flex parameter
 * Add a flex support mode for all other APIs
* Add API 19 proxy supporting an execution window
* Add NOT_ROAMING network type
* Adjust minimum interval for periodic jobs
* Add GCM service declaration in library manifest

## 1.0.13 (2016-09-12)

* Fix crash while acquiring wake lock
* Check boot permission only when persisted flag is set to true

## 1.0.12 (2016-08-29)

* Fix IllegalArgumentException with GCM API, see #72

## 1.0.11 (2016-08-09)

* Fix overflow for too large execution windows
* Fix immediately starting jobs with JobScheduler if the execution window is too large

## 1.0.10 (2016-07-25)

* Create the JobManager in all API services

## 1.0.9 (2016-07-18)

* Bug fixes

## 1.0.8 (2016-07-05)

* Make PlatformAlarmReceiver intent explicit, fixes #56
* Delete a job after it has finished, otherwise reschedule if app is crashing while job is running, fixes #55
* Extend Params class with more parameters from the job request, fixes #52
* Cache only 20 finished jobs to free up memory, fixes #57

## 1.0.7 (2016-06-03)

* Weird bug fixes

## 1.0.6 (2016-05-20)

* Clean up orphaned jobs after the database was deleted

## 1.0.5 (2016-05-03)

* Fix "WakeLock under-locked" crash

## 1.0.4 (2016-03-13)

* Add option to update any preexisting jobs 

## 1.0.3 (2016-02-29)

* Bug fixes

## 1.0.2 (2016-01-05)

* Add option to attach multiple job creators 

## 1.0.1 (2015-12-18)

* Catch certain exceptions and runtime crashes

## 1.0.0 (2015-08-20)

* Initial release
