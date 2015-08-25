package com.evernote.android.job.demo;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CompoundButton;
import android.widget.Spinner;

import com.evernote.android.job.JobManager;
import com.evernote.android.job.JobRequest;
import com.evernote.android.job.util.JobApi;
import com.evernote.android.job.util.support.PersistableBundleCompat;

/**
 * @author rwondratschek
 */
public class MainActivity extends Activity {

    private static final String LAST_JOB_ID = "LAST_JOB_ID";

    private int mLastJobId;

    private CompoundButton mRequiresCharging;
    private CompoundButton mRequiresDeviceIdle;
    private CompoundButton mSingle;
    private Spinner mNetworkTypeSpinner;

    private JobManager mJobManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mJobManager = JobManager.instance(this);

        if (savedInstanceState != null) {
            mLastJobId = savedInstanceState.getInt(LAST_JOB_ID, 0);
        }

        mRequiresCharging = (CompoundButton) findViewById(R.id.check_requires_charging);
        mRequiresDeviceIdle = (CompoundButton) findViewById(R.id.check_requires_device_idle);
        mSingle = (CompoundButton) findViewById(R.id.check_single);
        mNetworkTypeSpinner = (Spinner) findViewById(R.id.spinner_network_type);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, getNetworkTypesAsString());
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        mNetworkTypeSpinner.setAdapter(adapter);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(LAST_JOB_ID, mLastJobId);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        menu.findItem(R.id.action_force_14).setChecked(false);
        menu.findItem(R.id.action_force_21).setChecked(false);
        menu.findItem(R.id.action_force_gcm).setChecked(false);

        switch (mJobManager.getApi()) {
            case V_21:
                menu.findItem(R.id.action_force_21).setChecked(true);
                break;
            case V_14:
                menu.findItem(R.id.action_force_14).setChecked(true);
                break;
            case GCM:
                menu.findItem(R.id.action_force_gcm).setChecked(true);
                break;
            default:
                throw new IllegalStateException("not implemented");
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_force_21:
                mJobManager.forceApi(JobApi.V_21);
                return true;
            case R.id.action_force_14:
                mJobManager.forceApi(JobApi.V_14);
                return true;
            case R.id.action_force_gcm:
                mJobManager.forceApi(JobApi.GCM);
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.button_simple:
                testSimple();
                break;

            case R.id.button_all_impl:
                testAllImpl();
                break;

            case R.id.button_periodic:
                testPeriodic();
                break;

            case R.id.button_cancel_last:
                testCancelLast();
                break;

            case R.id.button_cancel_all:
                testCancelAll();
                break;

            case R.id.button_exact:
                testExact();
                break;

            case R.id.button_file_activity:
                startActivity(new Intent(this, FileActivity.class));
                break;
        }
    }

    private void testSimple() {
        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putString("key", "Hello world");

        mLastJobId = new JobRequest.Builder(this, TestJob.class)
                .setExecutionWindow(30_000L, 40_000L)
                .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
                .setRequiresCharging(mRequiresCharging.isChecked())
                .setRequiresDeviceIdle(mRequiresDeviceIdle.isChecked())
                .setRequiredNetworkType(JobRequest.NetworkType.values()[mNetworkTypeSpinner.getSelectedItemPosition()])
                .setExtras(extras)
                .setRequirementsEnforced(true)
                .setPersisted(true)
                .setSingle(mSingle.isChecked())
                .build()
                .schedule();
    }

    private void testAllImpl() {
        JobApi currentApi = mJobManager.getApi();

        for (JobApi api : JobApi.values()) {
            mJobManager.forceApi(api);
            testSimple();
        }

        mJobManager.forceApi(currentApi);
    }

    private void testPeriodic() {
        mLastJobId = new JobRequest.Builder(this, TestJob.class)
                .setPeriodic(60_000L)
                .setSingle(mSingle.isChecked())
                .setRequiresCharging(mRequiresCharging.isChecked())
                .setRequiresDeviceIdle(mRequiresDeviceIdle.isChecked())
                .setRequiredNetworkType(JobRequest.NetworkType.values()[mNetworkTypeSpinner.getSelectedItemPosition()])
                .setPersisted(true)
                .build()
                .schedule();
    }

    private void testExact() {
        PersistableBundleCompat extras = new PersistableBundleCompat();
        extras.putString("key", "Hello world");

        mLastJobId = new JobRequest.Builder(this, TestJob.class)
                .setBackoffCriteria(5_000L, JobRequest.BackoffPolicy.EXPONENTIAL)
                .setExtras(extras)
                .setExact(20_000L)
                .setPersisted(true)
                .build()
                .schedule();
    }

    private void testCancelLast() {
        mJobManager.cancel(mLastJobId);
    }

    private void testCancelAll() {
        mJobManager.cancelAll();
    }

    private String[] getNetworkTypesAsString() {
        String[] result = new String[JobRequest.NetworkType.values().length];
        for (int i = 0; i < JobRequest.NetworkType.values().length; i++) {
            result[i] = JobRequest.NetworkType.values()[i].toString();
        }
        return result;
    }
}
