/*
 * Copyright 2007-present Evernote Corporation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
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
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.text.TextUtils;
import android.util.LruCache;

import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.CatLog;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author rwondratschek
 */
@SuppressWarnings("WeakerAccess")
/*package*/ class JobStorage {

    private static final CatLog CAT = new JobCat("JobStorage");

    private static final String FAILED_DELETE_IDS = "FAILED_DELETE_IDS";

    public static final String PREF_FILE_NAME = "evernote_jobs";
    public static final String DATABASE_NAME = PREF_FILE_NAME + ".db";
    public static final int DATABASE_VERSION = 4;

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
    public static final String COLUMN_PERSISTED = "persisted";
    public static final String COLUMN_NUM_FAILURES = "numFailures";
    public static final String COLUMN_SCHEDULED_AT = "scheduledAt";
    public static final String COLUMN_TRANSIENT = "isTransient";
    public static final String COLUMN_FLEX_MS = "flexMs";
    public static final String COLUMN_FLEX_SUPPORT = "flexSupport";
    public static final String COLUMN_LAST_RUN = "lastRun";

    private static final int CACHE_SIZE = 30;

    private static final String WHERE_NOT_TRANSIENT = "ifnull(" + COLUMN_TRANSIENT + ", 0)<=0";

    private final SharedPreferences mPreferences;
    private final JobCacheId mCacheId;

    private AtomicInteger mJobCounter;
    private final Set<String> mFailedDeleteIds;

    private final JobOpenHelper mDbHelper;
    private SQLiteDatabase mInjectedDatabase;

    public JobStorage(Context context) {
        this(context, DATABASE_NAME);
    }

    public JobStorage(Context context, String databasePath) {
        mPreferences = context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE);

        mCacheId = new JobCacheId();

        mDbHelper = new JobOpenHelper(context, databasePath);

        mFailedDeleteIds = mPreferences.getStringSet(FAILED_DELETE_IDS, new HashSet<String>());
        if (!mFailedDeleteIds.isEmpty()) {
            tryToCleanupFinishedJobs();
        }
    }

    public synchronized void put(final JobRequest request) {
        // don't write to db async, there could be a race condition with remove()
        store(request);

        // put in cache if store() was successful
        updateRequestInCache(request);
    }

    public synchronized void update(JobRequest request, ContentValues contentValues) {
        updateRequestInCache(request);
        SQLiteDatabase database = null;
        try {
            database = getDatabase();
            database.update(JOB_TABLE_NAME, contentValues, COLUMN_ID + "=?", new String[]{String.valueOf(request.getJobId())});
        } catch (Exception e) {
            // catch the exception here and keep what's in the database
            CAT.e(e, "could not update %s", request);
        } finally {
            closeDatabase(database);
        }
    }

    private void updateRequestInCache(JobRequest request) {
        mCacheId.put(request.getJobId(), request);
    }

    public synchronized JobRequest get(int id) {
        // not necessary to check if failed to delete, the cache is doing this
        return mCacheId.get(id);
    }

    public synchronized Set<JobRequest> getAllJobRequests(@Nullable String tag, boolean includeTransient) {
        Set<JobRequest> result = new HashSet<>();

        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            String where; // filter transient requests
            String[] args;
            if (TextUtils.isEmpty(tag)) {
                where = includeTransient ? null : WHERE_NOT_TRANSIENT;
                args = null;
            } else {
                where = includeTransient ? "" : (WHERE_NOT_TRANSIENT + " AND ");
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
        }

        return result;
    }

    public synchronized void remove(JobRequest request) {
        remove(request, request.getJobId());
    }

    private synchronized boolean remove(@Nullable JobRequest request, int jobId) {
        mCacheId.remove(jobId);
        SQLiteDatabase database = null;
        try {
            database = getDatabase();
            database.delete(JOB_TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(jobId)});
            return true;
        } catch (Exception e) {
            CAT.e(e, "could not delete %d %s", jobId, request);
            addFailedDeleteId(jobId);
            return false;
        } finally {
            closeDatabase(database);
        }
    }

    public synchronized int nextJobId() {
        if (mJobCounter == null) {
            mJobCounter = new AtomicInteger(getMaxJobId());
        }

        int id = mJobCounter.incrementAndGet();

        if (id < 0) {
            /*
             * An overflow occurred. It'll happen rarely, but just in case reset the ID and start from scratch.
             * Existing jobs will be treated as orphaned and will be overwritten.
             */
            id = 1;
            mJobCounter.set(id);
        }

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

    private JobRequest load(int id, boolean includeTransient) {
        if (didFailToDelete(id)) {
            return null;
        }

        SQLiteDatabase database = null;
        Cursor cursor = null;
        try {
            String where = COLUMN_ID + "=?";
            if (!includeTransient) {
                where += " AND " + COLUMN_TRANSIENT + "<=0";
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

        try {
            database = getDatabase();
            cursor = database.rawQuery("SELECT MAX(" + COLUMN_ID + ") FROM " + JOB_TABLE_NAME, null);
            if (cursor != null && cursor.moveToFirst()) {
                return cursor.getInt(0);
            } else {
                return 0;
            }
        } catch (Exception e) {
            CAT.e(e);
            return 0;

        } finally {
            closeCursor(cursor);
            closeDatabase(database);
        }
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
                        upgradeFrom3to4(db);
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
                    + COLUMN_PERSISTED + " integer, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer, "
                    + COLUMN_TRANSIENT + " integer, "
                    + COLUMN_FLEX_MS + " integer, "
                    + COLUMN_FLEX_SUPPORT + " integer, "
                    + COLUMN_LAST_RUN + " integer);");
        }

        private void upgradeFrom1To2(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_TRANSIENT + " integer;");
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

        private void upgradeFrom3to4(SQLiteDatabase db) {
            db.execSQL("alter table " + JOB_TABLE_NAME + " add column " + COLUMN_LAST_RUN + " integer;");
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
        if (database != null) {
            try {
                database.close();
            } catch (Exception ignored) {
            }
        }
    }
}
