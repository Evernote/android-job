package com.evernote.android.job.test;

import org.junit.runners.model.InitializationError;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * @author rwondratschek
 */
public class JobRobolectricTestRunner extends RobolectricTestRunner {

    public JobRobolectricTestRunner(Class<?> testClass) throws InitializationError {
        super(testClass);
    }

    @Override
    protected Config buildGlobalConfig() {
        return new Config.Builder()
                .setSdk(25) // TODO: change to 26 when Robolectric supports Android O final
                .build();
    }
}
