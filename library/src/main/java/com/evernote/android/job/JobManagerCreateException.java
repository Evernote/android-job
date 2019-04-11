/*
 * Copyright (C) 2018 Evernote Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.evernote.android.job;

import androidx.annotation.RestrictTo;

/**
 * Indicates illegal states during the creation of the {@link JobManager}.
 *
 * <br>
 * <br>
 *
 * You can suppress this exception with {@link JobConfig#setForceAllowApi14(boolean)}.
 *
 * @author rwondratschek
 */
public class JobManagerCreateException extends IllegalStateException {
    @RestrictTo(RestrictTo.Scope.LIBRARY)
    public JobManagerCreateException(String s) {
        super(s);
    }
}
