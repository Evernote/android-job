package com.evernote.android.job;

import android.app.PendingIntent;
import android.os.Build;

public final class PendingIntentUtil {
  private PendingIntentUtil() {
    // No-op
  }

  public static int flagImmutable() {
    if (Build.VERSION.SDK_INT >= 23) {
      return PendingIntent.FLAG_IMMUTABLE;
    } else {
      return 0;
    }
  }
}
