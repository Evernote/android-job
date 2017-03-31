package com.evernote.android.job.test;

import android.support.annotation.NonNull;

import com.evernote.android.job.Job;
import com.evernote.android.job.JobCreator;
import com.evernote.android.job.JobRequest;

import static org.mockito.Mockito.spy;

/**
 * @author rwondratschek
 */
public final class DummyJobs {

    private DummyJobs() {
        throw new UnsupportedOperationException();
    }

    public static final class SuccessJob extends Job {

        public static final String TAG = "SuccessJob";

        @NonNull
        @Override
        protected Result onRunJob(Params params) {
            return Result.SUCCESS;
        }
    }

    public static final class RescheduleJob extends Job {

        public static final String TAG = "RescheduleJob";

        private int mNewJobId;

        @NonNull
        @Override
        protected Result onRunJob(Params params) {
            return Result.RESCHEDULE;
        }

        @Override
        protected void onReschedule(int newJobId) {
            mNewJobId = newJobId;
        }

        public int getNewJobId() {
            return mNewJobId;
        }
    }

    public static final class FailureJob extends Job {

        public static final String TAG = "FailureJob";

        @NonNull
        @Override
        protected Result onRunJob(Params params) {
            return Result.FAILURE;
        }
    }

    public static final JobCreator TEST_JOB_CREATOR = new JobCreator() {
        @Override
        public Job create(String tag) {
            switch (tag) {
                case SuccessJob.TAG:
                    return new SuccessJob();
                case RescheduleJob.TAG:
                    return new RescheduleJob();
                case FailureJob.TAG:
                    return new FailureJob();
                default:
                    return null;
            }
        }
    };

    public static final class SpyableJobCreator implements JobCreator {

        private final JobCreator mJobCreator;

        public SpyableJobCreator(JobCreator jobCreator) {
            mJobCreator = jobCreator;
        }

        @Override
        public Job create(String tag) {
            Job job = mJobCreator.create(tag);
            return job == null ? null : spy(job);
        }
    }

    public static JobRequest.Builder createBuilder(Class<? extends Job> jobClass) {
        try {
            String tag = (String) jobClass.getDeclaredField("TAG").get(null);
            return new JobRequest.Builder(tag);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
