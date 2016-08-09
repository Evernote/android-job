package com.evernote.android.job;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.test.suitebuilder.annotation.LargeTest;

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
import static com.evernote.android.job.JobStorage.COLUMN_TRANSIENT;
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
    public void testDatabaseUpgrade1to3() {
        Context context = InstrumentationRegistry.getContext();
        context.deleteDatabase(DATABASE_NAME);

        createDatabase(new JobOpenHelper1(context), false);
        checkJob(context);
    }

    @Test
    public void testDatabaseUpgrade2to3() {
        Context context = InstrumentationRegistry.getContext();
        context.deleteDatabase(DATABASE_NAME);

        createDatabase(new JobOpenHelper2(context), false);
        checkJob(context);
    }

    @Test
    public void testDatabaseUpgrade1to2to3() {
        Context context = InstrumentationRegistry.getContext();
        context.deleteDatabase(DATABASE_NAME);

        createDatabase(new JobOpenHelper1(context), false);
        createDatabase(new JobOpenHelper2(context), true);

        checkJob(context);
    }

    private void createDatabase(UpgradeAbleJobOpenHelper openHelper, boolean checkUpgraded) {
        SQLiteDatabase database = openHelper.getWritableDatabase();
        assertThat(openHelper.mDatabaseCreated).isTrue();
        if (checkUpgraded) {
            assertThat(openHelper.mDatabaseUpgraded).isTrue();
        }

        database.close();
    }

    private void checkJob(Context context) {
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

        JobManager.instance().destroy();
    }

    private abstract class UpgradeAbleJobOpenHelper extends SQLiteOpenHelper {

        private boolean mDatabaseCreated;
        private boolean mDatabaseUpgraded;

        public UpgradeAbleJobOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
            super(context, name, factory, version);
        }

        @Override
        public final void onCreate(SQLiteDatabase db) {
            onCreateInner(db);
            mDatabaseCreated = true;
        }

        protected abstract void onCreateInner(SQLiteDatabase db);

        @Override
        public final void onUpgrade(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion) {
            onUpgradeInner(sqLiteDatabase, oldVersion, newVersion);
            mDatabaseCreated = true;
            mDatabaseUpgraded = true;
        }

        protected abstract void onUpgradeInner(SQLiteDatabase sqLiteDatabase, int oldVersion, int newVersion);
    }

    private class JobOpenHelper1 extends UpgradeAbleJobOpenHelper {

        public JobOpenHelper1(Context context) {
            super(context, DATABASE_NAME, null, 1);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
            createJobTable(db);
        }

        @Override
        public void onUpgradeInner(SQLiteDatabase db, int oldVersion, int newVersion) {
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

    private class JobOpenHelper2 extends UpgradeAbleJobOpenHelper {

        public JobOpenHelper2(Context context) {
            super(context, DATABASE_NAME, null, 2);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
            createJobTable(db);
        }

        @Override
        public void onUpgradeInner(SQLiteDatabase db, int oldVersion, int newVersion) {
            // with newer versions there should be a smarter way
            if (oldVersion == 1 && newVersion == 2) {
                upgradeFrom1To2(db);
            }
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
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + COLUMN_TRANSIENT + " integer);");
        }

        private void upgradeFrom1To2(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_TRANSIENT + " integer;");
        }
    }
}
