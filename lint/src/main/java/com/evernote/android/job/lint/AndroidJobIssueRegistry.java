package com.evernote.android.job.lint;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.Issue;

import java.util.Collections;
import java.util.List;

public final class AndroidJobIssueRegistry extends IssueRegistry {
    @Override
    public List<Issue> getIssues() {
        return Collections.emptyList();
    }
}
