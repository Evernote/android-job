package com.evernote.android.job.demo;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

import net.vrallev.android.cat.Cat;

import java.io.File;
import java.io.IOException;

/**
 * @author rwondratschek
 */
public class FileActivity extends Activity {

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_file);

        mTextView = (TextView) findViewById(R.id.textView_log);
        refreshView();
    }

    private void refreshView() {
        File file = TestJob.getTestFile(this);
        if (!file.exists()) {
            return;
        }

        try {
            byte[] content = FileUtils.readFile(file);
            if (content != null) {
                mTextView.setText(new String(content, "UTF-8"));
            }

        } catch (IOException e) {
            Cat.e(e);
        }
    }
}
