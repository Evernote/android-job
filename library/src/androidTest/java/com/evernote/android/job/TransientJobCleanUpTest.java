package com.evernote.android.job;

import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.os.Build;
import android.os.Bundle;
import androidx.test.filters.LargeTest;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assume.assumeTrue;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class TransientJobCleanUpTest {

    private JobManager mManager;

    @Rule
    public PlatformJobManagerRule mJobManagerRule = new PlatformJobManagerRule();

    @Before
    public void prepare() {
        mManager = mJobManagerRule.getManager();
    }

    @Test
    public void verifyJobDeletedFromDatabaseSpecific() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        JobConfig.forceApi(JobApi.V_21);

        Bundle bundle = new Bundle();
        bundle.putString("key", "value");

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(40_000, 50_000)
                .setTransientExtras(bundle)
                .build()
                .schedule();

        assertThat(mManager.getAllJobRequests()).hasSize(1);
        assertThat(mManager.getJobRequest(jobId)).isNotNull();

        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).hasSize(1);

        mJobManagerRule.getJobScheduler().cancel(jobId);

        // cached request gone
        assertThat(mManager.getJobRequest(jobId)).isNull();

        try (SQLiteDatabase database = mManager.getJobStorage().getDatabase()) {
            long numEntries = DatabaseUtils.queryNumEntries(database, JobStorage.JOB_TABLE_NAME);
            assertThat(numEntries).isEqualTo(0);
        }
    }

    @Test
    public void verifyJobDeletedFromDatabaseAll() {
        assumeTrue(Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP);
        JobConfig.forceApi(JobApi.V_21);

        Bundle bundle = new Bundle();
        bundle.putString("key", "value");

        int jobId = new JobRequest.Builder("tag")
                .setExecutionWindow(40_000, 50_000)
                .setTransientExtras(bundle)
                .build()
                .schedule();

        assertThat(mManager.getAllJobRequests()).hasSize(1);
        assertThat(mManager.getJobRequest(jobId)).isNotNull();

        assertThat(mJobManagerRule.getAllPendingJobsFromScheduler()).hasSize(1);

        mJobManagerRule.getJobScheduler().cancel(jobId);

        // cached request gone
        assertThat(mManager.getAllJobRequests()).isEmpty();

        try (SQLiteDatabase database = mManager.getJobStorage().getDatabase()) {
            long numEntries = DatabaseUtils.queryNumEntries(database, JobStorage.JOB_TABLE_NAME);
            assertThat(numEntries).isEqualTo(0);
        }
    }
}
