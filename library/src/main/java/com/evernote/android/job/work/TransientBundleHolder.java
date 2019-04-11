package com.evernote.android.job.work;

import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import android.util.SparseArray;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
/*package*/ final class TransientBundleHolder {

    private TransientBundleHolder() {
        // no-op
    }

    private static SparseArray<Bundle> bundles = new SparseArray<>();

    public static synchronized void putBundle(int jobId, Bundle bundle) {
        bundles.put(jobId, bundle);
    }

    @Nullable
    public static synchronized Bundle getBundle(int jobId) {
        return bundles.get(jobId);
    }

    public static synchronized void cleanUpBundle(int jobId) {
        bundles.remove(jobId);
    }
}
