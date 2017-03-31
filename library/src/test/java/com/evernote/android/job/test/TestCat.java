package com.evernote.android.job.test;

import net.vrallev.android.cat.instance.CatLazy;

/**
 * @author rwondratschek
 */
public class TestCat extends CatLazy {

    @Override
    protected void println(int priority, String message, Throwable t) {
        //System.out.println(message);
        //if (t != null) {
        //    t.printStackTrace();
        //}
    }
}
