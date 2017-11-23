package com.evernote.android.job.demo;

import android.util.Log;

import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author rwondratschek
 */
@SuppressWarnings("unused")
public final class FileUtils {

    private FileUtils() {
        // no op
    }

    public static byte[] readFile(File file) throws IOException {
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(file);
            byte[] buffer = new byte[(int) file.length()];

            if (buffer.length != fis.read(buffer)) {
                return null;
            } else {
                return buffer;
            }

        } finally {
            close(fis);
        }
    }

    public static void writeFile(File file, String text, boolean append) throws IOException {
        if (file == null || text == null) {
            throw new IllegalArgumentException();
        }

        if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
            throw new IOException("Could not create parent directory");
        }

        if (!file.exists() && !file.createNewFile()) {
            throw new IOException("Could not create file");
        }

        FileWriter writer = null;
        try {
            writer = new FileWriter(file, append);

            writer.write(text);

        } finally {
            close(writer);
        }
    }

    public static void delete(File file) throws IOException {
        if (!file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] files = file.listFiles();
            for (File file1 : files) {
                delete(file1);
            }
        }
        if (!file.delete()) {
            throw new IOException("could not delete file " + file);
        }
    }

    public static void close(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (Exception e) {
                Log.e("Demo", e.getMessage(), e);
            }
        }
    }
}
