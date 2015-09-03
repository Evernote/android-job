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

import android.content.ContentValues;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.v4.util.LruCache;
import android.text.TextUtils;

import net.vrallev.android.cat.Cat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author rwondratschek
 */
/*package*/ class JobStorage {

//    private static final String JOB_PREFIX = "JOB_";
    private static final String JOB_ID_COUNTER = "JOB_ID_COUNTER";

    private static final String DATABASE_NAME = "evernote_jobs.db";
    private static final int DATABASE_VERSION = 1;

    private static final String JOB_TABLE_NAME = "jobs";

    public static final String COLUMN_ID = "_id";
    public static final String COLUMN_JOB_CLASS = "jobClass";
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
    public static final String COLUMN_TAG = "tag";
    public static final String COLUMN_NUM_FAILURES = "numFailures";
    public static final String COLUMN_SCHEDULED_AT = "scheduledAt";

    private static final int CACHE_SIZE = 30;

    private final SharedPreferences mPreferences;
    private final JobCacheId mCacheId;
    private final JobCacheTag mCacheTag;
    private final ExecutorService mExecutorService;

    private final AtomicInteger mJobCounter;

    private final JobOpenHelper mDbHelper;

    public JobStorage(Context context) {
        mPreferences = context.getSharedPreferences("jobs", Context.MODE_PRIVATE);
        mExecutorService = Executors.newSingleThreadExecutor();
        mCacheId = new JobCacheId();
        mCacheTag = new JobCacheTag();

        int lastJobId = mPreferences.getInt(JOB_ID_COUNTER, 0);
        mJobCounter = new AtomicInteger(lastJobId);

        mDbHelper = new JobOpenHelper(context);
    }

    public synchronized void put(final JobRequest request) {
        mCacheId.put(request.getJobId(), request);
        if (!TextUtils.isEmpty(request.getTag())) {
            mCacheTag.put(request.getTag(), request);
        }

        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                store(request);
            }
        });
    }

    public synchronized JobRequest get(int id) {
        return mCacheId.get(id);
    }

    public synchronized JobRequest get(String tag) {
        if (tag == null) {
            return null;
        }
        return mCacheTag.get(tag);
    }

    public synchronized Set<JobRequest> getAllJobRequests() {
        Set<JobRequest> result = new HashSet<>();

        Cursor cursor = null;
        try {
            cursor = mDbHelper.getWritableDatabase().query(JOB_TABLE_NAME, null, null, null, null, null, null);
            HashMap<Integer, JobRequest> cachedRequests = new HashMap<>(mCacheId.snapshot());

            while (cursor.moveToNext()) {
                // check in cache first, can avoid creating many JobRequest objects
                Integer id = cursor.getInt(cursor.getColumnIndex(COLUMN_ID));
                if (cachedRequests.containsKey(id)) {
                    result.add(cachedRequests.get(id));
                } else {
                    result.add(JobRequest.fromCursor(cursor));
                }
            }
        } catch (Exception e) {
            Cat.e(e, "could not load all jobs");

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return result;
    }

    public synchronized void remove(JobRequest request) {
        mCacheId.remove(request.getJobId());
        if (!TextUtils.isEmpty(request.getTag())) {
            mCacheTag.remove(request.getTag());
        }
        try {
            mDbHelper.getWritableDatabase().delete(JOB_TABLE_NAME, COLUMN_ID + "=?", new String[]{String.valueOf(request.getJobId())});
        } catch (Exception e) {
            Cat.e(e, "could not delete %s", request);
        }
    }

    public synchronized int nextJobId() {
        int id = mJobCounter.incrementAndGet();
        mPreferences.edit()
                .putInt(JOB_ID_COUNTER, id)
                .apply();

        return id;
    }

    private void store(JobRequest request) {
        try {
            ContentValues contentValues = request.toContentValues();
            mDbHelper.getWritableDatabase().insert(JOB_TABLE_NAME, null, contentValues);
        } catch (Exception e) {
            Cat.e(e, "could not store %s", request);
        }
    }

    private JobRequest load(int id) {
        Cursor cursor = null;
        try {
            cursor = mDbHelper.getWritableDatabase().query(JOB_TABLE_NAME, null, COLUMN_ID + "=?", new String[]{String.valueOf(id)}, null, null, null);
            if (cursor.moveToFirst()) {
                return JobRequest.fromCursor(cursor);
            }

        } catch (Exception e) {
            Cat.e(e, "could not load id %d", id);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private JobRequest load(String tag) {
        Cursor cursor = null;
        try {
            cursor = mDbHelper.getWritableDatabase().query(JOB_TABLE_NAME, null, COLUMN_TAG + "=?", new String[]{tag}, null, null, null);
            if (cursor.moveToFirst()) {
                return JobRequest.fromCursor(cursor);
            }

        } catch (Exception e) {
            Cat.e(e, "could not load tag %s", tag);

        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return null;
    }

    private class JobCacheId extends LruCache<Integer, JobRequest> {

        public JobCacheId() {
            super(CACHE_SIZE);
        }

        @Override
        protected JobRequest create(Integer id) {
            return load(id);
        }
    }

    private class JobCacheTag extends LruCache<String, JobRequest> {

        public JobCacheTag() {
            super(CACHE_SIZE);
        }

        @Override
        protected JobRequest create(String tag) {
            return load(tag);
        }
    }

    private class JobOpenHelper extends SQLiteOpenHelper {

        public JobOpenHelper(Context context) {
            super(context, DATABASE_NAME, null, DATABASE_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            createJobTable(db);
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            // not needed at the moment
        }

        private void createJobTable(SQLiteDatabase db) {
            db.execSQL("create table " + JOB_TABLE_NAME + " ("
                    + COLUMN_ID + " integer primary key, "
                    + COLUMN_JOB_CLASS + " text not null, "
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
                    + COLUMN_TAG + " text, "
                    + COLUMN_NUM_FAILURES + " integer, "
                    + COLUMN_SCHEDULED_AT + " integer);");
        }
    }
}
