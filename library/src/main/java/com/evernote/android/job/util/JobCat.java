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

import net.vrallev.android.cat.CatLog;
import net.vrallev.android.cat.instance.CatLazy;

import java.util.Arrays;

/**
 * The default {@link CatLog} class for this library.
 *
 * @author rwondratschek
 */
public class JobCat extends CatLazy {

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

    private final String mTag;

    public JobCat() {
        this((String) null);
    }

    public JobCat(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    public JobCat(String tag) {
        mTag = tag;
    }

    @Override
    public String getTag() {
        return mTag == null ? super.getTag() : mTag;
    }

    @Override
    protected void println(int priority, String message, Throwable t) {
        if (logcatEnabled) {
            super.println(priority, message, t);
        }

        JobLogger[] printers = JobCat.loggers;
        if (printers.length > 0) {
            String tag = getTag();

            for (JobLogger logger : printers) {
                if (logger != null) {
                    logger.log(priority, tag, message, t);
                }
            }
        }
    }
}
