/*
 * Copyright 2007-present Evernote Corporation.
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
package com.evernote.android.job.util;

import android.support.annotation.NonNull;

import net.vrallev.android.cat.CatLog;
import net.vrallev.android.cat.instance.CatLazy;
import net.vrallev.android.cat.print.CatPrinter;

import java.util.Arrays;

/**
 * The default {@link CatLog} class for this library.
 *
 * @author rwondratschek
 */
public class JobCat extends CatLazy {

    private static volatile CatPrinter[] printers = new CatPrinter[0]; // use array to avoid synchronization while printing log statements
    private static volatile boolean verbose = true;

    /**
     * Add a global logger for the job library, which will be notified about each log statement.
     *
     * @param printer Your desired logger.
     * @return {@code true} if the printer was added. Returns {@code false} if the printer was
     * already added.
     */
    public static synchronized boolean addLogPrinter(@NonNull CatPrinter printer) {
        for (CatPrinter printer1 : printers) {
            if (printer.equals(printer1)) {
                return false;
            }
        }

        for (int i = 0; i < printers.length; i++) {
            if (printers[i] == null) {
                printers[i] = printer;
                return true;
            }
        }

        int index = printers.length;
        printers = Arrays.copyOf(printers, printers.length + 2);
        printers[index] = printer;
        return true;
    }

    /**
     * Remove a global logger.
     *
     * @param printer Your desired logger.
     * @see #addLogPrinter(CatPrinter)
     */
    public static synchronized void removeLogPrinter(@NonNull CatPrinter printer) {
        for (int i = 0; i < printers.length; i++) {
            if (printer.equals(printers[i])) {
                printers[i] = null;
                // continue, maybe for some reason the printer is twice in the array
            }
        }
    }

    /**
     * Global switch to enable or disable logging.
     *
     * @param verbose Whether or not to print all log messages. The default value is {@code true}.
     */
    public static void setVerbose(boolean verbose) {
        JobCat.verbose = verbose;
    }

    /**
     * @return Whether logging is enabled for this library. The default value is {@code true}.
     */
    public static boolean isVerbose() {
        return verbose;
    }

    private final String mTag;

    public JobCat() {
        this((String) null);
    }

    public JobCat(Class<?> clazz) {
        this(clazz.getSimpleName());
    }

    public JobCat(String tag) {
        mTag = tag;
    }

    @Override
    public String getTag() {
        return mTag == null ? super.getTag() : mTag;
    }

    @Override
    protected void println(int priority, String message, Throwable t) {
        if (!verbose) {
            return;
        }

        super.println(priority, message, t);

        String tag = getTag();

        CatPrinter[] printers = JobCat.printers;
        for (CatPrinter printer : printers) {
            if (printer != null) {
                printer.println(priority, tag, message, t);
            }
        }
    }
}
