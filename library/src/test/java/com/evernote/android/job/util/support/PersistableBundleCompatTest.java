package com.evernote.android.job.util.support;

import android.content.Intent;
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
    private static final String EXTRA_BOOL_1 = "EXTRA_BOOL_1";
    private static final String EXTRA_INT_1 = "EXTRA_INT_1";
    private static final String EXTRA_LONG_1 = "EXTRA_LONG_1";
    private static final String EXTRA_DOUBLE_1 = "EXTRA_DOUBLE_1";
    private static final String EXTRA_STRING_1 = "EXTRA_STRING_1";
    private static final String EXTRA_STRING_2 = "EXTRA_STRING_2";
    private static final String EXTRA_INT_ARR = "EXTRA_INT_ARR";
    private static final String EXTRA_LONG_ARR = "EXTRA_LONG_ARR";
    private static final String EXTRA_DOUBLE_ARR = "EXTRA_DOUBLE_ARR";
    private static final String EXTRA_STRING_ARR = "EXTRA_STRING_ARR";
    private static final String EXTRA_BUNDLE_1 = "EXTRA_BUNDLE_1";

    @Test
    public void testBundle() {
        PersistableBundleCompat bundle = new PersistableBundleCompat();
        bundle.putBoolean(EXTRA_BOOL_1, true);
        bundle.putInt(EXTRA_INT_1, 1);
        bundle.putLong(EXTRA_LONG_1, 1L);
        bundle.putDouble(EXTRA_DOUBLE_1, 1.0);
        bundle.putString(EXTRA_STRING_1, "hello");
        bundle.putIntArray(EXTRA_INT_ARR, new int[]{1, 2, 3});
        bundle.putLongArray(EXTRA_LONG_ARR, new long[]{4L, 5L, 6L});
        bundle.putDoubleArray(EXTRA_DOUBLE_ARR, new double[]{7.0, 8.0, 9.0});
        bundle.putStringArray(EXTRA_STRING_ARR, new String[]{"Hello", "world"});

        PersistableBundleCompat other = new PersistableBundleCompat();
        other.putString(EXTRA_STRING_2, "world");
        bundle.putPersistableBundleCompat(EXTRA_BUNDLE_1, other);

        // test XML saves and inflates correctly
        String xml = bundle.saveToXml();
        PersistableBundleCompat inflated = PersistableBundleCompat.fromXml(xml);

        assertThat(xml).isNotEmpty();
        assertThat(inflated).isNotNull();

        assertThat(inflated.getBoolean(EXTRA_BOOL_1, false)).isTrue();
        assertThat(inflated.getInt(EXTRA_INT_1, 0)).isEqualTo(1);
        assertThat(inflated.getLong(EXTRA_LONG_1, 0L)).isEqualTo(1L);
        assertThat(inflated.getDouble(EXTRA_DOUBLE_1, 0.0)).isEqualTo(1.0);
        assertThat(inflated.getString(EXTRA_STRING_1, null)).isEqualTo("hello");
        assertThat(inflated.getIntArray(EXTRA_INT_ARR)).isNotEmpty().containsExactly(1, 2, 3);
        assertThat(inflated.getLongArray(EXTRA_LONG_ARR)).isNotEmpty().containsExactly(4L, 5L, 6L);
        assertThat(inflated.getDoubleArray(EXTRA_DOUBLE_ARR)).isNotEmpty().containsExactly(7.0, 8.0, 9.0);
        assertThat(inflated.getStringArray(EXTRA_STRING_ARR)).isNotEmpty().containsExactly("Hello", "world");

        PersistableBundleCompat inflatedInner = inflated.getPersistableBundleCompat(EXTRA_BUNDLE_1);
        assertThat(inflatedInner).isNotNull();
        assertThat(inflatedInner.getString(EXTRA_STRING_2, null)).isEqualTo("world");

        // test that the Intent contains all the values from PersistableBundleCompat
        Intent intent = bundle.toIntent();
        assertThat(intent).isNotNull();

        assertThat(intent.getBooleanExtra(EXTRA_BOOL_1, false)).isTrue();
        assertThat(intent.getIntExtra(EXTRA_INT_1, 0)).isEqualTo(1);
        assertThat(intent.getLongExtra(EXTRA_LONG_1, 0L)).isEqualTo(1L);
        assertThat(intent.getDoubleExtra(EXTRA_DOUBLE_1, 0.0)).isEqualTo(1.0);
        assertThat(intent.getStringExtra(EXTRA_STRING_1)).isEqualTo("hello");
        assertThat(intent.getIntArrayExtra(EXTRA_INT_ARR)).isNotEmpty().containsExactly(1, 2, 3);
        assertThat(intent.getLongArrayExtra(EXTRA_LONG_ARR)).isNotEmpty().containsExactly(4L, 5L, 6L);
        assertThat(intent.getDoubleArrayExtra(EXTRA_DOUBLE_ARR)).isNotEmpty().containsExactly(7.0, 8.0, 9.0);
        assertThat(intent.getStringArrayExtra(EXTRA_STRING_ARR)).isNotEmpty().containsExactly("Hello", "world");

        Intent innerIntent = intent.getParcelableExtra(EXTRA_BUNDLE_1);
        assertThat(innerIntent).isNotNull();
        assertThat(innerIntent.getStringExtra(EXTRA_STRING_2)).isEqualTo("world");
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
