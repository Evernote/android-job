package com.evernote.android.job;

import androidx.annotation.NonNull;

import com.evernote.android.job.util.JobLogger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.assertj.core.api.Java6Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;

@RunWith(MockitoJUnitRunner.class)
public class JobCreatorHolderTest {
    @Mock JobLogger jobLogger;
    @Mock JobCreator mockJobCreator;

    private JobCreatorHolder holder;

    @Before
    public void setup() {
        JobConfig.addLogger(jobLogger);
        holder = new JobCreatorHolder();
    }

    @After
    public void tearDown() {
        JobConfig.reset();
    }

    @Test
    public void createJobLogsWarningWhenNoCreatorsAreAdded() {
        holder.createJob("DOES_NOT_EXIST");

        verify(jobLogger).log(
                anyInt(),                  // priority
                eq("JobCreatorHolder"),    // tag
                eq("no JobCreator added"), // message
                ArgumentMatchers.<Throwable>isNull());
    }

    @Test
    public void createJobLogsNothingWhenAtLeastOneCreatorIsAdded() {
        holder.addJobCreator(mockJobCreator);

        holder.createJob("DOES_NOT_EXIST");

        verifyZeroInteractions(jobLogger);
    }

    @Test
    public void createJobSucceedsWhenCreatorListIsModifiedConcurrently() {
        // This test verifies that modifying the list of job-creators while
        // another thread is in the middle of JobCreatorHolder#createJob(String)
        // is safe, in that createJob will finish unexceptionally.
        //
        // We'll test thread-safety by beginning iteration through the
        // job-creator list, then adding another creator while the iterator
        // is active.  If we are thread-safe, then iteration will complete
        // without an exception.
        //
        // To coordinate this, we'll need a custom job creator that blocks
        // until it receives a signal to continue.  A "reader" thread will
        // invoke "createJob", iterating over the list, and blocking. While
        // the reader is blocked, a "mutator" thread will modify the creator
        // list, then signal the reader thread to resume.  Any
        // ConcurrentModificationException will be caught and stored.  When
        // both threads are finished, we can verify that no error was thrown.

        final Lock lock = new ReentrantLock();
        final Condition listModified = lock.newCondition();
        final Condition iterationStarted = lock.newCondition();
        final AtomicReference<Throwable> error = new AtomicReference<>();

        final AtomicBoolean isIteratorActive = new AtomicBoolean(false);

        class BlockingJobCreator implements JobCreator {
            @Override
            public Job create(@NonNull String tag) {
                lock.lock();
                try {
                    isIteratorActive.set(true);
                    iterationStarted.signal();

                    listModified.awaitUninterruptibly();
                } finally {
                    lock.unlock();
                }

                return null;
            }
        }

        class Mutator extends Thread {
            @Override
            public void run() {
                waitUntilIterationStarted();

                holder.addJobCreator(mockJobCreator);

                signalListModified();
            }

            private void waitUntilIterationStarted() {
                lock.lock();
                try {
                    if (!isIteratorActive.get()) {
                        iterationStarted.awaitUninterruptibly();
                    }
                } finally {
                    lock.unlock();
                }
            }

            private void signalListModified() {
                lock.lock();
                try {
                    listModified.signal();
                } finally {
                    lock.unlock();
                }
            }
        }

        class Reader extends Thread {
            @Override
            public void run() {
                try {
                    holder.createJob("SOME_JOB_TAG");
                } catch (Throwable t) {
                    error.set(t);
                }
            }
        }

        holder.addJobCreator(new BlockingJobCreator());

        Mutator mutator = new Mutator();
        Reader reader = new Reader();

        reader.start();
        mutator.start();

        join(mutator);
        join(reader);

        assertThat(error.get()).isNull();
    }

    private static void join(Thread thread) {
        try {
            thread.join();
        } catch (InterruptedException ignored) {
        }
    }
}
