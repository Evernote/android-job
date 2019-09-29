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
package com.evernote.android.job.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.util.Log;

/**
 * Logger interface for the library.
 *
 * @author rwondratschek
 */
public interface JobLogger {
    /**
     * Log a message from the library.
     *
     * @param priority The priority of the log message. See {@link Log} for all values.
     * @param tag The tag of the log message.
     * @param message The message itself.
     * @param t The thrown exception in case of a failure.
     */
    void log(int priority, @NonNull String tag, @NonNull String message, @Nullable Throwable t);
}
