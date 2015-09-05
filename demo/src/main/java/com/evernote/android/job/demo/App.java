package com.evernote.android.job.demo;

import android.app.Application;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobManager;

/**
 * @author rwondratschek
 */
public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        JobManager.create(this, new MyJobCreator());
    }

    private static class MyJobCreator implements JobCreator {

        @Override
        public Job create(String tag) {
            switch (tag) {
                case TestJob.TAG:
                    return new TestJob();
                default:
                    throw new RuntimeException("Cannot find job for tag " + tag);
            }
        }
    }
}
