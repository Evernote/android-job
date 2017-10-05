package com.evernote.android.job;

import android.content.ContentValues;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;

import com.evernote.android.job.test.DummyJobs;
import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class DatabaseFailureTest extends BaseJobManagerTest {

    @Test(expected = SQLException.class)
    public void testInsertFails() {
        SQLiteDatabase database = mock(SQLiteDatabase.class);
        when(database.insert(anyString(), nullable(String.class), any(ContentValues.class))).thenReturn(-1L);
        when(database.insertWithOnConflict(anyString(), nullable(String.class), any(ContentValues.class), anyInt())).thenThrow(SQLException.class);

        manager().getJobStorage().injectDatabase(database);

        DummyJobs.createOneOff().schedule();
    }

    @Test
    public void testUpdateDoesNotCrash() {
        JobRequest request = DummyJobs.createOneOff();
        int jobId = request.schedule();

        assertThat(request.getScheduledAt()).isGreaterThan(0L);
        assertThat(request.getFailureCount()).isEqualTo(0);
        assertThat(request.getLastRun()).isEqualTo(0);

        SQLiteDatabase database = mock(SQLiteDatabase.class);
        when(database.update(anyString(), any(ContentValues.class), nullable(String.class), any(String[].class))).thenThrow(SQLException.class);

        manager().getJobStorage().injectDatabase(database);

        request.updateStats(true, true); // updates the database value, but fails in this case
        assertThat(request.getFailureCount()).isEqualTo(1); // in memory value was updated, keep that
        assertThat(request.getLastRun()).isGreaterThan(0);

        // kinda hacky, this removes the request from the cache, but doesn't delete it in the database,
        // because we're using the mock at the moment
        manager().getJobStorage().remove(request);

        manager().getJobStorage().injectDatabase(null); // reset

        request = manager().getJobRequest(jobId);
        assertThat(request.getFailureCount()).isEqualTo(0);
        assertThat(request.getLastRun()).isEqualTo(0);
    }

    @Test
    public void testDeleteFailsAfterExecution() throws Exception {
        verifyDeleteOperationFailsAndGetsCleanedUp(new DeleteOperation() {
            @Override
            public void delete(JobRequest request) {
                executeJob(request.getJobId(), Job.Result.SUCCESS);
            }
        });
    }

    @Test
    public void testDeleteFailsAfterCancel() throws Exception {
        verifyDeleteOperationFailsAndGetsCleanedUp(new DeleteOperation() {
            @Override
            public void delete(JobRequest request) {
                manager().cancel(request.getJobId());
            }
        });
    }

    private interface DeleteOperation {
        void delete(JobRequest request);
    }

    private void verifyDeleteOperationFailsAndGetsCleanedUp(DeleteOperation deleteOperation) throws Exception {
        JobRequest request = DummyJobs.createOneOff();
        int jobId = request.schedule();

        SQLiteDatabase database = mock(SQLiteDatabase.class);
        when(database.delete(anyString(), anyString(), any(String[].class))).thenThrow(SQLException.class);

        manager().getJobStorage().injectDatabase(database);

        // that should delete the job, but this operation fails with the mock database
        deleteOperation.delete(request);

        manager().getJobStorage().injectDatabase(null); // restore

        // shouldn't be available anymore
        assertThat(manager().getJobRequest(jobId)).isNull();
        assertThat(manager().getJobStorage().getFailedDeleteIds()).containsExactly(String.valueOf(jobId));

        // initialize the job storage again and clean up the old finished job
        manager().destroy();
        JobManager manager = createManager();

        assertThat(manager.getJobRequest(jobId)).isNull();

        // clean up happens asynchronously, so wait for it
        final CountDownLatch latch = new CountDownLatch(1);
        new Thread() {
            @Override
            public void run() {
                long start = System.currentTimeMillis();
                while (System.currentTimeMillis() - start < 3_000) {
                    if (manager().getJobStorage().getFailedDeleteIds().isEmpty()) {
                        latch.countDown();
                        return;
                    }
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException ignored) {
                    }
                }
            }
        }.start();
        latch.await(3, TimeUnit.SECONDS);

        assertThat(manager.getJobStorage().getFailedDeleteIds()).isEmpty();
    }
}
