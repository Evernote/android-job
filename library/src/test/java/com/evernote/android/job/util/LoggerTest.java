package com.evernote.android.job.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.evernote.android.job.JobConfig;

import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@FixMethodOrder(MethodSorters.JVM)
public class LoggerTest {

    private boolean mResetValueCalled;

    @After
    public void resetValue() {
        JobCat.setLogcatEnabled(true);
        mResetValueCalled = true;
    }

    @Test
    public void testIsLogcatEnabled() {
        // first test in class, so resetValue() hasn't been called, yet
        assertThat(mResetValueCalled).isFalse();
        assertThat(JobCat.isLogcatEnabled()).isTrue();

        JobCat.setLogcatEnabled(false);
        assertThat(JobCat.isLogcatEnabled()).isFalse();
    }

    @Test
    public void testAddIsIdempotent() {
        TestLogger printer = new TestLogger();
        assertThat(JobConfig.addLogger(printer)).isTrue();
        assertThat(JobConfig.addLogger(printer)).isFalse();
    }

    @Test
    public void testRemove() {
        TestLogger printer = new TestLogger();
        assertThat(JobConfig.addLogger(printer)).isTrue();
        JobConfig.removeLogger(printer);
        assertThat(JobConfig.addLogger(printer)).isTrue();
    }

    @Test
    public void testSingleCustomLoggerAddBefore() {
        TestLogger printer = new TestLogger();
        assertThat(JobConfig.addLogger(printer)).isTrue();

        JobCat cat = new JobCat("Tag");
        cat.d("hello");
        cat.w("world");

        assertThat(printer.mMessages).contains("hello", "world");
    }

    @Test
    public void testSingleCustomLoggerAddAfter() {
        JobCat cat = new JobCat("Tag");

        TestLogger printer = new TestLogger();
        assertThat(JobConfig.addLogger(printer)).isTrue();

        cat.d("hello");
        cat.w("world");

        assertThat(printer.mMessages).containsExactly("hello", "world");
    }

    @Test
    public void test100Loggers() {
        JobCat cat1 = new JobCat("Tag1");

        List<TestLogger> printers = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            TestLogger printer = new TestLogger();
            assertThat(JobConfig.addLogger(printer)).isTrue();
            printers.add(printer);
        }

        JobCat cat2 = new JobCat("Tag2");

        cat1.d("hello");
        cat2.w("world");

        for (TestLogger printer : printers) {
            assertThat(printer.mTags).containsExactly("Tag1", "Tag2");
            assertThat(printer.mMessages).containsExactly("hello", "world");
        }

        TestLogger removedPrinter = printers.remove(50);
        JobConfig.removeLogger(removedPrinter);

        cat1.d("third");
        for (TestLogger printer : printers) {
            assertThat(printer.mTags).containsExactly("Tag1", "Tag2", "Tag1");
            assertThat(printer.mMessages).containsExactly("hello", "world", "third");
        }
        assertThat(removedPrinter.mTags).containsExactly("Tag1", "Tag2");
        assertThat(removedPrinter.mMessages).containsExactly("hello", "world");
    }

    private static final class TestLogger implements JobLogger {

        private final List<String> mTags = new ArrayList<>();
        private final List<String> mMessages = new ArrayList<>();

        @Override
        public void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t) {
            mTags.add(tag);
            mMessages.add(message);
        }
    }
}
