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

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.annotation.RestrictTo;

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
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class JobUtil {

    private static final ThreadLocal<SimpleDateFormat> FORMAT = new ThreadLocal<>();

    private static final long ONE_DAY = TimeUnit.DAYS.toMillis(1);

    private static final JobCat CAT = new JobCat("JobUtil");

    private JobUtil() {
        // no op
    }

    /**
     * @param timeMs The time which should be formatted in millie seconds.
     * @return The time in the format HH:mm:ss.
     */
    public static String timeToString(long timeMs) {
        SimpleDateFormat simpleDateFormat = FORMAT.get();
        if (simpleDateFormat == null) {
            simpleDateFormat = new SimpleDateFormat("HH:mm:ss", Locale.US);
            FORMAT.set(simpleDateFormat);
        }

        simpleDateFormat.setTimeZone(TimeZone.getTimeZone("GMT"));
        String result = simpleDateFormat.format(new Date(timeMs));

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
        return hasPermission(context, Manifest.permission.RECEIVE_BOOT_COMPLETED, 0);
    }

    /**
     * @param context Any context.
     * @return Whether the package has the WAKE_LOCK permission.
     */
    public static boolean hasWakeLockPermission(Context context) {
        return hasPermission(context, Manifest.permission.WAKE_LOCK, 0);
    }

    private static boolean hasPermission(Context context, String permission, int repeatCount) {
        try {
            return PackageManager.PERMISSION_GRANTED == context.getPackageManager()
                    .checkPermission(permission, context.getPackageName());
        } catch (Exception e) {
            CAT.e(e);
            // crash https://gist.github.com/vRallev/6affe17c93e993681bfd

            // give it another chance with the application context
            return repeatCount < 1 && hasPermission(context.getApplicationContext(), permission, repeatCount + 1);
        }
    }
}
