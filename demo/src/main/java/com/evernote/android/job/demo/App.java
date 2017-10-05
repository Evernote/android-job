package com.evernote.android.job.demo;

import android.app.Application;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.JobConfig;
import com.evernote.android.job.JobManager;
import com.evernote.android.job.util.JobLogger;
import com.facebook.stetho.Stetho;

/**
 * @author rwondratschek
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Stetho.initializeWithDefaults(this);

        JobConfig.addLogger(new JobLogger() {
            @Override
            public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
                // log
            }
        });
        JobConfig.setLogcatEnabled(false);
        JobManager.create(this).addJobCreator(new DemoJobCreator());
    }

    private static class MyLogger implements JobLogger {
        @Override
        public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
            // log
        }
    }
}

