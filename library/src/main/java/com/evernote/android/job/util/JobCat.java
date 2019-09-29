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
package com.evernote.android.job.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import android.util.Log;

import java.util.Arrays;

/**
 * The default {@link JobLogger} class for this library.
 *
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class JobCat implements JobLogger {

    private static volatile JobLogger[] loggers = new JobLogger[0]; // use array to avoid synchronization while printing log statements
    private static volatile boolean logcatEnabled = true;

    /**
     * Add a global logger for the job library, which will be notified about each log statement.
     *
     * @param logger Your desired logger.
     * @return {@code true} if the logger was added. Returns {@code false} if the logger was
     * already added.
     */
    public static synchronized boolean addLogger(@NonNull JobLogger logger) {
        for (JobLogger printer1 : loggers) {
            if (logger.equals(printer1)) {
                return false;
            }
        }

        for (int i = 0; i < loggers.length; i++) {
            if (loggers[i] == null) {
                loggers[i] = logger;
                return true;
            }
        }

        int index = loggers.length;
        loggers = Arrays.copyOf(loggers, loggers.length + 2);
        loggers[index] = logger;
        return true;
    }

    /**
     * Remove a global logger.
     *
     * @param logger Your desired logger.
     * @see #addLogger(JobLogger)
     */
    public static synchronized void removeLogger(@NonNull JobLogger logger) {
        for (int i = 0; i < loggers.length; i++) {
            if (logger.equals(loggers[i])) {
                loggers[i] = null;
                // continue, maybe for some reason the logger is twice in the array
            }
        }
    }

    public static synchronized void clearLogger() {
        Arrays.fill(loggers, null);
    }

    /**
     * Global switch to enable or disable printing log messages to Logcat.
     *
     * @param enabled Whether or not to print all log messages. The default value is {@code true}.
     */
    public static void setLogcatEnabled(boolean enabled) {
        JobCat.logcatEnabled = enabled;
    }

    /**
     * @return Whether logging is enabled for this library. The default value is {@code true}.
     */
    public static boolean isLogcatEnabled() {
        return logcatEnabled;
    }

    protected final String mTag;
    protected final boolean mEnabled;

    public JobCat(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    public JobCat(String tag) {
        this(tag, true);
    }

    public JobCat(String tag, boolean enabled) {
        mTag = tag;
        mEnabled = enabled;
    }

    public void i(@NonNull String message) {
        log(Log.INFO, mTag, message, null);
    }

    public void i(@NonNull String message, Object... args) {
        log(Log.INFO, mTag, String.format(message, args), null);
    }

    public void d(@NonNull String message) {
        log(Log.DEBUG, mTag, message, null);
    }

    public void d(@NonNull String message, Object... args) {
        log(Log.DEBUG, mTag, String.format(message, args), null);
    }

    public void d(@NonNull Throwable t, String message, Object... args) {
        log(Log.DEBUG, mTag, String.format(message, args), t);
    }

    public void w(@NonNull String message) {
        log(Log.WARN, mTag, message, null);
    }

    public void w(@NonNull String message, Object... args) {
        log(Log.WARN, mTag, String.format(message, args), null);
    }

    public void w(@NonNull Throwable t, @NonNull String message, Object... args) {
        log(Log.WARN, mTag, String.format(message, args), t);
    }

    public void e(@NonNull Throwable t) {
        String message = t.getMessage();
        log(Log.ERROR, mTag, message == null ? "empty message" : message, t);
    }

    public void e(@NonNull String message) {
        log(Log.ERROR, mTag, message, null);
    }

    public void e(@NonNull String message, Object... args) {
        log(Log.ERROR, mTag, String.format(message, args), null);
    }

    public void e(@NonNull Throwable t, @NonNull String message, Object... args) {
        log(Log.ERROR, mTag, String.format(message, args), t);
    }

    @Override
    public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
        if (!mEnabled) {
            return;
        }

        if (logcatEnabled) {
            String stacktrace = t == null ? "" : ('\n' + Log.getStackTraceString(t));
            Log.println(priority, tag, message + stacktrace);
        }

        JobLogger[] printers = JobCat.loggers;
        if (printers.length > 0) {
            for (JobLogger logger : printers) {
                if (logger != null) {
                    logger.log(priority, tag, message, t);
                }
            }
        }
    }
}
