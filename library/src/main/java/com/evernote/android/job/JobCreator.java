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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A {@code JobCreator} maps a tag to a specific {@link Job} class. You need to pass the tag in the
 * {@link JobRequest.Builder} constructor.
 *
 * <br>
 * <br>
 *
 * The {@link JobManager} can have multiple {@code JobCreator}s with a first come first serve principle.
 * That means that a {@code JobCreator} can block others from creating the right {@link Job}, if they
 * share the same tag.
 *
 * @author rwondratschek
 */
public interface JobCreator {

    /**
     * Map the {@code tag} to a {@code Job}. If you return {@code null}, then other {@code JobCreator}s
     * get the chance to create a {@code Job} for this tag. If no job is created at all, then it's assumed
     * that job failed. This method is called on a background thread right before the job runs.
     *
     * @param tag The tag from the {@link JobRequest} which you passed in the constructor of the
     * {@link JobRequest.Builder} class.
     * @return A new {@link Job} instance for this tag. If you return {@code null}, then the job failed
     * and isn't rescheduled.
     * @see JobRequest.Builder#Builder(String)
     */
    @Nullable
    Job create(@NonNull String tag);

    /**
     * Action to notify receives that the application was instantiated and {@link JobCreator}s should be added.
     */
    String ACTION_ADD_JOB_CREATOR = "com.evernote.android.job.ADD_JOB_CREATOR";

    /**
     * Abstract receiver to get notified about when {@link JobCreator}s need to be added.
     */
    abstract class AddJobCreatorReceiver extends BroadcastReceiver {

        @Override
        public final void onReceive(Context context, Intent intent) {
            if (context ==  null || intent == null || !ACTION_ADD_JOB_CREATOR.equals(intent.getAction())) {
                return;
            }

            try {
                addJobCreator(context, JobManager.create(context));
            } catch (JobManagerCreateException e) {
            }
        }

        /**
         * Called to add a {@link JobCreator} to this manager instance by calling {@link JobManager#addJobCreator(JobCreator)}.
         *
         * @param context Any context.
         * @param manager The manager instance.
         */
        protected abstract void addJobCreator(@NonNull Context context, @NonNull JobManager manager);
    }

}
