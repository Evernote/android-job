package com.evernote.android.job.demo;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

/**
 * @author rwondratschek
 */
public class SyncHistoryActivity extends Activity {

    private TextView mTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sync_history);

        mTextView = (TextView) findViewById(R.id.textView_log);
        refreshView();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        getMenuInflater().inflate(R.menu.activity_sync_history, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_sync_now:
                syncAsynchronously();
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @SuppressLint("StaticFieldLeak")
    private void syncAsynchronously() {
        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                return new DemoSyncEngine(SyncHistoryActivity.this).sync();
            }

            @Override
            protected void onPostExecute(Boolean aBoolean) {
                refreshView();
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void refreshView() {
        mTextView.setText(new DemoSyncEngine(this).getSuccessHistory());
    }
}
