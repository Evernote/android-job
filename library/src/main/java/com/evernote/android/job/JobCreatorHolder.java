package com.evernote.android.job;


import net.vrallev.android.cat.Cat;

import java.util.ArrayList;
import java.util.List;

/**
 * @author rwondratschek
 */
/*package*/ class JobCreatorHolder {

    private final List<JobCreator> mJobCreators;
    private final Object mMonitor;

    public JobCreatorHolder() {
        mJobCreators = new ArrayList<>();
        mMonitor = new Object();
    }

    public void addJobCreator(JobCreator creator) {
        synchronized (mMonitor) {
            mJobCreators.add(creator);
        }
    }

    public void removeJobCreator(JobCreator creator) {
        synchronized (mMonitor) {
            mJobCreators.remove(creator);
        }
    }

    public Job createJob(String tag) {
        ArrayList<JobCreator> jobCreators = null;
        JobCreator singleJobCreator = null;

        synchronized (mMonitor) {
            int count = mJobCreators.size();
            if (count == 0) {
                Cat.w("no JobCreator added");
                return null;

            } else if (count == 1) {
                // avoid creating an extra list when it's not necessary
                singleJobCreator = mJobCreators.get(0);
            } else {
                jobCreators = new ArrayList<>(mJobCreators);
            }
        }

        if (singleJobCreator != null) {
            return singleJobCreator.create(tag);
        }

        if (jobCreators != null) {
            for (JobCreator jobCreator : jobCreators) {
                Job job = jobCreator.create(tag);
                if (job != null) {
                    return job;
                }
            }
        }

        return null;
    }

    public boolean isEmpty() {
        synchronized (mMonitor) {
            return mJobCreators.isEmpty();
        }
    }
}
