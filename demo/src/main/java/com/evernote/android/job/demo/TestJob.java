package com.evernote.android.job.demo;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.support.annotation.NonNull;
import android.widget.Toast;

import com.evernote.android.job.Job;
import com.evernote.android.job.Job.Params;
import com.evernote.android.job.Job.Result;

import net.vrallev.android.cat.Cat;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;

/**
 * @author rwondratschek
 */
public class TestJob implements Job.Action {

    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault());

    public static File getTestFile(Context context) {
        return new File(context.getCacheDir(), "TestFile.txt");
    }

    @Override
    @NonNull
    public Result onRunJob(final Params params) {
        SystemClock.sleep(3000);


        if (!params.isCanceled()) {
            writeIntoFile(params);
        }

        final CountDownLatch latch = new CountDownLatch(1);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                StringBuilder message = new StringBuilder()
                        .append(params.isCanceled() ? "Canceled" : "Success")
                        .append(' ')
                        .append(params.getId())
                        .append(' ')
                        .append(params.getExtras().getString("key", "NOT_FOUND"));

                Toast.makeText(params.getContext(), message, Toast.LENGTH_SHORT).show();
                latch.countDown();
            }
        });

        try {
            latch.await();
        } catch (InterruptedException e) {
            Cat.e(e);
        }

        if (params.isCanceled()) {
            return params.isPeriodic() ? Result.FAILURE : Result.RESCHEDULE;
        } else {
            return Result.SUCCESS;
        }
    }

    private void writeIntoFile(Params params) {
        String text = DATE_FORMAT.format(new Date()) + "\t\t" + params.getId() + "\t\t";
        text += (hasInternetAccess() ? "has internet" : "no internet");
        text += '\n';

        try {
            FileUtils.writeFile(getTestFile(params.getContext()), text, true);

        } catch (IOException e) {
            Cat.e(e);
        }
    }

    private boolean hasInternetAccess() {
        InputStream inputStream = null;
        try {
            inputStream = new URL("https://evernote.com/").openConnection().getInputStream();

            byte[] buffer = new byte[128];
            return inputStream.read(buffer) > 0;

        } catch (IOException e) {
            return false;

        } finally {
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException ignored) {
                }
            }
        }
    }

    @Override
    public void onReschedule(int newJobId) {}
}
