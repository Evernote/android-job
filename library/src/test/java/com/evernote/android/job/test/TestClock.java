package com.evernote.android.job.test;

import com.evernote.android.job.util.Clock;
import com.evernote.android.job.util.JobPreconditions;

import java.util.Calendar;

/**
 * @author rwondratschek
 */
public class TestClock implements Clock {

    private long mTime;

    @Override
    public long currentTimeMillis() {
        return mTime;
    }

    @Override
    public long elapsedRealtime() {
        return mTime;
    }

    public void setTime(long time) {
        mTime = time;
    }

    public void setTime(int hour, int minute) {
        JobPreconditions.checkArgumentInRange(hour, 0, 23, "hour");
        JobPreconditions.checkArgumentInRange(minute, 0, 59, "minute");

        Calendar calendar = Calendar.getInstance();
        calendar.set(Calendar.HOUR_OF_DAY, hour);
        calendar.set(Calendar.MINUTE, minute);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        setTime(calendar.getTimeInMillis());
    }
}
