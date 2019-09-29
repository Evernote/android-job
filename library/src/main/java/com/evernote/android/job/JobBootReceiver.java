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
import androidx.annotation.RestrictTo;

/**
 * A {@code BroadcastReceiver} rescheduling jobs after a reboot, if the underlying {@link JobApi} can't
 * handle it.
 *
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public final class JobBootReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        /*
         * Create the job manager. We may need to reschedule jobs and some applications aren't initializing the
         * manager in Application.onCreate(). It may happen that some jobs can't be created if the JobCreator
         * wasn't registered, yet. Apps / Libraries need to figure out how to solve this themselves.
         */
        try {
            JobManager.create(context);
        } catch (JobManagerCreateException ignored) {
        }
    }
}
