package com.evernote.android.job.util;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@FixMethodOrder(MethodSorters.JVM)
public class JobUtilTest {

    @Test
    public void verifyTimeToStringReturnsCorrectString() throws Exception {
        assertThat(JobUtil.timeToString(TimeUnit.SECONDS.toMillis(10))).isEqualTo("00:00:10");
        assertThat(JobUtil.timeToString(TimeUnit.MINUTES.toMillis(10))).isEqualTo("00:10:00");
        assertThat(JobUtil.timeToString(TimeUnit.MINUTES.toMillis(70))).isEqualTo("01:10:00");
        assertThat(JobUtil.timeToString(TimeUnit.HOURS.toMillis(26))).isEqualTo("02:00:00 (+1 day)");
        assertThat(JobUtil.timeToString(TimeUnit.DAYS.toMillis(26))).isEqualTo("00:00:00 (+26 days)");
    }
}
