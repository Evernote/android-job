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
package com.evernote.android.job.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import java.util.Arrays;

/**
 * The default {@link JobLogger} class for this library.
 *
 * @author rwondratschek
 */
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
