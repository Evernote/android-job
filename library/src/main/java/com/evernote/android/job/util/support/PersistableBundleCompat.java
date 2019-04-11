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
package com.evernote.android.job.util.support;

import android.os.PersistableBundle;
import androidx.annotation.NonNull;

import com.evernote.android.job.util.JobCat;

import org.xmlpull.v1.XmlPullParserException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Compat class which provides similar features like {@link PersistableBundle}. Besides a boolean array
 * all methods are available.
 *
 * @author rwondratschek
 */
@SuppressWarnings("unused")
public final class PersistableBundleCompat {

    private static final JobCat CAT = new JobCat("PersistableBundleCompat");
    private static final String UTF_8 = "UTF-8";

    private final Map<String, Object> mValues;

    public PersistableBundleCompat() {
        this(new HashMap<String, Object>());
    }

    public PersistableBundleCompat(PersistableBundleCompat bundle) {
        this(new HashMap<>(bundle.mValues));
    }

    private PersistableBundleCompat(Map<String, Object> values) {
        mValues = values;
    }

    public void putBoolean(String key, boolean value) {
        mValues.put(key, value);
    }

    public boolean getBoolean(String key, boolean defaultValue) {
        Object value = mValues.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        } else {
            return defaultValue;
        }
    }

    public void putInt(String key, int value) {
        mValues.put(key, value);
    }

    public int getInt(String key, int defaultValue) {
        Object value = mValues.get(key);
        if (value instanceof Integer) {
            return (Integer) value;
        } else {
            return defaultValue;
        }
    }

    public void putIntArray(String key, int[] value) {
        mValues.put(key, value);
    }

    public int[] getIntArray(String key) {
        Object value = mValues.get(key);
        if (value instanceof int[]) {
            return (int[]) value;
        } else {
            return null;
        }
    }

    public void putLong(String key, long value) {
        mValues.put(key, value);
    }

    public long getLong(String key, long defaultValue) {
        Object value = mValues.get(key);
        if (value instanceof Long) {
            return (Long) value;
        } else {
            return defaultValue;
        }
    }

    public void putLongArray(String key, long[] value) {
        mValues.put(key, value);
    }

    public long[] getLongArray(String key) {
        Object value = mValues.get(key);
        if (value instanceof long[]) {
            return (long[]) value;
        } else {
            return null;
        }
    }

    public void putDouble(String key, double value) {
        mValues.put(key, value);
    }

    public double getDouble(String key, double defaultValue) {
        Object value = mValues.get(key);
        if (value instanceof Double) {
            return (Double) value;
        } else {
            return defaultValue;
        }
    }

    public void putDoubleArray(String key, double[] value) {
        mValues.put(key, value);
    }

    public double[] getDoubleArray(String key) {
        Object value = mValues.get(key);
        if (value instanceof double[]) {
            return (double[]) value;
        } else {
            return null;
        }
    }

    public void putString(String key, String value) {
        mValues.put(key, value);
    }

    public String getString(String key, String defaultValue) {
        Object value = mValues.get(key);
        if (value instanceof String) {
            return (String) value;
        } else {
            return defaultValue;
        }
    }

    public void putStringArray(String key, String[] value) {
        mValues.put(key, value);
    }

    public String[] getStringArray(String key) {
        Object value = mValues.get(key);
        if (value instanceof String[]) {
            return (String[]) value;
        } else {
            return null;
        }
    }

    public void putPersistableBundleCompat(String key, PersistableBundleCompat value) {
        mValues.put(key, value == null ? null : value.mValues);
    }

    @SuppressWarnings("unchecked")
    public PersistableBundleCompat getPersistableBundleCompat(String key) {
        Object value = mValues.get(key);
        if (value instanceof Map) {
            return new PersistableBundleCompat((Map<String, Object>) value);
        } else {
            return null;
        }
    }

    public void clear() {
        mValues.clear();
    }

    public boolean containsKey(String key) {
        return mValues.containsKey(key);
    }

    public Object get(String key) {
        return mValues.get(key);
    }

    public boolean isEmpty() {
        return mValues.isEmpty();
    }

    public Set<String> keySet() {
        return mValues.keySet();
    }

    public void putAll(PersistableBundleCompat bundle) {
        mValues.putAll(bundle.mValues);
    }

    public void remove(String key) {
        mValues.remove(key);
    }

    public int size() {
        return mValues.size();
    }

    @NonNull
    public String saveToXml() {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            XmlUtils.writeMapXml(mValues, outputStream);
            return outputStream.toString(UTF_8);

        } catch (XmlPullParserException | IOException e) {
            CAT.e(e);
            // shouldn't happen
            return "";

        } catch (Error e) {
            // https://gist.github.com/vRallev/9444359f05259e4b6317 and other crashes on rooted devices
            CAT.e(e);
            return "";

        } finally {
            try {
                outputStream.close();
            } catch (IOException ignored) {
            }
        }
    }

    @SuppressWarnings("unchecked")
    @NonNull
    public static PersistableBundleCompat fromXml(@NonNull String xml) {
        ByteArrayInputStream inputStream = null;
        try {
            inputStream = new ByteArrayInputStream(xml.getBytes(UTF_8));
            HashMap<String, ?> map = XmlUtils.readMapXml(inputStream);
            return new PersistableBundleCompat((Map<String, Object>) map);

        } catch (XmlPullParserException | IOException e) {
            CAT.e(e);
            return new PersistableBundleCompat();

        } catch (VerifyError e) {
            // https://gist.github.com/vRallev/9444359f05259e4b6317
            CAT.e(e);
            return new PersistableBundleCompat();

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }
}
