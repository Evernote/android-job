/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job.util;

import androidx.annotation.RestrictTo;

/**
 * @author rwondratschek
 */
@RestrictTo(RestrictTo.Scope.LIBRARY)
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
