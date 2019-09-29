/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job;

import android.annotation.SuppressLint;
import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteCantOpenDatabaseException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.LruCache;

import com.evernote.android.job.util.JobCat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author rwondratschek
 */
@SuppressWarnings("WeakerAccess")
@RestrictTo(RestrictTo.Scope.LIBRARY)
/*package*/ class JobStorage {

    private static final JobCat CAT = new JobCat("JobStorage");

    public static final String JOB_ID_COUNTER = "JOB_ID_COUNTER_v2";
    private static final String FAILED_DELETE_IDS = "FAILED_DELETE_IDS";

    public static final String PREF_FILE_NAME = "evernote_jobs";
    public static final String DATABASE_NAME = PREF_FILE_NAME + ".db";
    public static final int DATABASE_VERSION = 6;

    public static final String JOB_TABLE_NAME = "jobs";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_TAG = "tag";
    public static final String COLUMN_START_MS = "startMs";
    public static final String COLUMN_END_MS = "endMs";
    public static final String COLUMN_BACKOFF_MS = "backoffMs";
    public static final String COLUMN_BACKOFF_POLICY = "backoffPolicy";
    public static final String COLUMN_INTERVAL_MS = "intervalMs";
    public static final String COLUMN_REQUIREMENTS_ENFORCED = "requirementsEnforced";
    public static final String COLUMN_REQUIRES_CHARGING = "requiresCharging";
    public static final String COLUMN_REQUIRES_DEVICE_IDLE = "requiresDeviceIdle";
    public static final String COLUMN_EXACT = "exact";
    public static final String COLUMN_NETWORK_TYPE = "networkType";
    public static final String COLUMN_EXTRAS = "extras";

    @SuppressWarnings("unused")
    @Deprecated
    private static final String COLUMN_PERSISTED = "persisted";

    public static final String COLUMN_NUM_FAILURES = "numFailures";
    public static final String COLUMN_SCHEDULED_AT = "scheduledAt";

    @SuppressWarnings("DeprecatedIsStillUsed")
    @Deprecated
    private static final String COLUMN_TRANSIENT_OLD = "isTransient";

    public static final String COLUMN_STARTED = "started";
    public static final String COLUMN_FLEX_MS = "flexMs";
    public static final String COLUMN_FLEX_SUPPORT = "flexSupport";
    public static final String COLUMN_LAST_RUN = "lastRun";
    public static final String COLUMN_TRANSIENT = "transient";
    public static final String COLUMN_REQUIRES_BATTERY_NOT_LOW = "requiresBatteryNotLow";
    public static final String COLUMN_REQUIRES_STORAGE_NOT_LOW = "requiresStorageNotLow";

    private static final int CACHE_SIZE = 30;

    private static final String WHERE_NOT_STARTED = "ifnull(" + COLUMN_STARTED + ", 0)<=0";

    private final SharedPreferences mPreferences;
    private final JobCacheId mCacheId;

    private AtomicInteger mJobCounter;
    private final Set<String> mFailedDeleteIds;

    private final JobOpenHelper mDbHelper;
    private SQLiteDatabase mInjectedDatabase;

    private final ReadWriteLock mLock;

    public JobStorage(Context context) {
        this(context, DATABASE_NAME);
    }

    public JobStorage(Context context, String databasePath) {
        mPreferences = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);
        mLock = new ReentrantReadWriteLock();

        mCacheId = new JobCacheId();

        mDbHelper = new JobOpenHelper(context, databasePath);

        mFailedDeleteIds = mPreferences.getStringSet(FAILED_DELETE_IDS, new HashSet<String>());
        if (!mFailedDeleteIds.isEmpty()) {
            tryToCleanupFinishedJobs();
        }
    }

    public void put(final JobRequest request) {
        mLock.writeLock().lock();
        try {
            // don't write to db async, there could be a race condition with remove()
            store(request);

            // put in cache if store() was successful
            updateRequestInCache(request);
        } finally {
            mLock.writeLock().unlock();
        }
    }

    public void update(JobRequest request, ContentValues contentValues) {
        SQLiteDatabase database = null;
        mLock.writeLock().lock();

        try {
            updateRequestInCache(request);
            database = getDatabase();
            database.update(JOB_TABLE_NAME, contentValues, COLUMN_ID + "=?", new String[]{String.valueOf(request.getJobId())});
        } catch (Exception e) {
            // catch the exception here and keep what's in the database
            CAT.e(e, "could not update %s", request);
        } finally {
            closeDatabase(database);
            mLock.writeLock().unlock();
        }
    }

    private void updateRequestInCache(JobRequest request) {
        mCacheId.put(request.getJobId(), request);
    }

    public JobRequest get(int id) {
        mLock.readLock().lock();
        try {
            // not necessary to check if failed to delete, the cache is doing this
            return mCacheId.get(id);
        } finally {
            mLock.readLock().unlock();
        }
    }

    public Set<JobRequest> getAllJobRequests(@Nullable String tag, boolean includeStarted) {
        Set<JobRequest> result = new HashSet<>();

        SQLiteDatabase database = null;
        Cursor cursor = null;

        mLock.readLock().lock();

        try {
            String where; // filter started requests
            String[] args;
            if (TextUtils.isEmpty(tag)) {
                where = includeStarted ? null : WHERE_NOT_STARTED;
                args = null;
            } else {
                where = includeStarted ? "" : (WHERE_NOT_STARTED + " AND ");
                where += COLUMN_TAG + "=?";
                args = new String[]{tag};
            }

            database = getDatabase();
            cursor = database.query(JOB_TABLE_NAME, null, where, args, null, null, null);

            @SuppressLint("UseSparseArrays")
            HashMap<Integer, JobRequest> cachedRequests = new HashMap<>(mCacheId.snapshot());

            while (cursor != null && cursor.moveToNext()) {
                // check in cache first, can avoid creating many JobRequest objects
                Integer id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                if (!didFailToDelete(id)) {
                    if (cachedRequests.containsKey(id)) {
                        result.add(cachedRequests.get(id));
                    } else {
                        result.add(JobRequest.fromCursor(cursor));
                    }
                }
            }
        } catch (Exception e) {
            CAT.e(e, "could not load all jobs");

        } finally {
            closeCursor(cursor);
            closeDatabase(database);
            mLock.readLock().unlock();
        }

        return result;
    }

    public void remove(JobRequest request) {
        remove(request, request.getJobId());
    }

    private boolean remove(@Nullable JobRequest request, int jobId) {
        SQLiteDatabase database = null;
        mLock.writeLock().lock();

        try {
            mCacheId.remove(jobId);

            database = getDatabase();
            database.delete(JOB_TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(jobId)});
            return true;
        } catch (Exception e) {
            CAT.e(e, "could not delete %d %s", jobId, request);
            addFailedDeleteId(jobId);
            return false;
        } finally {
            closeDatabase(database);
            mLock.writeLock().unlock();
        }
    }

    public synchronized int nextJobId() {
        if (mJobCounter == null) {
            mJobCounter = new AtomicInteger(getMaxJobId());
        }

        int id = mJobCounter.incrementAndGet();

        int offset = JobConfig.getJobIdOffset();
        if (id < offset || id >= JobIdsInternal.RESERVED_JOB_ID_RANGE_START) {
            /*
             * An overflow occurred. It'll happen rarely, but just in case reset the ID and start from scratch.
             * Existing jobs will be treated as orphaned and will be overwritten.
             */
            mJobCounter.set(offset);
            id = mJobCounter.incrementAndGet();
        }

        mPreferences.edit().putInt(JOB_ID_COUNTER, id).apply();

        return id;
    }

    private void store(JobRequest request) {
        ContentValues contentValues = request.toContentValues();
        SQLiteDatabase database = null;
        try {
            database = getDatabase();
            /*
             * It could happen that a conflict with the job ID occurs, when a job was cancelled (cancelAndEdit())
             * the builder object scheduled twice. In this case the last call wins and the value in the database
             * will be overwritten.
             */
            if (database.insertWithOnConflict(JOB_TABLE_NAME, null, contentValues, SQLiteDatabase.CONFLICT_REPLACE) < 0) {
                throw new SQLException("Couldn't insert job request into database");
            }
        } finally {
            closeDatabase(database);
        }
    }

    @SuppressWarnings("SameParameterValue")
    private JobRequest load(int id, boolean includeStarted) {
        if (didFailToDelete(id)) {
            return null;
        }

        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            String where = COLUMN_ID + "=?";
            if (!includeStarted) {
                where += " AND " + COLUMN_STARTED + "<=0";
            }

            database = getDatabase();
            cursor = database.query(JOB_TABLE_NAME, null, where, new String[]{String.valueOf(id)}, null, null, null);
            if (cursor != null && cursor.moveToFirst()) {
                return JobRequest.fromCursor(cursor);
            }

        } catch (Exception e) {
            CAT.e(e, "could not load id %d", id);

        } finally {
            closeCursor(cursor);
            closeDatabase(database);
        }

        return null;
    }

    @NonNull
    @VisibleForTesting
    /*package*/ SQLiteDatabase getDatabase() {
        if (mInjectedDatabase != null) {
            return mInjectedDatabase;
        } else {
            try {
                return mDbHelper.getWritableDatabase();

            } catch (SQLiteCantOpenDatabaseException e) {
                CAT.e(e);

                // that's bad, delete the database and try again, otherwise users may get stuck in a loop
                new JobStorageDatabaseErrorHandler().deleteDatabaseFile(DATABASE_NAME);
                return mDbHelper.getWritableDatabase();
            }
        }
    }

    @VisibleForTesting
    /*package*/ void injectDatabase(SQLiteDatabase database) {
        mInjectedDatabase = database;
    }

    @VisibleForTesting
    /*package*/ Set<String> getFailedDeleteIds() {
        return mFailedDeleteIds;
    }

    @VisibleForTesting
    /*package*/ int getMaxJobId() {
        SQLiteDatabase database = null;
        Cursor cursor = null;

        int jobId = 0;

        try {
            database = getDatabase();
            cursor = database.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + JOB_TABLE_NAME, null);
            if (cursor != null && cursor.moveToFirst()) {
                jobId = cursor.getInt(0);
            }
        } catch (Exception e) {
            CAT.e(e);

        } finally {
            closeCursor(cursor);
            closeDatabase(database);
        }

        return Math.max(JobConfig.getJobIdOffset(), Math.max(jobId, mPreferences.getInt(JOB_ID_COUNTER, 0)));
    }

    private void addFailedDeleteId(int id) {
        synchronized (mFailedDeleteIds) {
            mFailedDeleteIds.add(String.valueOf(id));
            mPreferences.edit().putStringSet(FAILED_DELETE_IDS, mFailedDeleteIds).apply();
        }
    }

    private boolean didFailToDelete(int id) {
        synchronized (mFailedDeleteIds) {
            return !mFailedDeleteIds.isEmpty() && mFailedDeleteIds.contains(String.valueOf(id));
        }
    }

    private void tryToCleanupFinishedJobs() {
        new Thread("CleanupFinishedJobsThread") {
            @Override
            public void run() {
                Set<String> ids;
                synchronized (mFailedDeleteIds) {
                    ids = new HashSet<>(mFailedDeleteIds);
                }

                Iterator<String> iterator = ids.iterator();
                while (iterator.hasNext()) {
                    String idString = iterator.next();
                    try {
                        int jobId = Integer.parseInt(idString);
                        if (remove(null, jobId)) {
                            iterator.remove();
                            CAT.i("Deleted job %d which failed to delete earlier", jobId);
                        } else {
                            CAT.e("Couldn't delete job %d which failed to delete earlier", jobId);
                        }

                    } catch (NumberFormatException e) {
                        iterator.remove();
                    }
                }

                synchronized (mFailedDeleteIds) {
                    mFailedDeleteIds.clear();

                    // that's too bad, but there must be something wrong with the device
                    if (ids.size() > 50) {
                        int counter = 0;
                        for (String id : ids) {
                            if (counter++ > 50) {
                                break;
                            }
                            mFailedDeleteIds.add(id);
                        }
                    } else {
                        mFailedDeleteIds.addAll(ids);
                    }
                }
            }
        }.start();
    }

    private class JobCacheId extends LruCache<Integer, JobRequest> {

        public JobCacheId() {
            super(CACHE_SIZE);
        }

        @Override
        protected JobRequest create(Integer id) {
            return load(id, true);
        }
    }

    private static final class JobOpenHelper extends SQLiteOpenHelper {

        private JobOpenHelper(Context context, String databasePath) {
            super(context, databasePath, null, DATABASE_VERSION, new JobStorageDatabaseErrorHandler());
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createJobTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
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

        @SuppressWarnings("deprecation")
        private void upgradeFrom1To2(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_TRANSIENT_OLD + " integer;");
        }

        private void upgradeFrom2To3(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_FLEX_MS + " integer;");
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_FLEX_SUPPORT + " integer;");

            // adjust interval to minimum value if necessary
            ContentValues contentValues = new ContentValues();
            contentValues.put(COLUMN_INTERVAL_MS, JobRequest.MIN_INTERVAL);
            db.update(JOB_TABLE_NAME, contentValues, COLUMN_INTERVAL_MS + ">0 AND " + COLUMN_INTERVAL_MS + "<" + JobRequest.MIN_INTERVAL, new String[0]);

            // copy interval into flex column, that's the default value and the flex support mode is not required
            db.execSQL("update " + JOB_TABLE_NAME + " set " + COLUMN_FLEX_MS + " = " + COLUMN_INTERVAL_MS + ";");
        }

        private void upgradeFrom3To4(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_LAST_RUN + " integer;");
        }

        @SuppressWarnings("deprecation")
        private void upgradeFrom4To5(SQLiteDatabase db) {
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
                        + COLUMN_TRANSIENT_OLD + ","
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

        private void upgradeFrom5To6(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_REQUIRES_BATTERY_NOT_LOW + " integer;");
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_REQUIRES_STORAGE_NOT_LOW + " integer;");
        }
    }

    private static void closeCursor(@Nullable Cursor cursor) {
        // cursor implements Closeable only with API 16 and above
        if (cursor != null) {
            try {
                cursor.close();
            } catch (Exception ignored) {
            }
        }
    }

    private static void closeDatabase(@Nullable SQLiteDatabase database) {
        // SQLiteDatabase doesn't implement Closable on some 4.0.3 devices, see #182
        if (database != null && JobConfig.isCloseDatabase()) {
            try {
                database.close();
            } catch (Exception ignored) {
            }
        }
    }
}
