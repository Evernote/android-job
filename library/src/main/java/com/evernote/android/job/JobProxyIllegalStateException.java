package com.evernote.android.job;

/**
 * Indicates an illegal state when a proxy tries to schedule a job, but something weird happens.
 *
 * @author rwondratschek
 */
public class JobProxyIllegalStateException extends IllegalStateException {
    public JobProxyIllegalStateException(String s) {
        super(s);
    }

    public JobProxyIllegalStateException(Throwable cause) {
        super(cause);
    }
}
