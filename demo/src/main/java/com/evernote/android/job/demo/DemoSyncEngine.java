package com.evernote.android.job.demo;

import android.content.Context;
import android.os.Looper;
import android.os.NetworkOnMainThreadException;
import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * @author rwondratschek
 */
public class DemoSyncEngine {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.US);

    private final Context mContext;

    public DemoSyncEngine(Context context) {
        mContext = context;
    }

    @WorkerThread
    public boolean sync() {
        // do something fancy

        if (Looper.myLooper() == Looper.getMainLooper()) {
            throw new NetworkOnMainThreadException();
        }

        SystemClock.sleep(1_000);
        boolean success = Math.random() > 0.1; // successful 90% of the time
        saveSuccess(success);
        return success;
    }

    @NonNull
    public String getSuccessHistory() {
        try {
            byte[] data = FileUtils.readFile(getSuccessFile());
            if (data == null || data.length == 0) {
                return "";
            }
            return new String(data);
        } catch (IOException e) {
            return "";
        }
    }

    private void saveSuccess(boolean success) {
        String text = DATE_FORMAT.format(new Date()) + "\t\t" + success + '\n';
        try {
            FileUtils.writeFile(getSuccessFile(), text, true);
        } catch (IOException e) {
            Log.e("Demo", e.getMessage(), e);
        }
    }

    private File getSuccessFile() {
        return new File(mContext.getCacheDir(), "success.txt");
    }
}
