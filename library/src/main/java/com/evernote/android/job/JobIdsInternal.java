package com.evernote.android.job;

/**
 * Constants for jobIds reserved for internal jobs.
 *
 * There are some internal jobs needing fixed platform jobIds, which should not conflict with those chosen dynamically
 * for jobs scheduled with the {@link JobManager}.
 */
public final class JobIdsInternal {

    /**
     * JobIds between this and Integer.MAX_VALUE are reserved for internal purposes.
     */
    // close to Integer.MAX_VALUE
    public static final int RESERVED_JOB_ID_RANGE_START = 2147480000;

    // fixed and unique jobIds for internal jobs
    public static final int JOB_ID_JOB_RESCHEDULE_SERVICE = 2147480000;
    public static final int JOB_ID_PLATFORM_ALARM_SERVICE = 2147480001;

    private JobIdsInternal() {
        // do not instantiate; holder for constants
    }

}
