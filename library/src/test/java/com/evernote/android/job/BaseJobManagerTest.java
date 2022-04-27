package com.evernote.android.job;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import androidx.annotation.NonNull;
import android.test.mock.MockContext;
import androidx.test.core.app.ApplicationProvider;
import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.TestLogger;
import java.util.Collections;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.Rule;
import org.robolectric.RuntimeEnvironment;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@SuppressWarnings("WeakerAccess")
public abstract class BaseJobManagerTest {

    @Rule
    public final JobManagerRule mJobManagerRule;

    private final Context mContext;

    public BaseJobManagerTest() {
        Context mockContext = createMockContext();
        mContext = mockContext.getApplicationContext();

        mJobManagerRule = new JobManagerRule(provideJobCreator(), mockContext);
    }

    @NonNull
    protected JobCreator provideJobCreator() {
        return new DummyJobs.SpyableJobCreator(DummyJobs.TEST_JOB_CREATOR);
    }

    @NonNull
    protected final JobManager manager() {
        return mJobManagerRule.getJobManager();
    }

    @NonNull
    protected final Context context() {
        return mContext;
    }

    protected final JobManager createManager() {
        Context mockContext = mock(MockContext.class);
        when(mockContext.getApplicationContext()).thenReturn(mContext);
        return JobManager.create(mockContext);
    }

    protected void executeJob(int jobId, @NonNull Job.Result expected) {
        try {
            executeJobAsync(jobId, expected).get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e.getMessage(), e);
        }
    }

    protected Future<Job.Result> executeJobAsync(int jobId, @NonNull final Job.Result expected) throws InterruptedException {
        final JobProxy.Common common = new JobProxy.Common(context(), TestLogger.INSTANCE, jobId);

        final JobRequest pendingRequest = common.getPendingRequest(true, true);
        assertThat(pendingRequest).isNotNull();

        final CountDownLatch latch = new CountDownLatch(1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Job.Result> future = executor.submit(new Callable<Job.Result>() {
            @Override
            public Job.Result call() throws Exception {
                latch.countDown();

                Job.Result result = common.executeJobRequest(pendingRequest, null);
                assertThat(result).isEqualTo(expected);
                assertThat(common.getPendingRequest(true, false)).isNull();

                return result;
            }
        });

        // wait until the thread actually started
        assertThat(latch.await(3, TimeUnit.SECONDS)).isTrue();

        executor.shutdown();
        return future;
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    protected void resetJob(int jobId) {
        Job job = manager().getJob(jobId);
        if (job != null) {
            job.mFinishedTimeStamp = -1L;
        }
    }

    /**
     * @return A mocked context which returns a spy of {@link RuntimeEnvironment#application} in
     * {@link Context#getApplicationContext()}.
     */
    public static Context createMockContext() {
        // otherwise the JobScheduler isn't supported we check if the service is enabled
        // Robolectric doesn't parse services from the manifest, see https://github.com/robolectric/robolectric/issues/416
        PackageManager packageManager = mock(PackageManager.class);
        when(packageManager.queryBroadcastReceivers(any(Intent.class), anyInt())).thenReturn(Collections.singletonList(new ResolveInfo()));

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.serviceInfo = new ServiceInfo();
        resolveInfo.serviceInfo.permission = "android.permission.BIND_JOB_SERVICE";
        when(packageManager.queryIntentServices(any(Intent.class), anyInt())).thenReturn(Collections.singletonList(resolveInfo));

        Context context = spy(ApplicationProvider.getApplicationContext());
        when(context.getPackageManager()).thenReturn(packageManager);
        when(context.getApplicationContext()).thenReturn(context);

        Context mockContext = mock(MockContext.class);
        when(mockContext.getApplicationContext()).thenReturn(context);
        return mockContext;
    }
}
