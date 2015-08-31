package com.evernote.android.job.util;

import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;

import com.evernote.android.job.gcm.PlatformGcmService;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;

import java.util.List;

/**
 * @author rwondratschek
 */
/*package*/ final class GcmAvailableHelper {

    private static final String ACTION_TASK_READY = "com.google.android.gms.gcm.ACTION_TASK_READY";
    private static final String GCM_PERMISSION = "com.google.android.gms.permission.BIND_NETWORK_TASK_SERVICE";

    private static final boolean GCM_IN_CLASSPATH;

    private static int gcmServiceAvailable = -1;

    static {
        boolean gcmInClasspath;
        try {
            Class.forName("com.google.android.gms.gcm.GcmNetworkManager");
            gcmInClasspath = true;
        } catch (Throwable t) {
            gcmInClasspath = false;
        }
        GCM_IN_CLASSPATH = gcmInClasspath;
    }

    public static boolean isGcmApiSupported(Context context) {
        return GCM_IN_CLASSPATH
                && GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
                && isGcmServiceRegistered(context) == ConnectionResult.SUCCESS;
    }

    private static int isGcmServiceRegistered(Context context) {
        if (gcmServiceAvailable < 0) {
            synchronized (JobApi.class) {
                if (gcmServiceAvailable < 0) {
                    Intent intent = new Intent(context, PlatformGcmService.class);
                    List<ResolveInfo> resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
                    if (!hasPermission(resolveInfos)) {
                        gcmServiceAvailable = ConnectionResult.SERVICE_MISSING;
                        return gcmServiceAvailable;
                    }

                    intent = new Intent(ACTION_TASK_READY);
                    intent.setPackage(context.getPackageName());
                    resolveInfos = context.getPackageManager().queryIntentServices(intent, 0);
                    if (!hasPermission(resolveInfos)) {
                        gcmServiceAvailable = ConnectionResult.SERVICE_MISSING;
                        return gcmServiceAvailable;
                    }

                    gcmServiceAvailable = ConnectionResult.SUCCESS;
                }
            }
        }

        return gcmServiceAvailable;
    }

    private static boolean hasPermission(List<ResolveInfo> resolveInfos) {
        if (resolveInfos == null || resolveInfos.isEmpty()) {
            return false;
        }
        for (ResolveInfo info : resolveInfos) {
            if (info.serviceInfo != null && GCM_PERMISSION.equals(info.serviceInfo.permission) && info.serviceInfo.exported) {
                return true;
            }
        }
        return false;
    }

    private GcmAvailableHelper() {
        // no op
    }
}
