package com.evernote.android.job.demo;

import android.app.Application;

import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;

/**
 * @author rwondratschek
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        JobManager.create(this, new JobCreator.ClassNameJobCreator());
    }
}
