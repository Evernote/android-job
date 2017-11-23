package com.evernote.android.job;

import com.evernote.android.job.util.JobCat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author rwondratschek
 */
/*package*/ class JobCreatorHolder {

    private static final JobCat CAT = new JobCat("JobCreatorHolder");

    private final List<JobCreator> mJobCreators;

    public JobCreatorHolder() {
        mJobCreators = new CopyOnWriteArrayList<>();
    }

    public void addJobCreator(JobCreator creator) {
        mJobCreators.add(creator);
    }

    public void removeJobCreator(JobCreator creator) {
        mJobCreators.remove(creator);
    }

    public Job createJob(String tag) {
        Job job = null;
        boolean atLeastOneCreatorSeen = false;

        for (JobCreator jobCreator : mJobCreators) {
            atLeastOneCreatorSeen = true;

            job = jobCreator.create(tag);
            if (job != null) {
                break;
            }
        }

        if (!atLeastOneCreatorSeen) {
            CAT.w("no JobCreator added");
        }

        return job;
    }

    public boolean isEmpty() {
        return mJobCreators.isEmpty();
    }
}
