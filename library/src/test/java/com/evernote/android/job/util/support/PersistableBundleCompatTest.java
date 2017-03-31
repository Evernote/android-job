package com.evernote.android.job.util.support;

import com.evernote.android.job.test.JobRobolectricTestRunner;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Java6Assertions.assertThat;

/**
 * @author rwondratschek
 */
@RunWith(JobRobolectricTestRunner.class)
@FixMethodOrder(MethodSorters.JVM)
public class PersistableBundleCompatTest {

    @Test
    public void testBundle() {
        PersistableBundleCompat bundle = new PersistableBundleCompat();
        bundle.putBoolean("bool1", true);
        bundle.putInt("int1", 1);
        bundle.putLong("long1", 1L);
        bundle.putDouble("double1", 1.0);
        bundle.putString("string1", "hello");
        bundle.putIntArray("intArr", new int[]{1, 2, 3});
        bundle.putLongArray("longArr", new long[]{4L, 5L, 6L});
        bundle.putDoubleArray("doubleArr", new double[]{7.0, 8.0, 9.0});
        bundle.putStringArray("stringArr", new String[]{"Hello", "world"});

        PersistableBundleCompat other = new PersistableBundleCompat();
        other.putString("string2", "world");
        bundle.putPersistableBundleCompat("bundle1", other);

        String xml = bundle.saveToXml();
        PersistableBundleCompat inflated = PersistableBundleCompat.fromXml(xml);

        assertThat(xml).isNotEmpty();
        assertThat(inflated).isNotNull();

        assertThat(inflated.getBoolean("bool1", false)).isTrue();
        assertThat(inflated.getInt("int1", 0)).isEqualTo(1);
        assertThat(inflated.getLong("long1", 0L)).isEqualTo(1L);
        assertThat(inflated.getDouble("double1", 0.0)).isEqualTo(1.0);
        assertThat(inflated.getString("string1", null)).isEqualTo("hello");
        assertThat(inflated.getIntArray("intArr")).isNotEmpty().containsExactly(1, 2, 3);
        assertThat(inflated.getLongArray("longArr")).isNotEmpty().containsExactly(4L, 5L, 6L);
        assertThat(inflated.getDoubleArray("doubleArr")).isNotEmpty().containsExactly(7.0, 8.0, 9.0);
        assertThat(inflated.getStringArray("stringArr")).isNotEmpty().containsExactly("Hello", "world");


        PersistableBundleCompat inflatedInner = inflated.getPersistableBundleCompat("bundle1");
        assertThat(inflatedInner).isNotNull();
        assertThat(inflatedInner.getString("string2", null)).isEqualTo("world");
    }

    @Test
    public void testNullInStringArray() {
        PersistableBundleCompat bundle = new PersistableBundleCompat();

        String[] array = {"111", null, "333"};
        bundle.putStringArray("array", array);

        bundle = PersistableBundleCompat.fromXml(bundle.saveToXml());

        String[] inflated = bundle.getStringArray("array");
        assertThat(inflated).isNotNull().hasSize(3).containsExactly("111", null, "333");
    }
}
