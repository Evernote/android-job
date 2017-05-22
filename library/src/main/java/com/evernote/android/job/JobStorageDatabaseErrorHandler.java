/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job;

import android.annotation.TargetApi;
import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.os.Build;
import android.support.annotation.VisibleForTesting;
import android.util.Pair;

import com.evernote.android.job.util.JobCat;

import net.vrallev.android.cat.Cat;

import java.io.File;
import java.util.List;

/**
 * Default class used to define the action to take when database corruption is reported
 * by sqlite.
 * <p>
 * An application can specify an implementation of {@link DatabaseErrorHandler} on the
 * following:
 * <ul>
 * <li>{@link SQLiteDatabase#openOrCreateDatabase(String,
 * android.database.sqlite.SQLiteDatabase.CursorFactory, DatabaseErrorHandler)}</li>
 * <li>{@link SQLiteDatabase#openDatabase(String,
 * android.database.sqlite.SQLiteDatabase.CursorFactory, int, DatabaseErrorHandler)}</li>
 * </ul>
 * The specified {@link DatabaseErrorHandler} is used to handle database corruption errors, if they
 * occur.
 * <p>
 * If null is specified for the DatabaseErrorHandler param in the above calls, this class is used
 * as the default {@link DatabaseErrorHandler}.
 */
@SuppressWarnings("WeakerAccess")
/*package*/ final class JobStorageDatabaseErrorHandler implements DatabaseErrorHandler {

    private static final JobCat CAT = new JobCat("DatabaseErrorHandler");

    /**
     * defines the default method to be invoked when database corruption is detected.
     *
     * @param dbObj the {@link SQLiteDatabase} object representing the database on which corruption
     *              is detected.
     */
    public void onCorruption(SQLiteDatabase dbObj) {
        Cat.e("Corruption reported by sqlite on database: " + dbObj.getPath());

        // is the corruption detected even before database could be 'opened'?
        if (!dbObj.isOpen()) {
            // database files are not even openable. delete this database file.
            // NOTE if the database has attached databases, then any of them could be corrupt.
            // and not deleting all of them could cause corrupted database file to remain and
            // make the application crash on database open operation. To avoid this problem,
            // the application should provide its own {@link DatabaseErrorHandler} impl class
            // to delete ALL files of the database (including the attached databases).
            deleteDatabaseFile(dbObj.getPath());
            return;
        }

        List<Pair<String, String>> attachedDbs = null;
        try {
            // Close the database, which will cause subsequent operations to fail.
            // before that, get the attached database list first.
            try {
                attachedDbs = dbObj.getAttachedDbs();
            } catch (SQLiteException e) {
                /* ignore */
            }
            try {
                dbObj.close();
            } catch (SQLiteException e) {
                /* ignore */
            }
        } finally {
            // Delete all files of this corrupt database and/or attached databases
            if (attachedDbs != null) {
                for (Pair<String, String> p : attachedDbs) {
                    deleteDatabaseFile(p.second);
                }
            } else {
                // attachedDbs = null is possible when the database is so corrupt that even
                // "PRAGMA database_list;" also fails. delete the main database file
                deleteDatabaseFile(dbObj.getPath());
            }
        }
    }

    /*package*/ void deleteDatabaseFile(String fileName) {
        if (fileName.equalsIgnoreCase(":memory:") || fileName.trim().length() == 0) {
            return;
        }
        CAT.e("deleting the database file: " + fileName);
        try {
            File databaseFile = new File(fileName);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                deleteApi16(databaseFile);
            } else {
                deleteApi14(JobManager.instance().getContext(), databaseFile);
            }
        } catch (Exception e) {
            /* print warning and ignore exception */
            CAT.w(e, "delete failed: " + e.getMessage());
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    @VisibleForTesting
    /*package*/ void deleteApi16(File databaseFile) {
        SQLiteDatabase.deleteDatabase(databaseFile);
    }

    @VisibleForTesting
    /*package*/ void deleteApi14(Context context, File databaseFile) {
        context.deleteDatabase(databaseFile.getName());
    }
}
