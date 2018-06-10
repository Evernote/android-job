package com.evernote.android.job.demo;

import android.app.Application;
import android.os.StrictMode;

import com.evernote.android.job.JobManager;
import com.facebook.stetho.Stetho;

/**
 * @author rwondratschek
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        Stetho.initializeWithDefaults(this);

        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                    new StrictMode.ThreadPolicy.Builder()
                            .detectAll()
                            .penaltyLog()
                            .penaltyDeath()
                            .build());

            StrictMode.setVmPolicy(
                    new StrictMode.VmPolicy.Builder()
                            .detectAll()
                            .penaltyLog()
                            .penaltyDeath()
                            .build());
        }

        JobManager.create(this).addJobCreator(new DemoJobCreator());
    }
}

