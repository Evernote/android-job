/*
 * Copyright 2012 Evernote Corporation.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
 * IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF
 * LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.evernote.android.job;

import android.content.Context;
import android.content.SharedPreferences;
import android.support.v4.util.LruCache;
import android.text.TextUtils;

import net.vrallev.android.cat.Cat;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author rwondratschek
 */
/*package*/ class JobStorage {

    private static final String JOB_PREFIX = "JOB_";
    private static final String JOB_ID_COUNTER = "JOB_ID_COUNTER";

    private final SharedPreferences mPreferences;
    private final LruCache<Integer, JobRequest> mCache;
    private final ExecutorService mExecutorService;

    private final AtomicInteger mJobCounter;

    public JobStorage(Context context) {
        mPreferences = context.getSharedPreferences("jobs", Context.MODE_PRIVATE);
        mExecutorService = Executors.newSingleThreadExecutor();
        mCache = new JobCache();

        int lastJobId = mPreferences.getInt(JOB_ID_COUNTER, 0);
        mJobCounter = new AtomicInteger(lastJobId);
    }

    public synchronized void put(final JobRequest jobRequest) {
        mCache.put(jobRequest.getJobId(), jobRequest);

        mExecutorService.execute(new Runnable() {
            @Override
            public void run() {
                store(jobRequest);
            }
        });
    }

    public synchronized JobRequest get(int id) {
        return mCache.get(id);
    }

    public synchronized JobRequest get(String tag) {
        if (tag == null) {
            return null;
        }
        for (String key : mPreferences.getAll().keySet()) {
            JobRequest request = getRequestForKey(key);
            if (request != null && tag.equals(request.getTag())) {
                return request;
            }
        }
        return null;
    }

    public synchronized Set<JobRequest> getAllJobs() {
        Set<JobRequest> result = new HashSet<>();
        for (String key : mPreferences.getAll().keySet()) {
            JobRequest request = getRequestForKey(key);
            if (request != null) {
                result.add(request);
            }
        }

        return result;
    }

    public synchronized void remove(int jobId) {
        mPreferences.edit()
                .remove(createJobKey(jobId))
                .apply();
        mCache.remove(jobId);
    }

    public synchronized int nextJobId() {
        int id = mJobCounter.incrementAndGet();
        mPreferences.edit()
                .putInt(JOB_ID_COUNTER, id)
                .apply();

        return id;
    }

    private void store(JobRequest request) {
        String xml = request.saveToXml();
        mPreferences.edit()
                .putString(createJobKey(request), xml)
                .apply();
    }

    private JobRequest load(int id) {
        String xml = mPreferences.getString(createJobKey(id), null);
        if (TextUtils.isEmpty(xml)) {
            return null;
        }

        try {
            return JobRequest.fromXml(xml);
        } catch (Exception e) {
            Cat.e(e);
            return null;
        }
    }

    private String createJobKey(JobRequest jobRequest) {
        return createJobKey(jobRequest.getJobId());
    }

    private String createJobKey(int id) {
        return JOB_PREFIX + id;
    }

    private boolean isJobId(String key) {
        return key.startsWith(JOB_PREFIX);
    }

    private int getIdFromKey(String key) {
        try {
            return Integer.parseInt(key.substring(JOB_PREFIX.length()));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private JobRequest getRequestForKey(String key) {
        if (isJobId(key)) {
            int id = getIdFromKey(key);
            if (id > 0) {
                return mCache.get(id);
            }
        }
        return null;
    }

    private class JobCache extends LruCache<Integer, JobRequest> {

        public JobCache() {
            super(20);
        }

        @Override
        protected JobRequest create(Integer id) {
            return load(id);
        }
    }
}
