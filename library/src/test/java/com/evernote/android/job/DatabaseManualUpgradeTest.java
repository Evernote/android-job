package com.evernote.android.job;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.support.PersistableBundleCompat;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.TimeUnit;

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
import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class DatabaseManualUpgradeTest {

    @Test
    public void testDatabaseUpgrade1to3() {
        Context context = RuntimeEnvironment.application;
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper1 openHelper = new JobOpenHelper1(context);
        createDatabase(openHelper, false);
        createJobs(openHelper);

        checkJob(context);
    }

    @Test
    public void testDatabaseUpgrade2to3() {
        Context context = RuntimeEnvironment.application;
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper2 openHelper = new JobOpenHelper2(context);
        createDatabase(openHelper, false);
        createJobs(openHelper);

        checkJob(context);
    }

    @Test
    public void testDatabaseUpgrade1to2to3() {
        Context context = RuntimeEnvironment.application;
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper1 openHelper = new JobOpenHelper1(context);
        createDatabase(openHelper, false);
        createJobs(openHelper);

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

    private void createJobs(UpgradeAbleJobOpenHelper openHelper) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        ContentValues contentValues = openHelper.createBaseContentValues(1);
        contentValues.put(JobStorage.COLUMN_START_MS, 60_000L);
        contentValues.put(JobStorage.COLUMN_END_MS, 120_000L);
        database.insert(JobStorage.JOB_TABLE_NAME, null, contentValues);

        contentValues = openHelper.createBaseContentValues(2);
        contentValues.put(JobStorage.COLUMN_INTERVAL_MS, 60_000L);
        database.insert(JobStorage.JOB_TABLE_NAME, null, contentValues);

        contentValues = openHelper.createBaseContentValues(3);
        contentValues.put(JobStorage.COLUMN_INTERVAL_MS, TimeUnit.MINUTES.toMillis(20));
        database.insert(JobStorage.JOB_TABLE_NAME, null, contentValues);
    }

    private void checkJob(Context context) {
        JobManager.create(context).addJobCreator(new JobCreator() {
            @Override
            public Job create(String tag) {
                return null;
            }
        });

        assertThat(JobManager.instance().getAllJobRequests()).hasSize(3);

        JobRequest jobRequest = JobManager.instance().getJobRequest(1);
        assertThat(jobRequest.isPeriodic()).isFalse();
        assertThat(jobRequest.getStartMs()).isEqualTo(60_000L);
        assertThat(jobRequest.isTransient()).isFalse();

        jobRequest = JobManager.instance().getJobRequest(2);
        assertThat(jobRequest.isPeriodic()).isTrue();
        assertThat(jobRequest.getIntervalMs()).isEqualTo(JobRequest.MIN_INTERVAL);
        assertThat(jobRequest.getFlexMs()).isEqualTo(jobRequest.getIntervalMs());
        assertThat(jobRequest.isTransient()).isFalse();

        jobRequest = JobManager.instance().getJobRequest(3);
        assertThat(jobRequest.isPeriodic()).isTrue();
        assertThat(jobRequest.getIntervalMs()).isEqualTo(TimeUnit.MINUTES.toMillis(20));
        assertThat(jobRequest.getFlexMs()).isEqualTo(jobRequest.getIntervalMs());
        assertThat(jobRequest.isTransient()).isFalse();

        JobManager.instance().cancelAll();

        int jobId = new JobRequest.Builder("Tag")
                .setExact(90_000L)
                .build()
                .schedule();

        assertThat(JobManager.instance().getAllJobRequests()).hasSize(1);

        jobRequest = JobManager.instance().getJobRequest(jobId);
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

    private abstract static class UpgradeAbleJobOpenHelper extends SQLiteOpenHelper {

        private boolean mDatabaseCreated;
        private boolean mDatabaseUpgraded;

        UpgradeAbleJobOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
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

        protected ContentValues createBaseContentValues(int id) {
            ContentValues contentValues = new ContentValues();
            contentValues.put(JobStorage.COLUMN_ID, id);
            contentValues.put(JobStorage.COLUMN_TAG, "Tag");

            contentValues.put(JobStorage.COLUMN_START_MS, -1L);
            contentValues.put(JobStorage.COLUMN_END_MS, -1L);

            contentValues.put(JobStorage.COLUMN_BACKOFF_MS, JobRequest.DEFAULT_BACKOFF_MS);
            contentValues.put(JobStorage.COLUMN_BACKOFF_POLICY, JobRequest.DEFAULT_BACKOFF_POLICY.toString());

            contentValues.put(JobStorage.COLUMN_INTERVAL_MS, 0L);

            contentValues.put(JobStorage.COLUMN_REQUIREMENTS_ENFORCED, false);
            contentValues.put(JobStorage.COLUMN_REQUIRES_CHARGING, false);
            contentValues.put(JobStorage.COLUMN_REQUIRES_DEVICE_IDLE, false);
            contentValues.put(JobStorage.COLUMN_EXACT, false);
            contentValues.put(JobStorage.COLUMN_NETWORK_TYPE, JobRequest.DEFAULT_NETWORK_TYPE.toString());

            contentValues.put(JobStorage.COLUMN_EXTRAS, new PersistableBundleCompat().saveToXml());
            contentValues.put(JobStorage.COLUMN_PERSISTED, false);

            contentValues.put(JobStorage.COLUMN_NUM_FAILURES, 0);
            contentValues.put(JobStorage.COLUMN_SCHEDULED_AT, System.currentTimeMillis());

            return contentValues;
        }
    }

    private static final class JobOpenHelper1 extends UpgradeAbleJobOpenHelper {

        JobOpenHelper1(Context context) {
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

    private static final class JobOpenHelper2 extends UpgradeAbleJobOpenHelper {

        JobOpenHelper2(Context context) {
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

        @Override
        protected ContentValues createBaseContentValues(int id) {
            ContentValues contentValues = super.createBaseContentValues(id);
            contentValues.put(JobStorage.COLUMN_TRANSIENT, false);
            return contentValues;
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
