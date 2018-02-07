package android.support.v4.app;

/**
 * @author rwondratschek
 */
public final class JobIntentServiceReset {
    private JobIntentServiceReset() {
        throw new IllegalArgumentException();
    }

    public static void reset() {
        JobIntentService.sClassWorkEnqueuer.clear();
    }
}
