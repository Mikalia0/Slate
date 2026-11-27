package com.slate.android.storage;
interface IDebloatService {
    String change(String packageName, int action) = 0;
    void destroy() = 16777114;
}
