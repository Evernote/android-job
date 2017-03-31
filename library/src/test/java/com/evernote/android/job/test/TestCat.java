package com.evernote.android.job.test;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import net.vrallev.android.cat.instance.CatLazy;
import net.vrallev.android.cat.print.CatPrinter;

/**
 * @author rwondratschek
 */
public final class TestCat extends CatLazy implements CatPrinter {

    public static final TestCat INSTANCE = new TestCat();

    private TestCat() {
        // no op
    }

    @Override
    protected void println(int priority, String message, Throwable t) {
        println(priority, "Unit-Test", message, t);
    }

    @Override
    public void println(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
        // System.out.println(message);
        // if (t != null) {
        //     t.printStackTrace();
        // }
    }
}
