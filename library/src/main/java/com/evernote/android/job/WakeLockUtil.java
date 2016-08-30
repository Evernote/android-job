package com.evernote.android.job;

import android.content.Context;
import android.os.PowerManager;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import com.evernote.android.job.util.JobCat;
import com.evernote.android.job.util.JobUtil;

import net.vrallev.android.cat.CatLog;

/**
 * @author rwondratschek
 */
/*package*/ final class WakeLockUtil {

    private static final CatLog CAT = new JobCat("WakeLockUtil");

    private WakeLockUtil() {
        // no op
    }

    @Nullable
    public static PowerManager.WakeLock acquireWakeLock(@NonNull Context context, @NonNull String tag, long timeoutMillis) {
        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);

        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, tag);
        wakeLock.setReferenceCounted(false);

        return acquireWakeLock(context, wakeLock, timeoutMillis) ? wakeLock : null;
    }

    public static boolean acquireWakeLock(@NonNull Context context, @Nullable PowerManager.WakeLock wakeLock, long timeoutMillis) {
        if (wakeLock != null && !wakeLock.isHeld() && JobUtil.hasWakeLockPermission(context)) {
            // Even if we have the permission, some devices throw an exception in the try block nonetheless,
            // I'm looking at you, Samsung SM-T805

            try {
                wakeLock.acquire(timeoutMillis);
                return true;
            } catch (Exception e) {
                // saw an NPE on rooted Galaxy Nexus Android 4.1.1
                // android.os.IPowerManager$Stub$Proxy.acquireWakeLock(IPowerManager.java:288)
                CAT.e(e);
            }
        }
        return false;
    }

    public static void releaseWakeLock(@Nullable PowerManager.WakeLock wakeLock) {
        try {
            if (wakeLock != null && wakeLock.isHeld()) {
                wakeLock.release();
            }
        } catch (Exception e) {
            // just to make sure if the PowerManager crashes while acquiring a wake lock
            CAT.e(e);
        }
    }
}
