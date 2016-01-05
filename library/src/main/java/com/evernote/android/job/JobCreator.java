package com.evernote.android.job;

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
    Job create(String tag);
}
