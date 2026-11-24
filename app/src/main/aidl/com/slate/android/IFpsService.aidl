package com.slate.android;
interface IFpsService {
    String[] sample() = 0;
    void finish() = 1;
    void destroy() = 16777114;
}
