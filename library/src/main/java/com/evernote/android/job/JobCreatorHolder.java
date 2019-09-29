/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job;

import androidx.annotation.RestrictTo;

import com.evernote.android.job.util.JobCat;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
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
