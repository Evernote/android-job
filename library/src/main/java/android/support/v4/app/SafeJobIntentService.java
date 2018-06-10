package android.support.v4.app;

import android.support.annotation.RestrictTo;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class SafeJobIntentService extends JobIntentService {

    @Override
    GenericWorkItem dequeueWork() {
        try {
            return super.dequeueWork();
        } catch (SecurityException e) {
            e.printStackTrace();
            return null;
        }
    }
}
