package com.evernote.android.job;

import android.content.ContentValues;
import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import com.evernote.android.job.util.support.PersistableBundleCompat;
import java.util.concurrent.TimeUnit;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static com.evernote.android.job.JobStorage.COLUMN_BACKOFF_MS;
import static com.evernote.android.job.JobStorage.COLUMN_BACKOFF_POLICY;
import static com.evernote.android.job.JobStorage.COLUMN_END_MS;
import static com.evernote.android.job.JobStorage.COLUMN_EXACT;
import static com.evernote.android.job.JobStorage.COLUMN_EXTRAS;
import static com.evernote.android.job.JobStorage.COLUMN_FLEX_MS;
import static com.evernote.android.job.JobStorage.COLUMN_FLEX_SUPPORT;
import static com.evernote.android.job.JobStorage.COLUMN_ID;
import static com.evernote.android.job.JobStorage.COLUMN_INTERVAL_MS;
import static com.evernote.android.job.JobStorage.COLUMN_LAST_RUN;
import static com.evernote.android.job.JobStorage.COLUMN_NETWORK_TYPE;
import static com.evernote.android.job.JobStorage.COLUMN_NUM_FAILURES;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIREMENTS_ENFORCED;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIRES_BATTERY_NOT_LOW;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIRES_CHARGING;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIRES_DEVICE_IDLE;
import static com.evernote.android.job.JobStorage.COLUMN_REQUIRES_STORAGE_NOT_LOW;
import static com.evernote.android.job.JobStorage.COLUMN_SCHEDULED_AT;
import static com.evernote.android.job.JobStorage.COLUMN_STARTED;
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
@SuppressWarnings("unused")
public class DatabaseManualUpgradeTest extends BaseJobManagerTest {

    @Test
    public void testDatabaseUpgrade1to6() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper1 openHelper = new JobOpenHelper1(context);
        createDatabase(openHelper, false);
        createJobs(openHelper);

        checkJob();
    }

    @Test
    public void testDatabaseUpgrade2to6() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper2 openHelper = new JobOpenHelper2(context);
        createDatabase(openHelper, false);
        createJobs(openHelper);

        checkJob();
    }

    @Test
    public void testDatabaseUpgrade3to6() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper3 openHelper = new JobOpenHelper3(context);
        createDatabase(openHelper, false);
        createJobs(openHelper, true);

        checkJob();
    }

    @Test
    public void testDatabaseUpgrade4to6() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper4 openHelper = new JobOpenHelper4(context);
        createDatabase(openHelper, false);
        createJobs(openHelper, true);

        checkJob();
    }

    @Test
    public void testDatabaseUpgrade5to6() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper5 openHelper = new JobOpenHelper5(context);
        createDatabase(openHelper, false);
        createJobs(openHelper, true);

        checkJob();
    }

    @Test
    public void testDatabaseUpgrade1to2to3to4to5to6() {
        Context context = ApplicationProvider.getApplicationContext();
        context.deleteDatabase(DATABASE_NAME);

        JobOpenHelper1 openHelper = new JobOpenHelper1(context);
        createDatabase(openHelper, false);
        createJobs(openHelper);

        createDatabase(new JobOpenHelper2(context), true);
        createDatabase(new JobOpenHelper3(context), true);
        createDatabase(new JobOpenHelper4(context), true);
        createDatabase(new JobOpenHelper5(context), true);

        checkJob();
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
        createJobs(openHelper, false);
    }

    private void createJobs(UpgradeAbleJobOpenHelper openHelper, boolean validInterval) {
        SQLiteDatabase database = openHelper.getWritableDatabase();

        ContentValues contentValues = openHelper.createBaseContentValues(1);
        contentValues.put(JobStorage.COLUMN_START_MS, 60_000L);
        contentValues.put(JobStorage.COLUMN_END_MS, 120_000L);
        database.insert(JobStorage.JOB_TABLE_NAME, null, contentValues);

        contentValues = openHelper.createBaseContentValues(2);
        if (validInterval) {
            contentValues.put(JobStorage.COLUMN_INTERVAL_MS, JobRequest.MIN_INTERVAL);
            contentValues.put(JobStorage.COLUMN_FLEX_MS, JobRequest.MIN_INTERVAL);
        } else {
            contentValues.put(JobStorage.COLUMN_INTERVAL_MS, 60_000L);
        }
        database.insert(JobStorage.JOB_TABLE_NAME, null, contentValues);

        contentValues = openHelper.createBaseContentValues(3);
        contentValues.put(JobStorage.COLUMN_INTERVAL_MS, TimeUnit.MINUTES.toMillis(20));
        if (validInterval) {
            contentValues.put(JobStorage.COLUMN_FLEX_MS, TimeUnit.MINUTES.toMillis(20));
        }
        database.insert(JobStorage.JOB_TABLE_NAME, null, contentValues);
    }

    private void checkJob() {
        createManager().addJobCreator(new JobCreator() {
            @Override
            public Job create(@NonNull String tag) {
                return null;
            }
        });

        assertThat(JobManager.instance().getAllJobRequests()).hasSize(3);

        JobRequest jobRequest = JobManager.instance().getJobRequest(1);
        assertThat(jobRequest.isPeriodic()).isFalse();
        assertThat(jobRequest.getStartMs()).isEqualTo(60_000L);
        assertThat(jobRequest.isStarted()).isFalse();

        jobRequest = JobManager.instance().getJobRequest(2);
        assertThat(jobRequest.isPeriodic()).isTrue();
        assertThat(jobRequest.getIntervalMs()).isEqualTo(JobRequest.MIN_INTERVAL);
        assertThat(jobRequest.getFlexMs()).isEqualTo(jobRequest.getIntervalMs());
        assertThat(jobRequest.isStarted()).isFalse();

        jobRequest = JobManager.instance().getJobRequest(3);
        assertThat(jobRequest.isPeriodic()).isTrue();
        assertThat(jobRequest.getIntervalMs()).isEqualTo(TimeUnit.MINUTES.toMillis(20));
        assertThat(jobRequest.getFlexMs()).isEqualTo(jobRequest.getIntervalMs());
        assertThat(jobRequest.isStarted()).isFalse();
        assertThat(jobRequest.isTransient()).isFalse();

        JobManager.instance().cancelAll();

        int jobId = new JobRequest.Builder("Tag")
                .setExact(90_000L)
                .build()
                .schedule();

        assertThat(JobManager.instance().getAllJobRequests()).hasSize(1);

        jobRequest = JobManager.instance().getJobRequest(jobId);
        assertThat(jobRequest).isNotNull();
        assertThat(jobRequest.isStarted()).isFalse();

        jobRequest.setStarted(true);
        assertThat(JobManager.instance().getAllJobRequests()).isEmpty();
        assertThat(JobManager.instance().getAllJobRequests(null, true, true)).hasSize(1);

        JobManager.instance().cancelAll();

        assertThat(JobManager.instance().getAllJobRequests()).isEmpty();
        assertThat(JobManager.instance().getAllJobRequests(null, true, true)).isEmpty();

        JobManager.instance().destroy();
    }

    private abstract static class UpgradeAbleJobOpenHelper extends SQLiteOpenHelper {

        private boolean mDatabaseCreated;
        private boolean mDatabaseUpgraded;

        UpgradeAbleJobOpenHelper(Context context, int version) {
            super(context, DATABASE_NAME, null, version);
        }

        @Override
        public final void onCreate(SQLiteDatabase db) {
            onCreateInner(db);
            mDatabaseCreated = true;
        }

        protected abstract void onCreateInner(SQLiteDatabase db);

        @Override
        public final void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            while (oldVersion < newVersion) {
                switch (oldVersion) {
                    case 1:
                        upgradeFrom1To2(db);
                        oldVersion++;
                        break;
                    case 2:
                        upgradeFrom2To3(db);
                        oldVersion++;
                        break;
                    case 3:
                        upgradeFrom3To4(db);
                        oldVersion++;
                        break;
                    case 4:
                        upgradeFrom4To5(db);
                        oldVersion++;
                        break;
                    case 5:
                        upgradeFrom5To6(db);
                        oldVersion++;
                        break;
                    default:
                        throw new IllegalStateException("not implemented");
                }
            }

            mDatabaseCreated = true;
            mDatabaseUpgraded = true;
        }

        protected void upgradeFrom1To2(SQLiteDatabase db) {
            // override me
        }

        protected void upgradeFrom2To3(SQLiteDatabase db) {
            // override me
        }

        protected void upgradeFrom3To4(SQLiteDatabase db) {
            // override me
        }

        protected void upgradeFrom4To5(SQLiteDatabase db) {
            // override me
        }

        protected void upgradeFrom5To6(SQLiteDatabase db) {
            // override me
        }

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
            contentValues.put("persisted", false);

            contentValues.put(JobStorage.COLUMN_NUM_FAILURES, 0);
            contentValues.put(JobStorage.COLUMN_SCHEDULED_AT, System.currentTimeMillis());

            return contentValues;
        }
    }

    private static class JobOpenHelper1 extends UpgradeAbleJobOpenHelper {

        JobOpenHelper1(Context context) {
            this(context, 1);
        }

        JobOpenHelper1(Context context, int version) {
            super(context, version);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
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
                    + "persisted" + " integer, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer);");
        }
    }

    private static class JobOpenHelper2 extends JobOpenHelper1 {

        JobOpenHelper2(Context context) {
            this(context, 2);
        }

        JobOpenHelper2(Context context, int version) {
            super(context, version);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
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
                    + "persisted" + " integer, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + "isTransient" + " integer);");
        }

        @Override
        protected ContentValues createBaseContentValues(int id) {
            ContentValues contentValues = super.createBaseContentValues(id);
            contentValues.put("isTransient", false);
            return contentValues;
        }

        protected void upgradeFrom1To2(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + "isTransient" + " integer;");
        }
    }

    private static class JobOpenHelper3 extends JobOpenHelper2 {

        JobOpenHelper3(Context context) {
            this(context, 3);
        }

        JobOpenHelper3(Context context, int version) {
            super(context, version);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
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
                    + "persisted" + " integer, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + "isTransient" + " integer, "
                    + COLUMN_FLEX_MS + " integer, "
                    + COLUMN_FLEX_SUPPORT + " integer);");
        }

        protected void upgradeFrom2To3(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_FLEX_MS + " integer;");
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_FLEX_SUPPORT + " integer;");

            // adjust interval to minimum value if necessary
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_INTERVAL_MS, JobRequest.MIN_INTERVAL);
            db.update(JOB_TABLE_NAME, contentValues, COLUMN_INTERVAL_MS + ">0 AND " + COLUMN_INTERVAL_MS + "<" + JobRequest.MIN_INTERVAL, new String[0]);

            // copy interval into flex column, that's the default value and the flex support mode is not required
            db.execSQL("update " + JOB_TABLE_NAME + " set " + COLUMN_FLEX_MS + " = " + COLUMN_INTERVAL_MS + ";");
        }
    }

    private static class JobOpenHelper4 extends JobOpenHelper3 {

        JobOpenHelper4(Context context) {
            this(context, 4);
        }

        JobOpenHelper4(Context context, int version) {
            super(context, version);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
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
                    + "persisted" + " integer, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + "isTransient" + " integer, "
                    + COLUMN_FLEX_MS + " integer, "
                    + COLUMN_FLEX_SUPPORT + " integer, "
                    + COLUMN_LAST_RUN + " integer);");
        }

        protected void upgradeFrom3To4(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_LAST_RUN + " integer;");
        }
    }

    private static class JobOpenHelper5 extends JobOpenHelper4 {

        JobOpenHelper5(Context context) {
            this(context, 5);
        }

        JobOpenHelper5(Context context, int version) {
            super(context, version);
        }

        @Override
        protected ContentValues createBaseContentValues(int id) {
            ContentValues values = super.createBaseContentValues(id);
            values.remove("isTransient");
            values.remove("persisted");
            return values;
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
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
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + COLUMN_STARTED + " integer, "
                    + COLUMN_FLEX_MS + " integer, "
                    + COLUMN_FLEX_SUPPORT + " integer, "
                    + COLUMN_LAST_RUN + " integer, "
                    + COLUMN_TRANSIENT + " integer);");
        }

        @SuppressWarnings("deprecation")
        @Override
        protected void upgradeFrom4To5(SQLiteDatabase db) {
            // remove "persisted" column and rename "isTransient" to "started", add "transient" column for O
            try {
                db.beginTransaction();

                String newTable = JOB_TABLE_NAME + "_new";

                db.execSQL("create table " + newTable + " ("
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
                        + COLUMN_NUM_FAILURES + " integer, "
                        + COLUMN_SCHEDULED_AT + " integer, "
                        + COLUMN_STARTED + " integer, "
                        + COLUMN_FLEX_MS + " integer, "
                        + COLUMN_FLEX_SUPPORT + " integer, "
                        + COLUMN_LAST_RUN + " integer);");

                db.execSQL("INSERT INTO " + newTable + " SELECT "
                        + COLUMN_ID + ","
                        + COLUMN_TAG + ","
                        + COLUMN_START_MS + ","
                        + COLUMN_END_MS + ","
                        + COLUMN_BACKOFF_MS + ","
                        + COLUMN_BACKOFF_POLICY + ","
                        + COLUMN_INTERVAL_MS + ","
                        + COLUMN_REQUIREMENTS_ENFORCED + ","
                        + COLUMN_REQUIRES_CHARGING + ","
                        + COLUMN_REQUIRES_DEVICE_IDLE + ","
                        + COLUMN_EXACT + ","
                        + COLUMN_NETWORK_TYPE + ","
                        + COLUMN_EXTRAS + ","
                        + COLUMN_NUM_FAILURES + ","
                        + COLUMN_SCHEDULED_AT + ","
                        + "isTransient" + ","
                        + COLUMN_FLEX_MS + ","
                        + COLUMN_FLEX_SUPPORT + ","
                        + COLUMN_LAST_RUN + " FROM " + JOB_TABLE_NAME);

                db.execSQL("DROP TABLE " + JOB_TABLE_NAME);
                db.execSQL("ALTER TABLE " + newTable + " RENAME TO " + JOB_TABLE_NAME);

                db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_TRANSIENT + " integer;");

                db.setTransactionSuccessful();
            } finally {
                db.endTransaction();
            }
        }
    }

    private static final class JobOpenHelper6 extends JobOpenHelper5 {

        JobOpenHelper6(Context context) {
            this(context, 6);
        }

        JobOpenHelper6(Context context, int version) {
            super(context, version);
        }

        @Override
        public void onCreateInner(SQLiteDatabase db) {
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
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + COLUMN_STARTED + " integer, "
                    + COLUMN_FLEX_MS + " integer, "
                    + COLUMN_FLEX_SUPPORT + " integer, "
                    + COLUMN_LAST_RUN + " integer, "
                    + COLUMN_TRANSIENT + " integer, "
                    + COLUMN_REQUIRES_BATTERY_NOT_LOW + " integer, "
                    + COLUMN_REQUIRES_STORAGE_NOT_LOW +" integer);");
        }

        @Override
        protected void upgradeFrom5To6(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_REQUIRES_BATTERY_NOT_LOW + " integer;");
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_REQUIRES_STORAGE_NOT_LOW + " integer;");
        }
    }
}
