package com.evernote.android.job.test;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.util.JobLogger;

import net.vrallev.android.cat.instance.CatLazy;
import net.vrallev.android.cat.print.CatPrinter;

/**
 * @author rwondratschek
 */
public final class TestLogger extends CatLazy implements CatPrinter, JobLogger {

    public static final TestLogger INSTANCE = new TestLogger();

    private TestLogger() {
        // no op
    }

    @Override
    protected void println(int priority, String message, Throwable t) {
        println(priority, "Unit-Test", message, t);
    }

    @Override
    public void println(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
        log(priority, tag, message, t);
    }

    @Override
    public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
        // System.out.println(message);
        // if (t != null) {
        //     t.printStackTrace();
        // }
    }
}
