package com.slate.android;
interface IFpsService {
    String[] sample(boolean includeCpu, boolean includeFrames) = 0;
    void finish() = 1;
    void destroy() = 16777114;
}
