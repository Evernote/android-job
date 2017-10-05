package com.evernote.android.job.util;

/**
 * @author rwondratschek
 */
public final class BatteryStatus {

    public static final BatteryStatus DEFAULT = new BatteryStatus(false, 1f);

    private final boolean mCharging;
    private final float mBatteryPercent;

    /*package*/ BatteryStatus(boolean charging, float batteryPercent) {
        mCharging = charging;
        mBatteryPercent = batteryPercent;
    }

    /**
     * @return Whether the device is charging.
     */
    public boolean isCharging() {
        return mCharging;
    }

    /**
     * @return The battery percent from 0..1
     */
    public float getBatteryPercent() {
        return mBatteryPercent;
    }

    /**
     * @return Whether the battery is low. The battery is low if has less 15 percent
     * and is not charging.
     */
    public boolean isBatteryLow() {
        return mBatteryPercent < 0.15f && !mCharging;
    }
}
