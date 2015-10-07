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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Provides helper methods.
 *
 * @author rwondratschek
 */
public final class JobUtil {

    private static final SimpleDateFormat FORMAT = new SimpleDateFormat("HH:mm:ss", Locale.US);

    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);

    private JobUtil() {
        // no op
    }

    /**
     * @param timeMs The time which should be formatted in millie seconds.
     * @return The time in the format HH:mm:ss.
     */
    public static String timeToString(long timeMs) {
        FORMAT.setTimeZone(TimeZone.getTimeZone("GMT"));
        String result = FORMAT.format(new Date(timeMs));

        long days = timeMs / ONE_DAY;
        if (days == 1) {
            result += " (+1 day)";
        } else if (days > 1) {
            result += " (+" + days + " days)";
        }

        return result;
    }

    /**
     * @param context Any context.
     * @return Whether the package has the RECEIVE_BOOT_COMPLETED permission.
     */
    public static boolean hasBootPermission(Context context) {
        int result = context.getPackageManager()
                .checkPermission(Manifest.permission.RECEIVE_BOOT_COMPLETED, context.getPackageName());

        return result == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * @param context Any context.
     * @return Whether the package has the WAKE_LOCK permission.
     */
    public static boolean hasWakeLockPermission(Context context) {
        int result = context.getPackageManager()
                .checkPermission(Manifest.permission.WAKE_LOCK, context.getPackageName());

        return result == PackageManager.PERMISSION_GRANTED;
    }
}
