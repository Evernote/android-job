package com.evernote.android.job;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.LargeTest;
import com.evernote.android.job.v21.PlatformJobService;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 * @since 09.05.17
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DisabledServiceTest {

    private Context mContext;
    private PackageManager mPackageManager;
    private ComponentName mComponent;

    @Rule
    public PlatformJobManagerRule mJobManagerRule = new PlatformJobManagerRule();

    @Before
    public void prepare() {
        mContext = ApplicationProvider.getApplicationContext();
        mPackageManager = mContext.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mComponent = new ComponentName(mContext, PlatformJobService.class);
        }
    }

    @After
    public void cleanup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            mPackageManager.setComponentEnabledSetting(mComponent, PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, PackageManager.DONT_KILL_APP);
        }
    }

    @Test
    public void verifyJobApiNotSupportedWhenServiceIsDisabled() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            assertThat(JobApi.V_26.isSupported(mContext)).isTrue();
        }
        assertThat(JobApi.V_24.isSupported(mContext)).isTrue();
        assertThat(JobApi.V_21.isSupported(mContext)).isTrue();

        mPackageManager.setComponentEnabledSetting(mComponent, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);

        assertThat(JobApi.V_26.isSupported(mContext)).isFalse();
        assertThat(JobApi.V_24.isSupported(mContext)).isFalse();
        assertThat(JobApi.V_21.isSupported(mContext)).isFalse();
    }
}
