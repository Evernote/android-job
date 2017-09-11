package com.evernote.android.job;

public class JobIds {

    // close to Integer.MAX_VALUE; jobIds in this range are reserved for internal purposes
    public static final int RESERVED_JOB_ID_RANGE_START = 2147480000;

    // fixed and unique jobIds for internal jobs
    public static final int JOB_ID_JOB_RESCHEDULE_SERVICE = 2147480000;
    public static final int JOB_ID_PLATFORM_ALARM_SERVICE = 2147480001;

    private JobIds() {
        // do not instantiate; holder for constants
    }

}
