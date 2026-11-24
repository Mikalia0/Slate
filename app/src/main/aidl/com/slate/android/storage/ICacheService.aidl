// SPDX-License-Identifier: GPL-3.0-only
package com.slate.android.storage;
interface ICacheService {
    int trim(long target) = 0;
    String[] listProtected(int user) = 1;
    boolean removeEmpty(int user, String path) = 2;
    void destroy() = 16777114;
}
