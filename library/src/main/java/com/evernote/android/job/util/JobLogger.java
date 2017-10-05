package com.evernote.android.job.util;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
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
