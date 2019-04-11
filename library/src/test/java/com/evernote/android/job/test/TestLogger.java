package com.evernote.android.job.test;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.job.util.JobCat;

/**
 * @author rwondratschek
 */
public final class TestLogger extends JobCat {

    public static final TestLogger INSTANCE = new TestLogger();

    private TestLogger() {
        super("JobCat", false); // disabled
    }

    @Override
    public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
        if (mEnabled) {
            System.out.println(message);
            if (t != null) {
                t.printStackTrace();
            }
        }
    }
}
