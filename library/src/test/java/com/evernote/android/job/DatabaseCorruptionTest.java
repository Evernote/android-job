package com.evernote.android.job;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.evernote.android.job.test.JobRobolectricTestRunner;
import java.io.File;
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
public class DatabaseCorruptionTest {

    @Test
    public void verifyDeleteAfterCorruptionWhileOpen() {
        Context context = ApplicationProvider.getApplicationContext();

        JobStorage jobStorage = new JobStorage(context);
        SQLiteDatabase database = jobStorage.getDatabase();
        assertThat(database).isNotNull();
        assertThat(database.isOpen()).isTrue();

        File file = new File(database.getPath());
        assertThat(file.exists()).isTrue();
        assertThat(file.isFile()).isTrue();

        new JobStorageDatabaseErrorHandler().onCorruption(database);

        assertThat(file.exists()).isFalse();
    }

    @Test
    public void verifyDeleteAfterCorruptionWhileClosed() {
        Context context = ApplicationProvider.getApplicationContext();

        JobStorage jobStorage = new JobStorage(context);
        SQLiteDatabase database = jobStorage.getDatabase();
        assertThat(database).isNotNull();
        assertThat(database.isOpen()).isTrue();

        File file = new File(database.getPath());
        assertThat(file.exists()).isTrue();
        assertThat(file.isFile()).isTrue();

        database.close();

        new JobStorageDatabaseErrorHandler().onCorruption(database);

        assertThat(file.exists()).isFalse();
    }

    @Test
    public void verifyDeleteWithApi14() {
        Context context = ApplicationProvider.getApplicationContext();

        JobStorage jobStorage = new JobStorage(context);
        SQLiteDatabase database = jobStorage.getDatabase();
        assertThat(database).isNotNull();
        assertThat(database.isOpen()).isTrue();

        File file = new File(database.getPath());
        assertThat(file.exists()).isTrue();
        assertThat(file.isFile()).isTrue();

        new JobStorageDatabaseErrorHandler().deleteApi14(context, file);

        assertThat(file.exists()).isFalse();
    }

    @Test
    public void verifyDeleteWhileOpening() {
        Context context = ApplicationProvider.getApplicationContext();

        String filePath = getClass().getResource("/databases/corrupted.db").getPath();
        final long originalLength = new File(filePath).length();

        assertThat(new File(filePath).exists()).isTrue();

        JobStorage jobStorage = new JobStorage(context, filePath);
        SQLiteDatabase database = jobStorage.getDatabase();

        assertThat(database).isNotNull();
        assertThat(database.isOpen()).isTrue();
        assertThat(originalLength).isNotEqualTo(new File(filePath).length());

        File databaseFile = new File(database.getPath());
        assertThat(databaseFile.exists()).isTrue();
        assertThat(databaseFile.isFile()).isTrue();
    }
}
