package com.evernote.android.job.lint;

import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

import static org.assertj.core.api.Java6Assertions.assertThat;

@FixMethodOrder(MethodSorters.JVM)
public class AndroidJobIssueRegistryTest {

    /**
     * Test that the issue registry contains the correct number of issues.
     */
    @Test
    public void verifyIssueCount() {
        assertThat(new AndroidJobIssueRegistry().getIssues()).isEmpty();
    }
}
