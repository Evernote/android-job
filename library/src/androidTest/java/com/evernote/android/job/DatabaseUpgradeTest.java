package com.evernote.android.job;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static com.evernote.android.job.JobStorage.COLUMN_BACKOFF_MS;
import static com.evernote.android.job.JobStorage.COLUMN_BACKOFF_POLICY;
import static com.evernote.android.job.JobStorage.COLUMN_END_MS;
import static com.evernote.android.job.JobStorage.COLUMN_EXACT;
import static com.evernote.android.job.JobStorage.COLUMN_EXTRAS;
import static com.evernote.android.job.JobStorage.COLUMN_ID;
import static com.evernote.android.job.JobStorage.COLUMN_INTERVAL_MS;
import static com.evernote.android.job.JobStorage.COLUMN_NETWORK_TYPE;
import static com.evernote.android.job.JobStorage.COLUMN_NUM_FAILURES;
import static com.evernote.android.job.JobStorage.COLUMN_PERSISTED;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIREMENTS_ENFORCED;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIRES_CHARGING;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIRES_DEVICE_IDLE;
import static com.evernote.android.job.JobStorage.COLUMN_SCHEDULED_AT;
import static com.evernote.android.job.JobStorage.COLUMN_START_MS;
import static com.evernote.android.job.JobStorage.COLUMN_TAG;
import static com.evernote.android.job.JobStorage.DATABASE_NAME;
import static com.evernote.android.job.JobStorage.JOB_TABLE_NAME;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(AndroidJUnit4.class)
@LargeTest
public class DatabaseUpgradeTest {

    @Test
    public void testDatabaseUpgrade() {
        Context context = InstrumentationRegistry.getContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper1 jobOpenHelper = new JobOpenHelper1(context);
        SQLiteDatabase database = jobOpenHelper.getWritableDatabase();
        assertThat(jobOpenHelper.mDatabaseCreated).isTrue();

        database.close();

        JobManager.create(context).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return null;
            }
        });

        int jobId = new JobRequest.Builder("Tag")
                .setExact(90_000L)
                .build()
                .schedule();

        assertThat(JobManager.instance().getAllJobRequests()).hasSize(1);

        JobRequest jobRequest = JobManager.instance().getJobRequest(jobId);
        assertThat(jobRequest).isNotNull();
        assertThat(jobRequest.isTransient()).isFalse();

        jobRequest.setTransient(true);
        assertThat(JobManager.instance().getAllJobRequests()).isEmpty();
        assertThat(JobManager.instance().getJobStorage().getAllJobRequests(null, true)).hasSize(1);

        JobManager.instance().cancelAll();

        assertThat(JobManager.instance().getAllJobRequests()).isEmpty();
        assertThat(JobManager.instance().getJobStorage().getAllJobRequests(null, true)).isEmpty();
    }

    @After
    public void tearDown() {
        JobManager.instance().cancelAll();
    }

    private class JobOpenHelper1 extends SQLiteOpenHelper {

        private boolean mDatabaseCreated;

        public JobOpenHelper1(Context context) {
            super(context, DATABASE_NAME, null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createJobTable(db);
            mDatabaseCreated = true;
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // not needed at the moment
        }

        private void createJobTable(SQLiteDatabase db) {
            db.execSQL("create table " + JOB_TABLE_NAME + " ("
                    + COLUMN_ID + " integer primary key, "
                    + COLUMN_TAG + " text not null, "
                    + COLUMN_START_MS + " integer, "
                    + COLUMN_END_MS + " integer, "
                    + COLUMN_BACKOFF_MS + " integer, "
                    + COLUMN_BACKOFF_POLICY + " text not null, "
                    + COLUMN_INTERVAL_MS + " integer, "
                    + COLUMN_REQUIREMENTS_ENFORCED + " integer, "
                    + COLUMN_REQUIRES_CHARGING + " integer, "
                    + COLUMN_REQUIRES_DEVICE_IDLE + " integer, "
                    + COLUMN_EXACT + " integer, "
                    + COLUMN_NETWORK_TYPE + " text not null, "
                    + COLUMN_EXTRAS + " text, "
                    + COLUMN_PERSISTED + " integer, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer);");
        }
    }
}
