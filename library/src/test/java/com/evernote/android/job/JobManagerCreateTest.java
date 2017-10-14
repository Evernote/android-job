package com.evernote.android.job;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.test.mock.MockContext;

import org.junit.After;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;
import org.mockito.ArgumentMatchers;

import java.util.Collections;
import java.util.HashSet;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Copyright 2017 Evernote Corporation. All rights reserved.
 *
 * Created by rwondratschek on 12.05.17.
 */
@FixMethodOrder(MethodSorters.JVM)
public class JobManagerCreateTest {

    @After
    public void cleanup() {
        JobConfig.setForceAllowApi14(false);
        try {
            JobManager.instance().destroy();
        } catch (Exception ignored) {
        }
    }

    @Test(expected = JobManagerCreateException.class)
    public void verifyJobManagerCrashesWithoutSupportedApi() {
        JobManager.create(mockContext());
    }

    @Test
    public void verifyCreateSuccessful() {
        PackageManager packageManager = mock(PackageManager.class);
        when(packageManager.queryIntentServices(any(Intent.class), anyInt())).thenReturn(Collections.singletonList(new ResolveInfo()));
        when(packageManager.queryBroadcastReceivers(any(Intent.class), anyInt())).thenReturn(Collections.singletonList(new ResolveInfo()));

        Context context = mockContext();
        when(context.getPackageManager()).thenReturn(packageManager);

        JobManager.create(context);
    }

    @Test
    public void verifyForceAllowApi14() {
        JobConfig.setForceAllowApi14(true);

        Context context = mockContext();

        PackageManager packageManager = mock(PackageManager.class);
        when(context.getPackageManager()).thenReturn(packageManager);

        JobManager.create(context);
    }

    private Context mockContext() {
        SharedPreferences preferences = mock(SharedPreferences.class);
        when(preferences.getStringSet(anyString(), ArgumentMatchers.<String>anySet())).thenReturn(new HashSet<String>());

        Context context = mock(MockContext.class);
        when(context.getApplicationContext()).thenReturn(context);
        when(context.getSharedPreferences(anyString(), anyInt())).thenReturn(preferences);
        return context;
    }
}
