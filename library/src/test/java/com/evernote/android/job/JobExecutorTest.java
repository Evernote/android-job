package com.evernote.android.job;

import android.util.LruCache;

import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.lang.ref.WeakReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@FixMethodOrder(MethodSorters.JVM)
@RunWith(JobRobolectricTestRunner.class)
public class JobExecutorTest {

    @Test
    public void verifyGetAllJobResultsReturnsAllResults() {
        JobExecutor executor = new JobExecutor();

        executor.markJobAsFinished(createJobMock(1));
        executor.markJobAsFinished(createJobMock(2));

        assertThat(executor.getAllJobs()).hasSize(2);
        assertThat(executor.getAllJobResults().size()).isEqualTo(2);
    }

    @Test
    public void verifyCleanUpRoutine() {
        LruCache<Integer, WeakReference<Job>> cache = new LruCache<>(20);
        cache.put(1, new WeakReference<>(createJobMock(1)));
        cache.put(2, new WeakReference<Job>(null));

        new JobExecutor().cleanUpRoutine(cache);

        assertThat(cache.size()).isEqualTo(1);
    }

    private Job createJobMock(int id) {
        Job.Params params = mock(Job.Params.class);
        when(params.getId()).thenReturn(id);

        Job job = mock(Job.class);
        when(job.getParams()).thenReturn(params);
        return job;
    }
}
