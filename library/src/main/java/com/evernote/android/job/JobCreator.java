package com.evernote.android.job;

import net.vrallev.android.cat.Cat;

/**
 * A {@code JobCreator} maps a key to a specific {@link Job} class. You need to pass the key in the
 * {@link JobRequest.Builder} constructor. For convenience usage you can use the Job class itself as
 * key, e.g. the {@link ClassNameJobCreator} expects a fully-qualified class name as key.
 *
 * @author rwondratschek
 */
public interface JobCreator {

    /**
     * Map the {@code key} to a {@code Job}. If you return {@code null}, then no job can be executed
     * and it's assumed that job failed. This method is called on the UI thread right before job runs.
     *
     * @param key The key from the {@link JobRequest} which you passed in constructor of the
     * {@code Builder} class.
     * @return A new {@link Job} instance for this key. If you return {@code null}, then the job failed
     * and isn't rescheduled.
     * @see JobRequest.Builder#Builder(String)
     */
    Job create(String key);

    /**
     * A simple implementation which expects fully-qualified class names as key. If you use this
     * {@code JobCreator} it's recommended to use {@link JobRequest.Builder#Builder(Class)} to
     * create job requests.
     *
     * <br>
     * <br>
     *
     * If the job can't be instantiated, then this creator returns {@code null} and the job failed and
     * is not rescheduled.
     */
    class ClassNameJobCreator implements JobCreator {
        @Override
        public Job create(String key) {
            try {
                return (Job) Class.forName(key).newInstance();
            } catch (Throwable t) {
                Cat.e(t);
                return null;
            }
        }
    }
}
