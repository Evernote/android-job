package com.evernote.android.job;

import androidx.annotation.RestrictTo;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
/*package*/ final class WorkManagerAvailableHelper {

    private static final boolean MANAGER_IN_CLASSPATH;

    static {
        boolean managerInClasspath;
        try {
            Class.forName("androidx.work.WorkManager");
            managerInClasspath = true;
        } catch (Throwable t) {
            managerInClasspath = false;
        }
        MANAGER_IN_CLASSPATH = managerInClasspath;
    }

    public static boolean isWorkManagerApiSupported() {
        return MANAGER_IN_CLASSPATH;
    }

    private WorkManagerAvailableHelper() {
        // no op
    }
}
