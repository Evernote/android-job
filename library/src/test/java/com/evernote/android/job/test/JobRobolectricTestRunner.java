package com.evernote.android.job.test;

import com.evernote.android.job.BuildConfig;

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
        .setConstants(BuildConfig.class)
        .build();
  }
}
