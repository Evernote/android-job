package com.evernote.android.job;

import android.os.Build;
import android.support.test.filters.LargeTest;
import android.support.test.runner.AndroidJUnit4;

import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class Platform21Test {

    @Rule
    public JobManagerRule mJobManagerRule = new JobManagerRule();

    @Test(expected = IllegalStateException.class)
    public void test100DistinctJobsLimit() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1);

        for (int i = 0; i < 130; i++) {
            new JobRequest.Builder("tag")
                    .setExecutionWindow(30_000, 40_000)
                    .build()
                    .schedule();
        }
    }
}
