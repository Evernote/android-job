package com.evernote.android.job;

import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.io.File;
import java.util.Set;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * Databases should be created with UnitTestDatabaseCreator.java and then be pulled from the device.
 * Best is to use an emulator with API 23.
 *
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class DatabaseExistingTest extends BaseJobManagerTest {

    @Test
    public void upgradeFromV1() {
        testDatabase("evernote_jobs_v1.db");
    }

    @Test
    public void upgradeFromV2() {
        testDatabase("evernote_jobs_v2.db");
    }

    @Test
    public void upgradeFromV3() {
        testDatabase("evernote_jobs_v3.db");
    }

    @Test
    public void upgradeFromV4() {
        testDatabase("evernote_jobs_v4.db");
    }

    @Test
    public void upgradeFromV5() {
        testDatabase("evernote_jobs_v5.db");
    }

    @Test
    public void upgradeFromV6() {
        testDatabase("evernote_jobs_v6.db");
    }

    private void testDatabase(String name) {
        String filePath = getClass().getResource("/databases/" + name).getPath();
        assertThat(new File(filePath).exists()).isTrue();

        JobStorage storage = new JobStorage(context(), filePath);

        Set<JobRequest> requests = storage.getAllJobRequests("tag", true);
        assertThat(requests).hasSize(30);

        int exact = 0;
        int oneOff = 0;
        int periodic = 0;

        for (JobRequest request : requests) {
            if (request.isExact()) {
                exact++;
            } else if (request.isPeriodic()) {
                periodic++;
            } else {
                oneOff++;
            }
        }

        assertThat(exact).isEqualTo(10);
        assertThat(oneOff).isEqualTo(10);
        assertThat(periodic).isEqualTo(10);

        // none of them should be started
        for (JobRequest request : requests) {
            assertThat(request.isStarted()).isFalse();
        }

        for (JobRequest request : requests) {
            if (!request.isPeriodic()) {
                continue;
            }

            assertThat(request.getIntervalMs()).isGreaterThanOrEqualTo(JobRequest.MIN_INTERVAL);
            assertThat(request.getFlexMs()).isGreaterThanOrEqualTo(JobRequest.MIN_FLEX);
            assertThat(request.getLastRun()).isEqualTo(0);
        }
    }
}
